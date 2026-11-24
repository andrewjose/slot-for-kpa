#!/usr/bin/env python3
"""
make_test_set.py - build a slot. test ROM set: a few games per system, each
picked to exercise one of slot.'s cores or features, laid out like ES-DE.

    E:\\Projects\\slot-for-kpa\\test ROM set\\
        gb\\ gbc\\ gba\\ nes\\ snes\\ genesis\\ psx\\ tg-cd\\
        README.md        what each game tests, and the checklist

Sources stay where they are. Files on E: are hard-linked (no extra space);
files on F: are copied. Zips that slot. reads as they are are linked/copied
as zips; 7z archives (which slot. can't open) and zips of discs are
extracted. Nothing outside the test set folder is ever written or deleted.

Run from WSL or the Cowork shell (dry run by default):
    python3 make_test_set.py
    python3 make_test_set.py --apply [--only gba,psx]
Options: --e /mnt/e  --f /mnt/f  --dest <dir>
7z needs py7zr importable (PYTHONPATH); zips use the standard library.
"""
import argparse
import fnmatch
import os
import shutil
import sys
import zipfile

# (system, source, how, what it tests)
#   source: "E:..." or "F:..." path (glob allowed in the last part)
#   how: "file"   link/copy the file as it is ("file>sub": into a subfolder)
#        "dir"    link/copy a whole folder (BIN/CUE sets, m3u + discs)
#        "7z:<member glob>" extract matching members of a .7z, flattened
#        "zip:<member glob>" extract matching members of a .zip, flattened
#        "zipzip:<member>" take one zip out of a zip of zips, as a zip
GBA7Z = "E:Downloads/Full Sets/gba/Game Boy Advance Full Set.7z"
NES = "E:Downloads/Full Sets/nes/Nintendo - Nintendo Entertainment System (Headered)/"
SNESZIP = "E:Downloads/Full Sets/snes/Nintendo - Super Nintendo Entertainment System (No-Intro) (17-03-2024).zip"
SET = [
    # ---- Game Boy -------------------------------------------------------
    ("gb", "F:Curated ROMS/gb/Legend of Zelda, The - Link's Awakening.zip", "file",
     "Zipped cart; DMG palettes (Pause > Screen > Palette); Gambatte vs mGBA (SELECT)"),
    ("gb", "F:Curated ROMS/gb/Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb", "file",
     "Official label; battery save lands in gb/<core>/ beside the ROM"),
    ("gb", "F:Curated ROMS/gb/Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced).gb", "file",
     "A .gb file with colour (CGB flag): shelf and palette rows"),
    ("gb", "F:Curated ROMS/gb/Super Mario Land 2 - 6 Golden Coins (USA, Europe).zip", "file", "Plain GB game"),
    ("gb", "F:Curated ROMS/ROM Hacks/gb/Shin Pokemon Blue (v3.2.3).gb", "file",
     "Hack: borrows Pokemon Blue's label with the file name band"),
    ("gb", "F:Curated ROMS/ROM Hacks/gb/Pokemon - PureGreen.gb", "file",
     "Hack: second borrowed Pokemon label (two hacks stay told apart)"),
    ("gb", "F:Curated ROMS/Translations/gb/Gargoyle's Quest II - The Demon Darkness (Japan) [T-En by Bailli v1.0] [n].7z", "7z:*.gb",
     "Translation of a Japanese game: label match by title"),
    ("gb", "E:Downloads/ROMs/gb/LanternOfDarkness_demo_v1_0.gb", "file",
     "Homebrew: no official art, generated label"),
    # ---- Game Boy Color -------------------------------------------------
    ("gbc", "F:Curated ROMS/gbc/Legend of Zelda, The - Link's Awakening DX (USA, Europe) (SGB Enhanced) (GB Compatible).zip", "file",
     "GB Compatible colour game; Gambatte vs mGBA"),
    ("gbc", "F:Curated ROMS/gbc/Pokemon - Crystal Version (USA, Europe) (Rev 1).zip", "file",
     "Real-time clock save"),
    ("gbc", "F:Curated ROMS/gbc/Pokemon Pinball (USA, Australia) (Rumble Version) (SGB Enhanced) (GB Compatible).zip", "file",
     "Rumble cart: the KONKR should buzz"),
    ("gbc", "F:Curated ROMS/gbc/Wario Land 3.zip", "file", "Plain GBC game"),
    ("gbc", "E:Projects/slot-for-kpa/motion test ROMs/Kirby - Tilt 'n' Tumble (USA).gbc", "file",
     "Motion (MBC7 tilt): switch to mGBA with SELECT; Pause > Emulation > Sensitivity"),
    ("gbc", "E:Downloads/ROM Hacks/gbc/polishedcrystal-3.0.0-beta.gbc", "file",
     "Hack: borrows Crystal's label with the file name band; SELECT then X toggles it"),
    ("gbc", "F:Curated ROMS/ROM Hacks/gbc/Metroid II - Color (ejrtq) v1.3.gbc", "file",
     "Hack: a GB game recoloured as .gbc"),
    ("gbc", "E:Downloads/ROMs/gbc/walfies-nonograms.gbc", "file", "Homebrew: generated label"),
    # ---- Game Boy Advance -----------------------------------------------
    ("gba", "F:Curated ROMS/gba/Pokemon - Emerald Version (USA, Europe).gba", "file",
     "Flash save (Pokemon gen 3 guard), saves with the ROMs in gba/mGBA/"),
    ("gba", "F:Curated ROMS/gba/Metroid - Zero Mission (USA).zip", "file",
     "Zipped cart; mGBA vs gpSP (SELECT); Run-Ahead"),
    ("gba", "F:Curated ROMS/gba/Drill_Dozer.zip", "file",
     "Rumble without the Game Boy Player screen (mGBA)"),
    ("gba", "F:Curated ROMS/gba/Kirby & The Amazing Mirror (USA).zip", "file", "Plain GBA game"),
    ("gba", GBA7Z, "7z:*/Boktai - The Sun Is in Your Hand (USA).gba",
     "Solar sensor: Pause > Emulation > Sun, L3/R3"),
    ("gba", GBA7Z, "7z:*/Super Mario Advance 4 - Super Mario Bros. 3 (USA).gba",
     "SMA4 Run-Ahead guard (no e-Reader crash)"),
    ("gba", "E:Downloads/Full Sets/gba/Nintendo - Game Boy Advance (Video)/Game Boy Advance Video - Pokemon - Volume 1 (USA).zip", "file",
     "GBA Video: boots past the Game Boy Player check"),
    ("gba", "E:Projects/slot-for-kpa/motion test ROMs/WarioWare - Twisted! (USA, Australia).gba", "file",
     "Motion (gyro): turn the KONKR; Sensitivity row and L3/R3"),
    ("gba", "E:Projects/slot-for-kpa/motion test ROMs/Korokoro Puzzle - Happy Panecchu! (Japan).gba", "file",
     "Motion (tilt)"),
    ("gba", "E:Downloads/ROMs/gba/240pee_mb.gba", "file",
     "240p test suite: shaders, LCD effects, Pixel Transparency, frame blending"),
    ("gba", "F:Curated ROMS/ROM Hacks/gba/Castlevania Aria of Sorrow Definitive Edition (Tommy Edition) v1.0.gba", "file",
     "Hack: borrowed label + file name band"),
    ("gba", "F:Curated ROMS/ROM Hacks/gba/Emerald Cross (v2.0.1).gba", "file",
     "Hack of Emerald: same label as Emerald, told apart by the band"),
    ("gba", "F:Curated ROMS/ROM Hacks/gba/modernv3.0.gba", "file",
     "Second Emerald hack: band; Cart Studio File name row"),
    ("gba", "F:Curated ROMS/Translations/gba/Mother 1+2 (Japan) (patched).gba", "file",
     "Translation: Japanese title, label search"),
    ("gba", "F:Curated ROMS/Translations/gba/Rhythm Tengoku (English v1.0).zip", "file",
     "Translation (zipped)"),
    # ---- NES ------------------------------------------------------------
    ("nes", NES + "Super Mario Bros. 3 (USA).zip", "file", "MMC3; FCEUmm vs Nestopia (SELECT)"),
    ("nes", NES + "Mega Man 2 (USA).zip", "file", "Plain NES game"),
    ("nes", NES + "Kirby's Adventure (USA)*.zip", "file", "Big MMC3 cart, battery save"),
    ("nes", NES + "Castlevania III - Dracula's Curse (USA).zip", "file", "MMC5"),
    ("nes", NES + "Akumajou Densetsu (Japan).zip", "file", "VRC6 expansion audio"),
    ("nes", "E:Downloads/Translations/nes/Akumajyou_Densetsu_(Tr).zip", "file",
     "Translation of the same game: band beside the original"),
    ("nes", "E:Downloads/ROMs/nes/Ghosts'n Goblins (USA).zip", "file", "Original for the Fair Knight hack"),
    ("nes", "E:Projects/Ghosts n Goblins Fair Knight/NES/builds/Ghosts'n Goblins (USA) [Fair Knight NES v0.5.3].nes", "file",
     "Your hack: borrowed label + band"),
    ("nes", "E:Downloads/ROM Hacks/nes/Solomon's Key (USA) [Secret Hints]/Solomon's Key (USA) [Secret Hints].nes", "file",
     "Your hack: label for a game with no original on the shelf"),
    # ---- SNES -----------------------------------------------------------
    ("snes", "F:Curated ROMS/snes/Super Metroid (Japan, USA).sfc", "file", "Snes9x vs bsnes (SELECT)"),
    ("snes", "F:Curated ROMS/snes/Chrono Trigger (USA).sfc", "file", "Battery save"),
    ("snes", "F:Curated ROMS/snes/Super Mario World (USA).sfc", "file", "Plain SNES game"),
    ("snes", "F:Curated ROMS/snes/Super Mario RPG - Legend of the Seven Stars (USA).sfc", "file", "SA-1 chip"),
    ("snes", "F:Curated ROMS/snes/Kirby Super Star (USA).sfc", "file", "SA-1 chip"),
    ("snes", SNESZIP, "zipzip:SNES/Star Fox (USA) (Rev 2).zip", "Super FX chip"),
    ("snes", SNESZIP, "zipzip:SNES/Super Mario World 2 - Yoshi's Island (USA) (Rev 1).zip", "Super FX 2 chip"),
    ("snes", "F:Curated ROMS/snes/Super Punch-Out!! (USA).sfc", "file", "Fast action: Run-Ahead feel"),
    ("snes", "F:Curated ROMS/Translations/snes/Star Ocean (Japan) [T-En by DeJap v1.0].sfc", "file",
     "Translation; S-DD1 chip"),
    ("snes", "F:Curated ROMS/Translations/snes/Bahamut Lagoon (Japan) [T-En by Tom & Near v1.2].sfc", "file", "Translation"),
    ("snes", "E:Downloads/ROM Hacks/snes/Brave New World (Hack v2.0).sfc", "file", "Hack: borrowed label + band"),
    # ---- Genesis / Mega Drive -------------------------------------------
    ("genesis", "F:Curated ROMS/gen/Gunstar Heroes (USA).md", "file", "Genesis Plus GX vs PicoDrive (SELECT)"),
    ("genesis", "F:Curated ROMS/gen/Castlevania - Bloodlines (USA).md", "file", "Plain Genesis game"),
    ("genesis", "F:Curated ROMS/gen/Phantasy Star IV (USA).zip", "file", "Zipped; battery save"),
    ("genesis", "F:Curated ROMS/gen/Alien Soldier (Europe).md", "file", "PAL/50 Hz game: pacing"),
    ("genesis", "E:Downloads/ROMs/genesis/Streets of Rage 2 (USA).zip", "file", "Zipped; 6-button pad"),
    ("genesis", "E:Downloads/ROMs/genesis/Demons of Asteborg*(Aftermarket) (Unl).zip", "file",
     "Modern homebrew: generated label, Dither Blending"),
    # ---- PlayStation ----------------------------------------------------
    ("psx", "F:Curated ROMS/psx/Castlevania - Symphony of the Night (USA).chd", "file",
     "CHD; cover found by its serial (SLUS-00067)"),
    ("psx", "F:Curated ROMS/ROM Hacks/psx/Castlevania - Symphony of the Night (QoL v1.2).chd", "file",
     "Hack: same serial, so same cover with the file name band"),
    ("psx", "F:Curated ROMS/psx/Metal Gear Solid.m3u", "file", "m3u playlist: one cart, Change Disc"),
    ("psx", "F:Curated ROMS/psx/.hidden/Metal Gear Solid (Disc 1).chd", "file>.hidden",
     "(disc of the m3u above, in ES-DE's .hidden folder)"),
    ("psx", "F:Curated ROMS/psx/.hidden/Metal Gear Solid (Disc 2).chd", "file>.hidden", "(second disc)"),
    ("psx", "F:Curated ROMS/psx/Parasite Eve (USA) (Disc 1).chd", "file",
     "Multi-disc without an m3u: grouped into one cart"),
    ("psx", "F:Curated ROMS/psx/Parasite Eve (USA) (Disc 2).chd", "file", "(second disc)"),
    ("psx", "E:Downloads/ROMs/psx/Vagrant Story (USA)", "dir", "BIN/CUE"),
    ("psx", "F:Curated ROMS/Translations/psx/Choro Q 3 (English Patched)", "dir",
     "Translation, multi-track BIN/CUE (CD audio)"),
    ("psx", "F:Curated ROMS/Translations/psx/Echo Night - The Lord of Nightmares (Japan) [T-En by Devil Hackers v1.0a] [n].chd", "file",
     "Translation CHD: Japanese serial"),
    ("psx", "F:Curated ROMS/psx/Ape Escape (USA).chd", "file", "Analog sticks and DualShock rumble"),
    # ---- TurboGrafx-CD / PC Engine CD -----------------------------------
    ("tg-cd", "F:Curated ROMS/ROM Hacks/tg-cd/Castlevania - Rondo of Blood (T-En + English PSP Audio v1.01).chd", "file",
     "TG-CD CHD (System Card 3 BIOS); Press RUN"),
    ("tg-cd", "E:Downloads/Translations/pcenginecd/Snatcher CD-ROMantic (English).7z", "7z:*",
     "TG-CD BIN/CUE"),
    ("tg-cd", "E:Downloads/Translations/pcenginecd/Downtown Nekketsu Monogatari [PCECD][ENG-AI][TruMisery][Japanese Audio][v0.99].7z", "7z:*",
     "TG-CD BIN/CUE translation"),
]

CHECKLIST = """
## Checklist

General (any system):
- [ ] Every system's shelf shows its games; covers or generated labels on all carts.
- [ ] Hacks and translations that borrow another game's label show their file name band.
- [ ] SELECT opens the core menu; X there toggles the file name band; survives a restart.
- [ ] Saves and states land in `<system>/<core>/` beside the ROMs.
- [ ] Wallpaper > OLED Black: pure black behind the carts.
- [ ] Second cold start is quicker than the first (`start pictures` in logcat).

Per feature:
- [ ] GB: palettes on a DMG game; Gambatte <-> mGBA switch keeps the save.
- [ ] GBC: Pokemon Pinball rumbles; Crystal's clock keeps time.
- [ ] GBA: Emerald saves and reloads; Drill Dozer rumbles; Boktai's Sun row; SMA4 doesn't crash with Run-Ahead.
- [ ] GBA Video plays (no Game Boy Player hang).
- [ ] Motion: WarioWare (gyro), Korokoro and Kirby Tilt 'n' Tumble (tilt, on mGBA); Sensitivity changes it.
- [ ] NES: MMC5 (Castlevania III) and VRC6 audio (Akumajou Densetsu) on both cores.
- [ ] SNES: SA-1, Super FX, Super FX 2 and S-DD1 games boot on Snes9x and bsnes.
- [ ] Genesis: both cores; Alien Soldier (PAL) runs at the right speed.
- [ ] PS1: CHD, m3u (Change Disc), grouped multi-disc, BIN/CUE, multi-track CUE (CD music), translation CHD; Ape Escape's sticks and rumble.
- [ ] TG-CD: CHD and BIN/CUE boot past Press RUN.
"""


def real(path, roots):
    drive, rest = path.split(":", 1)
    return os.path.join(roots[drive.upper()], rest)


def resolve(pattern):
    """A path whose parts may hold globs; the first match, or None."""
    parts = pattern.split("/")
    cur = [parts[0] if parts[0] else "/"]
    for part in parts[1:]:
        nxt = []
        for base in cur:
            if any(c in part for c in "*?"):
                # [ ] are literal in file names (ROM tags), not glob classes
                glob = part.replace("[", "[[]")
                try:
                    nxt += [os.path.join(base, n) for n in sorted(os.listdir(base)) if fnmatch.fnmatch(n, glob)]
                except OSError:
                    pass
            elif os.path.lexists(os.path.join(base, part)):
                nxt.append(os.path.join(base, part))
        cur = nxt
    return cur[0] if cur else None


def same_drive(a, b):
    while b and not os.path.exists(b):  # dest may not exist yet: nearest parent
        b = os.path.dirname(b)
    try:
        return os.stat(a).st_dev == os.stat(b).st_dev
    except OSError:
        return False


def place(src, dst, apply):
    """Hard link on the same drive, else copy. Skips a dst that already matches."""
    if os.path.exists(dst) and os.path.getsize(dst) == os.path.getsize(src):
        return "have"
    how = "link" if same_drive(src, os.path.dirname(dst)) else "copy"
    if apply:
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        if how == "link":
            try:
                os.link(src, dst)
            except OSError:
                shutil.copy2(src, dst)
                how = "copy"
        else:
            tmp = dst + ".part"
            shutil.copyfile(src, tmp)
            os.replace(tmp, dst)
    return how


def extract_7z(src, member_glob, dest_dir, apply):
    """member_glob "*": the whole archive (a disc) into its own folder,
    dest_dir/<archive name>/, without a single top folder it may have.
    Otherwise: the matching files, flattened into dest_dir."""
    import py7zr  # only needed for .7z sources
    with py7zr.SevenZipFile(src) as a:
        files = [i.filename for i in a.list() if not i.is_directory
                 and fnmatch.fnmatch(i.filename, member_glob.replace("[", "[[]"))]
    if not files:
        return [], "no member matches %s" % member_glob
    if member_glob == "*":
        out = os.path.join(dest_dir, os.path.splitext(os.path.basename(src))[0])
        tops = {f.split("/")[0] for f in files}
        strip = len(tops) == 1 and all("/" in f for f in files)
        rel = [f.split("/", 1)[1] if strip else f for f in files]
    else:
        out = dest_dir
        rel = [os.path.basename(f) for f in files]
    targets = [os.path.join(out, r) for r in rel]
    if all(os.path.exists(t) for t in targets):
        return targets, "have"
    if apply:
        tmp = os.path.join(dest_dir, ".extract")
        shutil.rmtree(tmp, ignore_errors=True)
        with py7zr.SevenZipFile(src) as a:
            a.extract(path=tmp, targets=files)
        for f, t in zip(files, targets):
            os.makedirs(os.path.dirname(t), exist_ok=True)
            os.replace(os.path.join(tmp, f), t)
        shutil.rmtree(tmp, ignore_errors=True)
    return targets, "extract"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--e", default="/mnt/e")
    ap.add_argument("--f", default="/mnt/f")
    ap.add_argument("--dest", default=None)
    ap.add_argument("--only", default="")
    ap.add_argument("--apply", action="store_true")
    a = ap.parse_args()
    roots = {"E": a.e, "F": a.f}
    dest = a.dest or os.path.join(a.e, "Projects", "slot-for-kpa", "test ROM set")
    only = {s.strip() for s in a.only.split(",") if s.strip()}

    rows, missing = [], []
    for system, source, how, note in SET:
        if only and system not in only:
            continue
        sysdir = os.path.join(dest, system)
        src = resolve(real(source, roots))
        if not src:
            missing.append((system, source))
            continue
        if a.apply:
            os.makedirs(sysdir, exist_ok=True)
        if how == "file" or how.startswith("file>"):
            sub = how[5:] if how.startswith("file>") else ""
            dst = os.path.join(sysdir, sub, os.path.basename(src))
            did = place(src, dst, a.apply)
            shown = [os.path.join(sub, os.path.basename(src)) if sub else os.path.basename(src)]
        elif how == "dir":
            did = "dir"
            out = os.path.join(sysdir, os.path.basename(src))
            for name in sorted(os.listdir(src)):
                full = os.path.join(src, name)
                if os.path.isfile(full) and not name.lower().endswith((".ppf", ".txt")):
                    did = place(full, os.path.join(out, name), a.apply)
            shown = [os.path.basename(src) + "/"]
        elif how.startswith("7z:"):
            targets, did = extract_7z(src, how[3:], sysdir, a.apply)
            if not targets:
                missing.append((system, source + " (" + did + ")"))
                continue
            shown = [os.path.relpath(t, sysdir) for t in targets if not t.lower().endswith(".bin")] or \
                    [os.path.relpath(t, sysdir) for t in targets]
        elif how.startswith("zipzip:"):
            member = how[7:]
            dst = os.path.join(sysdir, os.path.basename(member))
            did = "have" if os.path.exists(dst) else "unzip"
            if a.apply and did != "have":
                with zipfile.ZipFile(src) as z, z.open(member) as fh, open(dst + ".part", "wb") as out:
                    shutil.copyfileobj(fh, out)
                os.replace(dst + ".part", dst)
            shown = [os.path.basename(member)]
        else:
            sys.exit("unknown how: " + how)
        print("[%s] %-7s %s" % (system, did, " + ".join(shown)))
        rows.append((system, shown, note, source))

    if a.apply and not only:
        md = ["# slot. test ROM set", "",
              "Built by `tooling\\make_test_set.py` (re-run it to restore anything removed). "
              "Files from E: are hard links, files from F: are copies; the originals stay where they are. "
              "Copy each folder to the KONKR's ROM folder of the same name (ES-DE layout).", "",
              "| System | Game | What it tests |", "|---|---|---|"]
        for system, shown, note, source in rows:
            md.append("| %s | %s | %s |" % (system, "<br>".join(shown), note))
        if missing:
            md += ["", "## Not found", ""] + ["- %s: `%s`" % m for m in missing]
        md.append(CHECKLIST)
        with open(os.path.join(dest, "README.md"), "w", encoding="utf-8", newline="\r\n") as fh:
            fh.write("\n".join(md) + "\n")

    if missing:
        print("\nNot found:")
        for m in missing:
            print("  - %s: %s" % m)
    print("\n%s %d entries into %s" % ("Placed" if a.apply else "Would place", len(rows), dest))


if __name__ == "__main__":
    main()
