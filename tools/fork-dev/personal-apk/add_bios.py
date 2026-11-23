"""Copy an APK's entries (dropping its signing block) and add BIOS files
under assets/bios/, lowercase names. Output is unaligned and unsigned.

Usage: python3 add_bios.py <in.apk> <out.apk> <bios file>...
"""
import os
import sys
import zipfile


def main(src, dst, files):
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as zout:
        names = set()
        for info in zin.infolist():
            if info.filename.startswith("assets/bios/"):
                continue
            data = zin.read(info)
            out = zipfile.ZipInfo(info.filename, info.date_time)
            out.compress_type = info.compress_type
            out.external_attr = info.external_attr
            out.create_system = info.create_system
            zout.writestr(out, data)
            names.add(info.filename)
        for path in files:
            name = "assets/bios/" + os.path.basename(path).lower()
            assert name not in names, name
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            zout.writestr(info, open(path, "rb").read(), compresslevel=9)
            names.add(name)
            print("added", name)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
