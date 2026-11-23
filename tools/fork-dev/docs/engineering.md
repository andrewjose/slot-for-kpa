# slot. fork — engineering guide (for software engineers new to emulators)

## The shape of the app
- **Kotlin Android app** (`android/app/src/main/java/fyi/slot/konkr/`). It
  handles the home screen, ROM scanning, the label/box-art cache, file access
  (Android's Storage Access Framework, so every file is a `content://` URI),
  and display modes.
- **Rust native library**, loaded through JNI. It runs the game loop, the
  pause menus and settings, OpenGL ES rendering, and the emulator cores.
  - `crates/slot-retro`: hosts the emulators. They are *libretro cores*:
    shared libraries with a C API. The host calls `retro_run()` once per game
    frame. The core calls back into the host through `environment(cmd, data)`
    to ask for things or to hand over interfaces.
  - `crates/slot-gfx`: the GL pipeline. The game frame is uploaded as a
    texture and drawn through one big fragment shader, `GAME_FRAG`, which
    picks its effect from a `u_mode` uniform (CRT, LCD and so on).
  - `crates/slot-android`: the runtime. It covers the frame loop, the menus
    (`runtime.rs`), settings (`settings.rs`, a packed binary format with
    numbered keys), per-game state (`game.rs`), and screen layout
    (`video_mode.rs`).
  - `crates/slot-ui`: drawing helpers, including the Watomsk overlay catalogue.

## Branch model
Every feature has its own `pr/<topic>` branch, cut from upstream v2.2.2, so it
can go upstream as a clean PR. `build/test-apk-v2.2.2` merges all of them and
adds the CI workflow and the committed *test* signing key. Every push to the
build branch makes GitHub Actions build an APK and publish it as a prerelease
named `test-v2.2.2-mac-lab-357-runN`.

| Branch | Feature | Notes |
|---|---|---|
| `pr/tgcd` | PS1/TG-CD Watomsk overlays (with earlier TG-CD work) | |
| `pr/smart-labels` | Label guessing | |
| `pr/multi-disc` | Multi-disc PS1 | |
| `pr/bfi` | Black Frame Insertion | Stacked on `pr/multi-disc`, which must merge first |
| `pr/crt-interlace` | CRT 480-line fix | Based on `pr/tgcd` |
| `build/test-apk-v2.2.2` | All of the above plus CI | Never open a PR from this branch |

The personal APK is the CI APK with BIOS added and re-signed locally (see
`tooling/README.md`). BIOS never go into git: `android/app/src/main/assets/bios/`
is in `.gitignore`.

## Features, as an engineer would want them explained

### Overlays
These are PNG frames exported from Watomsk's source set by
`tools/watomsk-overlays/export.py`, in 3:2 and 16:9 versions. Each system gets
a per-system overlay setting (`bezel_psx` and `bezel_tgcd`, settings keys 72
and 75). `video_mode.rs` lays out PS1 and TG like the NES: an integer-scaled
picture centred inside the frame's window.

### Label guessing
The goal is to always show *some* box. A box is chosen in four steps:
1. **Identify the game.** For GB/GBC/GBA carts, read the internal header
   title (and the GBA game code) with `CartGuess`. For PS1 discs, read the
   product code (e.g. `SLUS-00067`) from `SYSTEM.CNF` inside a CHD, or from a
   PBP's PARAM.SFO (`disc_serial.rs`, in Rust).
2. **Look it up in a DAT.** DAT files list every known release. They come from
   No-Intro for carts and Redump for discs, via libretro-database, and are
   cached for 7 days (`ArtCatalogs`).
3. **Match a box image name.** Fuzzy-match the cleaned title against
   libretro-thumbnails' list of box art names (`ArtGuess`). Hack and
   translation tags such as `[Hack]`, `(Translated)` and "Retranslated" are
   stripped first, so a hack lands on its base game. Any match scoring above
   0.42 is accepted.
4. **Show it.** The cart shows the box, and the title stays the filename.

A wrong guess is fixed in Cart Studio: turn the label off, or choose another
with **Search game…**, which lists the system's names and box arts.

### Multi-disc
`DiscGroups` groups files that differ only by "(Disc N)". It also reads
`.m3u` playlists. The scanner emits one entry per set, with `discs` and
`disc_names` fields. At launch, the app writes a temporary `.m3u`
(`DiscSource.linkSet`) that the PS1 core understands.

Disc swapping uses libretro's *disk control interface*, which the core hands
to the host. A swap is a short sequence: open the tray, change the index, wait
30 frames, close the tray. The core needs those frames to notice the change.
`set_initial_image` starts the game on the disc you last used.

The memory card is saved under the set's name. The first time a set is
opened, `adoptDiscSave` copies the newest non-empty per-disc `.srm` to it and
leaves the old ones alone.

### CRT 480-line fix
CRT shaders size their scanlines by the source height. A 480-line PS1 frame
is interlaced: it is really two 240-line fields woven together. In the shader,
anything taller than 300 lines is now treated as half its height, through a
global `crt_src` that every CRT mode uses. The exception is the CRT-Geom
shader, which already handled this.

### Black Frame Insertion
The screen must refresh N times per game frame (N ≥ 2). On lit refreshes the
game picture is drawn with a brightness gain (*boost*). On the remaining
refreshes it is drawn at the *dark* level.

The app asks Android for the fastest display mode when BFI is wanted, and
reports the maximum refresh rate to Rust. The game clock then advances one
game frame every N refreshes. BFI is off for every system unless that
system's bit is set in `bfi_systems`. The menu entry only appears when the
display can reach at least twice the game's frame rate.

## Testing
- **Kotlin**: JVM unit tests for the pure parts (`ArtGuessTest`,
  `DiscGroupsTest`). `tooling/kotlin-typecheck` checks that new files compile
  without the Android SDK.
- **Rust**: `cargo test` for the libraries. The runtime itself is only
  compiled when the `runtime-tests` feature is on (the exact command is in
  `tooling/README.md`); 438 tests passed on run 12's code.
- **Shaders**: `tooling/shader-check` compiles every mode as GLSL ES 1.00 and
  renders before/after comparison pictures.
- **Device**: anything involving installs, refresh rate or real cores is
  tested on the KONKR. See the checklist in `HANDOFF.md`.
