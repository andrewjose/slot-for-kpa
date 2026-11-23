"""Sign an (already zip-aligned) APK with APK Signature Scheme v2.

Usage: python3 v2sign.py <key.pem> <cert.der> <in.apk> <out.apk>

RSA key, RSASSA-PKCS1-v1_5 with SHA2-256 (algorithm 0x0103). Follows
https://source.android.com/docs/security/features/apksigning/v2 : the signing
block goes between the ZIP entries and the central directory, and the EOCD's
central-directory offset is moved past it. No v1 (JAR) signature: minSdk 31.
"""
import hashlib
import struct
import sys

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

ALGO = 0x0103
V2_ID = 0x7109871A
MAGIC = b"APK Sig Block 42"
CHUNK = 1 << 20


def lp(b):
    return struct.pack("<I", len(b)) + b


def find_eocd(data):
    i = data.rfind(b"PK\x05\x06", max(0, len(data) - 65557))
    if i < 0:
        sys.exit("no EOCD")
    return i


def digest(sections):
    chunks = []
    for s in sections:
        for o in range(0, len(s), CHUNK):
            c = s[o:o + CHUNK]
            chunks.append(hashlib.sha256(b"\xa5" + struct.pack("<I", len(c)) + c).digest())
    return hashlib.sha256(b"\x5a" + struct.pack("<I", len(chunks)) + b"".join(chunks)).digest()


def main(key_pem, cert_der, src, dst):
    data = open(src, "rb").read()
    e = find_eocd(data)
    cd_size, cd_off = struct.unpack("<II", data[e + 12:e + 20])
    assert cd_off + cd_size == e, "data between central directory and EOCD"
    if data[cd_off - 16:cd_off] == MAGIC:
        sys.exit("input already has an APK Signing Block; strip it first")
    entries, cd, eocd = data[:cd_off], data[cd_off:e], data[e:]

    dg = digest([entries, cd, eocd])  # EOCD's CD offset already = block start
    key = serialization.load_pem_private_key(open(key_pem, "rb").read(), None)
    cert = open(cert_der, "rb").read()
    spki = key.public_key().public_bytes(serialization.Encoding.DER,
                                         serialization.PublicFormat.SubjectPublicKeyInfo)
    signed = (lp(lp(struct.pack("<I", ALGO) + lp(dg)))
              + lp(lp(cert))
              + lp(b""))
    sig = key.sign(signed, padding.PKCS1v15(), hashes.SHA256())
    signer = lp(signed) + lp(lp(struct.pack("<I", ALGO) + lp(sig))) + lp(spki)
    value = lp(lp(signer))

    pair = struct.pack("<Q", 4 + len(value)) + struct.pack("<I", V2_ID) + value
    size = len(pair) + 8 + 16  # everything after the first size field
    block = struct.pack("<Q", size) + pair + struct.pack("<Q", size) + MAGIC

    new_eocd = bytearray(eocd)
    struct.pack_into("<I", new_eocd, 16, cd_off + len(block))
    with open(dst, "wb") as f:
        f.write(entries + block + cd + bytes(new_eocd))


if __name__ == "__main__":
    main(*sys.argv[1:5])
