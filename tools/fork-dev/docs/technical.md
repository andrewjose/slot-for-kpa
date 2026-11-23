# slot. fork: technical reference

This document covers the internals of the work in test runs 9–12. Paths are
relative to the repository root. Kotlin files live in
`android/app/src/main/java/fyi/slot/konkr/`.

## 0. Build, test and release mechanics
- **CI.** `.github/workflows/test-apk.yml` on `build/test-apk-v2.2.2`. Every
  push builds the debug variant (`applicationIdSuffix ".diag"`, package
  `fyi.slot.konkr.diag`). It is signed with `.github/test-signing/debug.keystore`
  (store password `android`, alias `androiddebugkey`, cert SHA-256
  `d14befdc8b72…bd2c`). The build is published as the prerelease
  `test-v2.2.2-mac-lab-357-run<N>`.
- **Downloading builds.** Asset downloads need
  `gh api -H "Accept: application/octet-stream" repos/andrewjose/slot-for-kpa/releases/assets/<id>`.
  The Azure artifact links and GraphQL are blocked from the cloud workspace.
- **Personal APK.** Built with `tooling/personal-apk/`:
  1. `add_bios.py` copies every entry into a new zip, which drops the v2
     signing block because it isn't a zip entry. It then adds
     `assets/bios/<lowercase name>`, deflated.
  2. `repro-apk zipalign -P 16 4` aligns entries: 4 bytes in general, and
     16 KiB pages for `.so` files.
  3. `v2sign.py` signs it with APK Signature Scheme v2. It computes 1 MiB
     chunk SHA-256 digests over [entries | central directory | EOCD, with the
     CD offset pointing at the signing block], builds the signed-data
     structure (digests, certificate chain, no additional attributes), and
     signs it with RSA PKCS#1 v1.5 / SHA-256 (algorithm id 0x0103). The block
     is inserted before the central directory and the EOCD offset is patched.
     No v1 signature is needed because minSdk ≥ 24.
  4. `apksigtool verify` checks the result.
- **BIOS seeding.** `BiosLibrary.seedBundled(context)` runs on the gameLoader
  thread at startup. It copies each `assets/bios/*` file to the BIOS folder
  only when the destination is missing, so files the user placed are never
  overwritten.
- **Runtime tests.** `crates/slot-android/src/runtime*` is `cfg`'d to Android
  unless `--features runtime-tests` is set, and that feature links `libEGL`.
  Run: `RUSTFLAGS="-L <dir>" cargo +stable test -p slot-android --features runtime-tests --lib`.
  Result: 438 passed on the final code. Two pre-existing test files needed
  updating:
  - `game_runahead_tests.rs` was given the new `disc_memory` field.
  - `ui_feedback_tests.rs` expected the old PSX/TG Screen page rows, before
    rows 90/72/75 were added.

## 1. Watomsk overlays (`pr/tgcd`)
- **Export.** `tools/watomsk-overlays/export.py` maps Watomsk's source
  folders to `VARIANTS`. PSX uses `PSX`; the TurboGrafx and PC Engine variants
  come from `Turbografx16`. A missing folder is skipped rather than treated as
  fatal. The output is `assets/watomsk/{psx,turbografx,pcengine}-{3x2,16x9}.png`.
- **Catalogue.** `slot-ui/src/watomsk.rs` gains `Design::Psx`, `Turbografx`
  and `PcEngine`. Each has the opaque-window rectangle that `video_mode.rs`
  fits the picture into.
- **Settings.** New `Bezel` variants, plus `Bezel::PSX` and `Bezel::TGCD`
  lists. They are stored in `bezel_psx` (settings key 72) and `bezel_tgcd`
  (key 75). Value-name strings are 116–118. Scoped per-game keys are
  registered in `emulation.rs`.
- **Layout.** In `video_mode.rs`, PSX and TGCD take the NES path: the
  picture is fitted into the frame's window. The overlay preview test
  includes their panels.
- **Licence.** `licenses/watomsk-overlays.txt`.

## 2. Label guessing (`pr/smart-labels`)
**Pipeline.** `AutoCartLabels.guessScan` and `HomeCartLabels.guessLabel`. The
second also works with no ScreenScraper credentials.
1. **Clues.**
   - The filename.
   - Header titles from `CartGuess.headerTitle`: GB/GBC `0x134..0x143`, GBA
     `0xA0..0xAC`. The 4-character GBA code comes from `0xAC..0xAF`
     (`gameCode`); hacks almost never change it.
   - For PS1 discs, a serial from `DiscId.serialFromFd`. This is JNI into
     `disc_serial.rs::serial(file, ext)`:
     - A CHD goes through the `chd` crate (0.4, `default-features = false`,
       `features = ["std"]`; frames are 2448 bytes, 2352 of sector plus 96 of
       subcode). ISO9660 is walked to the root `SYSTEM.CNF` and
       `BOOT = cdrom:\SLUS_001.13;1` is normalised to `SLUS-00113`.
     - A PBP has `DISC_ID` read from its PARAM.SFO.
     - Otherwise the serial is taken from a code in the filename.
2. **Known titles.** `ArtCatalogs` caches libretro-database clrmamepro DATs
   (`metadat/no-intro/*`, `metadat/redump/Sony - PlayStation.dat`) and the
   libretro-thumbnails `Named_Boxarts` name lists. The lists come from the
   GitHub git-trees API; symlink entries are resolved through their target
   text. Everything refreshes after 7 days. `parseDat` keeps
   `name`, the first ROM's `crc` and `serial`.
3. **Scoring.** `ArtGuess.rank`.
   - `titleKey` normalises names: lowercase, accents folded, tags and
     version marks dropped.
   - `titleScore` takes the best of four scores:
     - 1.0 for an exact match.
     - Prefix: the official words start the file's words, minus hack words
       (`0.8 + 0.15·|official|/|file|`).
     - Shorter: the file's words start the official title
       (`0.7 + 0.25·|file|/|official|`).
     - Word cover: `0.62·cover + 0.28·own-word hit rate`.
     - `0.85 ×` string similarity.
   - Header titles are weighted ×0.95, and the filename leads.
   - A serial or GBA-code hit (`serialScore`) raises the score to at least
     0.5, then adds 0.6 for an exact code or 0.25 for a GBA code that differs
     only in its region letter.
   - Candidates below `FLOOR = 0.42` are dropped. The rest get a `preference`
     bonus for region and disc number.
   - The best names are tried against the fork's own art set by that game's
     CRC, then slot.'s set (GB family); for discs, libretro-thumbnails
     Named_Boxarts.
4. **Art name.** `thumbnailName` applies libretro's file-name rules
   (`&*/:\`<>?\\|"` → `_`). `DiscLabels` turns the Named_Boxarts image into the
   disc "cover" label (`studioCover`) and records the publisher through
   `studio()`.
5. **Display.** The art comes from the matched name. The cart title stays the
   user's filename.

**Undo.** Cart Studio, slot.'s existing per-cart editor. The label can be
switched off, or replaced with *Search game…*, which lists the system's
official names (carts) or box art names (discs) to pick from.
`DiscLabels.studioCover` serves the chosen cover as the Real Label.
ScreenScraper credentials are optional: without them (any fork build), NES,
SNES and MD carts still get labels from the fork's own set and from
guesses.

**Tests.** `ArtGuessTest` covers about 65 cases from Andrew's library,
including Japanese-to-English base titles, `[T-En]` and Rev tags, and multi-disc
sets.

## 3. Multi-disc PS1 (`pr/multi-disc`)
- **Grouping.** `DiscGroups`.
  - `discNumber` recognises `(Disc N)`, `[Disk N]`, `(CD N)` and `(Disc N of M)`.
  - `gameName` removes the disc tag, replacing it with a single space, then
    tidies whitespace. This fixed the "Xenogears[Retranslated]" bug.
  - `parseM3u` resolves relative entries.
  - In `RomLibrary`, a pre-pass groups sibling files by
    `(folder, gameName, extension class)`. It emits one entry with JSON
    `discs: [uri…]` and `disc_names`, and hides discs that an `.m3u` already
    names.
  - `m3u` is in `DISC_EXT` and `TGCD_EXT`.
- **Launch.**
  - `DiscSource.linkSet` opens each disc (CHD or PBP only) as a
    `ParcelFileDescriptor` and writes an `.m3u` into `cacheDir/discs/` whose
    lines are `/proc/self/fd/<n>` paths. The core opens images by path, and
    the extension tells PCSX ReARMed CHD from PBP. The descriptors stay open
    until the next game is linked, so a Run-Ahead copy can reopen them.
  - The scanned file name is `"$title.m3u"`, so saves and states use the set
    name.
- **Libretro side** (`slot-retro`).
  - The host implements `RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE` (13)
    and `GET_DISK_CONTROL_INTERFACE_VERSION` (57, returns 1), and stores the
    `SET_DISK_CONTROL_EXT_INTERFACE` (58) callbacks.
  - Before `retro_load_game`, `set_initial_disc(index, path)` calls
    `set_initial_image`, so the game resumes on the disc it was last on.
  - `set_disc` ejects the tray, sets the index (re-closing the tray if that
    fails), then holds the tray open for 30 `retro_run` frames
    (`disc_tray`). PCSX ReARMed needs to see the lid open.
- **Runtime.**
  - `game.rs` holds `disc_memory` (the last index, saved alongside the game's
    state) and `next_disc()`.
  - The pause Settings row *Change Disc* (string 137) is listed only for
    multi-disc games. It calls `change_disc()`, which moves to the next disc
    and wraps around to the first.
  - The run-ahead secondary instance copies the change through
    `remembered_disc`, so the two cores never disagree on the inserted disc.
- **Saves.** One memory card, named after the set. `adoptDiscSave(save, discNames)`
  runs when the set's `.srm` doesn't exist. It copies the most recently
  modified non-empty per-disc `.srm` without overwriting anything. Nothing is
  deleted.
- **Tests.** `DiscGroupsTest`: tags, m3u parsing, groups across mixed
  folders, and the adopt order.

## 4. CRT on 480-line PS1 modes (`pr/crt-interlace`)
`GAME_FRAG` now has a global `vec2 crt_src;`. `main()` sets
`crt_src = u_src.y > 300.0 ? vec2(u_src.x, u_src.y*0.5) : u_src;`, and every
CRT mode that derives scanline phase or pitch from the source size reads
`crt_src` instead of `u_src`: zfast, crt-pi, aperture, fakelottes, hyllian,
GDV and easymode. Texture sampling still uses the real `u_src`, so the
480-line image detail is kept and only the beam spacing follows the 240-line
field rate. crt-geom is unchanged because it already has its own interlace
detection. All 26 `u_mode` values compile under GLSL ES 1.00 on Mesa.
`render.py` shows the 240-line and 480-line columns matching.

## 5. Black Frame Insertion (`pr/bfi`, stacked on multi-disc)
- **Settings** (`settings.rs`).
  - `bfi_systems: u16` holds one bit per `Platform`, all off by default.
  - `bfi_boost` takes `BFI_BOOSTS` = 100..200 in steps of 10 (%).
  - `bfi_dark` takes `BFI_DARKS` = 0..50 in steps of 10 (%).
  - `bfi_frames`: 0 = auto, otherwise 1–3.
  - Keys: 80 = on/off for the running system, 81 = boost, 82 = dark,
    83 = frames.
  - `bfi_plan(n) -> (lit_gain, dark_gain, dark_frames)`. Auto means n−1 dark
    frames; a fixed count is clamped to n−1.
- **Display.**
  - In `MainActivity`, `fastestDisplayMode()` picks the highest-refresh
    `Display.Mode` at the current resolution.
  - `updateRefreshPreference()` polls `nativeHighRefreshWanted()` (true when
    a game is playing with BFI on) and sets `preferredDisplayModeId` to that
    mode, or back to 0.
  - `publishDisplayRefresh` reports both the current refresh
    (`DISPLAY_REFRESH_MILLIHZ`) and the maximum (`nativeSetMaxRefresh` →
    `MAX_REFRESH_MILLIHZ`).
- **Clocking.**
  - `bfi_refreshes(display_hz, fps)` returns `n = round(hz/fps)` when n is
    between 2 and 8 and `ra_sync::display_can_clock_core(hz/n, fps)`
    accepts it.
  - In the clocked loop, `self.bfi_phase` counts refreshes. The number of
    core frames to run is the number of n-boundaries crossed. The last frame
    is re-presented on the in-between refreshes.
  - `self.bfi_gain` is `boost` while `phase % n < n − darks` and `dark` after
    that, so each game frame is lit first and then dark.
  - The phase resets only when BFI turns off. It used to reset whenever the
    gain was 1.0, which broke BFI at 100% boost.
- **Rendering.** `pipeline.rs` gains `gain` and `set_gain`, and the shader
  uniform becomes `u_bright = screen_brightness(power) * gain`. Dark frames
  therefore still go through the selected shader; they are not a glClear.
  `fbo.rs` gains `set_picture_gain` for the FBO path. Gain above 1.0 clips,
  which is the boost's intended trade-off.
- **Menu.**
  - The `PauseBfiSettings` overlay (id 42) has rows
    `BFI_ROWS = [138,140,141,142]` (strings), `BFI_KEYS = [80,81,82,83]` and
    heading 139.
  - The entry row 138 is listed in every `pause_settings_rows` variant, but
    `bfi_offered()` hides it unless `MAX_REFRESH_MILLIHZ / fps ≥ 2`.
- **Test.** `black_frame_insertion_needs_twice_the_games_rate`:
  - 60 Hz gives None.
  - 120 Hz with 60 fps gives 2.
  - 240 Hz gives 4.
  - 120 Hz with 50 fps gives None.

## 6. Known gaps and risks
- Nothing in runs 9–12 has been checked on a device: the v2-signed install
  over run 11, the refresh switch, PCSX ReARMed's swap timing, or how the
  `/proc/self/fd` m3u behaves on Android 13/14.
- BFI depends on the panel's mode list. If a device reports 120 Hz but won't
  switch to it while running full-screen GL, BFI stays hidden.
- Label guessing can pick the wrong game in a series (numbered sequels). That
  is why Clear and Search exist.
- `gexpress.pce` is not bundled because slot. requires 256 KiB System Cards.
