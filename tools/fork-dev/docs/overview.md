# slot. for KONKR — what this fork adds (plain-language overview)

slot. is the game front end (menus plus emulators) on the KONKR Pocket Advance.
Andrew's fork adds PlayStation and TurboGrafx-CD support and the extras below.
Each feature is kept on its own branch so it can be offered to slot.'s author
separately. The test builds combine all of them.

Latest build: **slot v2.2.2 (357) test run 12** (personal copy, with BIOS).
It installs as a separate app ("slot. diag") next to the normal slot., so the
normal app's saves are never touched.

## What's new in this round

**Watomsk overlays for PS1 and TurboGrafx / PC Engine.**
These are the decorative frames around the game picture, made by the artist
Watomsk. They come in a 3:2 version (KONKR, RP Classic) and a 16:9 version
(RP Nova, 1080p screens). Choose one in Settings → Screen → Overlay.

**Your BIOS files come pre-installed (personal APK only).**
The BIOS from your own consoles are packed into the personal copy. On first
launch they are copied into slot.'s BIOS folder, so PS1, TurboGrafx-CD and the
GB/GBC/GBA boot screens work straight away. This copy is never uploaded
anywhere. The public branches don't contain any BIOS.

**Every game gets a box label.**
- ROM hacks and translations show the original game's box, labelled with the
  hack's own filename. Example: "Pokemon Emerald – Battle Frontier Start" shows
  the Pokémon Emerald box.
- PS1 and TurboGrafx-CD discs now get labels too. For PS1, slot. reads the
  game's product code off the disc, so even renamed files usually match.
- When slot. isn't sure, it still takes its best guess rather than leaving the
  cart blank.
- If a guess is wrong, open the game in **Cart Studio**: switch the label off
  or pick a different one with **Search game…**.

**Multi-disc PS1 games are one cart.**
Final Fantasy VII's three discs, for example, show up as one game. To switch
discs, open the pause menu's Settings and choose **Change Disc**. All discs share one
memory card, so your save from disc 1 carries on to disc 2. If you already
had saves from the separate discs, the most recent one is copied over to the
shared card the first time (the old saves are left in place).

**CRT shaders now look right on PS1 high-resolution screens.**
Some PS1 games switch to a 480-line picture (menus, FMVs, some 2D games).
Before, the CRT shaders drew twice as many, much thinner scanlines on those
screens. Now they look the same as the normal 240-line picture.

**Black Frame Insertion (BFI), off by default.**
BFI blanks the screen between game frames, the way a CRT flickers. This makes
fast motion look much sharper, but it needs a screen running at least twice the
game's speed (120 Hz for most games). Turn it on per system in the pause menu
→ Black Frame Insertion. The settings are:
- **Brightness boost**: brightens the lit frames, because BFI makes the
  picture darker.
- **Dark frame level**: how dark the blank frames are. 0 is fully black.
- **Frames**: how many blank frames go between game frames. Auto picks the
  number from the screen's speed.

On a 60 Hz screen the BFI option is hidden, because BFI would only flicker
there.

## Not done yet / ideas on the shelf
- CRT beam simulator (a closer CRT imitation that needs a 120 Hz+ screen).
  Parked by Andrew.
- Cheats for disc games (PS1/TG-CD).
- Small logo strips for NES/SNES/PS1/TG on narrow screens.

## What still needs testing on a real device
- Installing run 12 over run 11.
- BFI on a 120 Hz screen.
- Change Disc in a real multi-disc game.
- That labels appear for PS1 and TG-CD discs.
