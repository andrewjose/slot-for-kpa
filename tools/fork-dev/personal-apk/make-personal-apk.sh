#!/usr/bin/env bash
# Makes Andrew's PERSONAL slot. APK: a CI test build with his own BIOS files
# packed in (assets/bios/), re-signed with the fork's test key so it installs
# over the other test builds. NEVER upload or publish the result.
#
# Why this exists: the fork is public, so the BIOS can't go through GitHub
# Actions. The CI build (no BIOS) is downloaded, the BIOS added locally,
# then the APK is zip-aligned and signed (APK Signature Scheme v2) here.
#
# Needs: python3, pip packages `cryptography`, `repro-apk` (zipalign),
# `apksigtool` (verify only), a JDK (keytool), and `gh` for the download.
#
# Usage:
#   make-personal-apk.sh <release tag> <bios folder> <out.apk>
#   e.g. make-personal-apk.sh test-v2.2.2-mac-lab-357-run12 "/path/F/Curated ROMS/bios" personal.apk
# The bios folder should hold only the files to bundle (the app keeps those
# it recognises: scph*.bin, syscard1-3.pce, gb/gbc/gba_bios.bin).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
tag="$1"; bios="$2"; out="$3"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT

# 1. The CI build of that run.
id=$(gh api "repos/andrewjose/slot-for-kpa/releases/tags/$tag" -q '.assets[0].id')
gh api -H "Accept: application/octet-stream" "repos/andrewjose/slot-for-kpa/releases/assets/$id" > "$work/ci.apk"
apksigtool verify "$work/ci.apk" | tail -1

# 2. The fork's committed test key (not a release key), as PEM + DER.
git -C "${SLOT_REPO:-$here/../../repo}" show HEAD:.github/test-signing/debug.keystore > "$work/debug.keystore" 2>/dev/null \
  || curl -sfL -o "$work/debug.keystore" https://raw.githubusercontent.com/andrewjose/slot-for-kpa/build/test-apk-v2.2.2/.github/test-signing/debug.keystore
keytool -importkeystore -srckeystore "$work/debug.keystore" -srcstorepass android -srcalias androiddebugkey \
  -srckeypass android -destkeystore "$work/k.p12" -deststoretype PKCS12 -deststorepass android \
  -destkeypass android -noprompt >/dev/null 2>&1
python3 - "$work" <<'EOF'
import sys
from cryptography.hazmat.primitives.serialization import pkcs12, Encoding, PrivateFormat, NoEncryption
w = sys.argv[1]
k, c, _ = pkcs12.load_key_and_certificates(open(f"{w}/k.p12", "rb").read(), b"android")
open(f"{w}/key.pem", "wb").write(k.private_bytes(Encoding.PEM, PrivateFormat.PKCS8, NoEncryption()))
open(f"{w}/cert.der", "wb").write(c.public_bytes(Encoding.DER))
EOF

# 3. BIOS in, align (4 bytes, .so pages 16 KiB), sign v2, verify.
python3 "$here/add_bios.py" "$work/ci.apk" "$work/unaligned.apk" "$bios"/*
repro-apk zipalign -P 16 4 "$work/unaligned.apk" "$work/aligned.apk"
python3 "$here/v2sign.py" "$work/key.pem" "$work/cert.der" "$work/aligned.apk" "$out"
apksigtool verify "$out" | tail -1
apksigtool parse "$out" | grep "X.509 SHA256"   # must match the CI build's certificate
unzip -Z -1 "$out" | grep assets/bios/
