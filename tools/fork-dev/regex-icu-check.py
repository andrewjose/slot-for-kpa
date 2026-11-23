#!/usr/bin/env python3
"""Compile every Kotlin Regex literal with ICU, the regex engine Android uses.

Desktop Java (the JVM unit tests) accepts patterns that Android rejects at
runtime, such as an unescaped '}' or ']'. A bad pattern in an object's
initializer throws ExceptionInInitializerError on the device. Run this before
a build: python3 regex-icu-check.py android/app/src/main/java
Exits 1 when a pattern fails. Needs libicu (libicui18n.so.*), present on Ubuntu.
"""
import ctypes, ctypes.util, glob, os, re, sys

def icu():
    names = sorted(glob.glob("/usr/lib/*/libicui18n.so.*") + glob.glob("/usr/lib/libicui18n.so.*"))
    for path in names:
        m = re.search(r"libicui18n\.so\.(\d+)$", path)
        if not m:
            continue
        lib = ctypes.CDLL(path)
        fn = getattr(lib, "uregex_open_" + m.group(1))
        fn.restype = ctypes.c_void_p
        fn.argtypes = [ctypes.c_char_p, ctypes.c_int32, ctypes.c_uint32, ctypes.c_void_p,
                       ctypes.POINTER(ctypes.c_int)]
        close = getattr(lib, "uregex_close_" + m.group(1))
        close.argtypes = [ctypes.c_void_p]
        return fn, close
    sys.exit("libicui18n not found (apt install libicu-dev or libicu74)")

def literals(text):
    # Regex("""...""") and Regex("..."), with any RegexOption after; skip
    # templates ($) and concatenations, which can't be checked statically.
    for m in re.finditer(r'Regex\(\s*"""(.*?)"""\s*[,)]', text, re.S):
        yield m.group(1), text.count("\n", 0, m.start()) + 1
    for m in re.finditer(r'Regex\(\s*"((?:\\.|[^"\\\n])*)"\s*[,)]', text):
        raw = m.group(1)
        unescaped = re.sub(r'\\(.)', lambda e: {"n": "\n", "t": "\t", "r": "\r"}.get(e.group(1), e.group(1)), raw)
        yield unescaped, text.count("\n", 0, m.start()) + 1

def main(root):
    open_, close = icu()
    bad = 0
    for path in sorted(glob.glob(os.path.join(root, "**", "*.kt"), recursive=True)):
        text = open(path, encoding="utf-8").read()
        for pattern, line in literals(text):
            if "${" in pattern or re.search(r"\$[A-Za-z_]", pattern):
                continue
            data = pattern.encode("utf-16-le")
            status = ctypes.c_int(0)
            perr = (ctypes.c_byte * 128)()
            handle = open_(data, len(data) // 2, 0, perr, ctypes.byref(status))
            if status.value > 0:
                bad += 1
                print(f"{path}:{line}: ICU rejects (status {status.value}): {pattern}")
            elif handle:
                close(handle)
    print("ICU regex check:", "FAILED" if bad else "all patterns compile")
    return 1 if bad else 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "android/app/src/main/java"))
