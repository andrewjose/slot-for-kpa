# slot. for KONKR (andrewjose/slot-for-kpa) — HANDOFF / status

_Last updated 2026-11-27._

## 0. Resume here
- **Current build:** `slot 2.2.2-fork.37 - UMD case + Reset to Fresh Install (personal)` (BIOS + Kingdom Hearts wallpaper inside: never upload). fork.37: PSP = `HomeMould::Umd` / `Outline::Umd` ("umd"), art by `tools/home-art/umd.py` (312x540, well 22,22,280,496), shown at `UMD_GROW` = 1.2*276/540 so it stands as tall as a jewel case; disc moulds interchangeable on any disc system. Settings row 151 "Reset to Fresh Install" -> UI action 51 (MainActivity.askResetSettings) -> nativeResetSettings -> Input::ResetSettings -> `Settings::reset_to_fresh_install` (keeps orientation + user.png wallpaper; unsets every system default core / Screen Size, re-seeds GB/GBC) -> UI action 52 -> restartSlot(). fork.36: Library > Redo All Labels (LIBRARY_ROWS row 150 -> UI action 50 -> MainActivity.askRedoAllLabels AlertDialog: Keep My Picks / Redo Everything / Cancel -> CartLabelReset.reset on the label worker; picks = `Labels/<key>.picked`, written by Cart Studio commit_cart and Import Label since fork.36). fork.35: Japanese box art + title band for translations. Personal APK = release asset + `tooling\add_bios.py --wallpaper "personal assets\default-wallpaper.png"` + zipalign + v2 sign.
- **fork.27 + fork.29 (2026-11-26/27, Andrew: "Do all the todos"), untested on a device:** PS1 DualShock sticks/L3/R3, PS1 Controller/Vibration/Memory Card 1/2 settings (cards were empty because slot. never set `pcsx_rearmed_memcard1/2`), CRT Curvature (CRT-Pi, GDV, Geom Deluxe, Fakelottes), band logos for PS1/TG/PCE/NES/SNES (`tools/watomsk-overlays/bands.py`), NES/SNES Integer (No Crop) and 4:3 on narrow screens, PSP launcher shelf (hands games to PPSSPP: Legacy, Gold, then store package), plus queue items 2, 10, 11, 12, 14 (BFI->Native pacing, SELECT menu on one-core systems, disc-hack name band, Orientation, LCD effects at any size). fork.28 failed one Kotlin test (CSO now PSP), fixed in 29. Checklist in the build's `What's new.txt`.
- **Run 26 new:** updater reads andrewjose/slot-for-kpa, versions 2.2.2-fork.N / 357000+N, CI publishes each run as a release (run 25 failed on the version-read step; fixed).
- **Run 23 new (untested):** X in the SELECT core menu toggles the selected cart's `.title` marker, the same as Cart Studio's File name row (`pr/label-name-toggle`). The cart face disk cache now keys on the marker, so a toggled face doesn't come back stale after a restart (this also affected run 20's Cart Studio row). PS1/TG-CD have one core and no SELECT menu, so for them it's Cart Studio only.
- **Run 22 new (untested):**
  - Wallpaper > OLED Black: no picture, and the shelf and hold-scene background is pure black (`pr/oled-black`).
  - Motion control ported from upstream v2.3.0 (`pr/motion-control`): sensors.rs taken from v3.0.0 (gyro worked out from the accelerometer, sensitivity, sample timestamps). mGBA tilt/gyro carts are found by their header (GBA KYG/KHP/RZW, GB/GBC cart type 0x22). Each gets a per-game Sensitivity 1-10 in Pause > Emulation, also on L3/R3 and SELECT+L2/R2.
  - Faster cold start from upstream 5aea7e5/ecf813c/3ffc9df (`pr/faster-start`): start pictures are made on worker threads and cached in `Cache/start-pictures`, with a 4 MB limit so the 960x640 bezels are cached too. Logcat prints `start pictures: 46 in X ms`. Upstream's off-thread settings writes, label workers (96b3d4f) and Rotate fixes were not ported.
- **Run 20 new (untested):** Cart Studio's File name row (Shown/Hidden; in the Background row's place unless the label is a logo) writes or removes `Labels/<key>.title`; picking a label yourself hides the band unless the row was set (`pr/label-name-toggle`).
- **Confirmed on the KONKR (2026-11-23):** saves with the ROMs, AYANEO controls (run 14); PS1 and TG-CD discs boot (run 16).
- **Disc loading, what was wrong:** runs 9-14 never started a disc. Run 14 passed the real file name, which slot.'s `game.rs` refuses ("Unsafe ROM cache file name": the ROM file stem must be the hex digest). Run 16: a digest-named link in `cache/discs/` pointing at the real file (All files access, asked once), else at `/proc/self/fd/N`.
- **Run 19 new (untested):** held X / A-on-DEL repeat on Cart Studio's Search keyboard (`pr/keyboard-repeat`); .cue for PS1 and TG-CD, .iso for PS1 (`pr/cue-iso`: a rewritten cue in `cache/discs/` names each track by its real path); a dark band with the file name on labels borrowed from another game (`pr/borrowed-label-title`: `Labels/<hash>.title` marker, drawn by slot-ui `title_band`).
- **Open question to Andrew:** a one-time "redo automatic labels" so labels saved by runs 10-16 (no marker) get the band (or he sets File name > Shown per cart).
- **Motion test ROMs:** `motion test ROMs\` (WarioWare: Twisted! USA, Korokoro Puzzle JP, Kirby Tilt 'n' Tumble USA; Yoshi Topsy-Turvy isn't in his GBA set), also placed in `_NEEDS-PLAYTEST\gba` and `\gbc` by `make_needs_playtest.py` (entries `slot-motion-gba` / `slot-motion-gbc`).
- **Queue status (2026-11-27):** items 1, 2, 3, 5, 8-15 done (see above). Item 6 done as the launcher route (PSP shelf -> PPSSPP); a real libretro PPSSPP still needs hardware-render support. Still open:
  4. RP Nova centre button: waiting on `adb logcat -s SlotKONKR` of a few presses.
  7. N64 / Dreamcast: ON HOLD (Andrew, 2026-11-26: wait). Decided: other systems get launcher shelves only (hand-off to standalone apps like the PSP), no GPU-drawn libretro cores. Needs his app picks when he resumes. Same blocker as libretro PSP. The PSP hand-off (`Platform::external()`, `HANDOFF` -> `nativePollHandoff`, `PspHandoff.kt`) extends to them once Andrew names his apps (e.g. Mupen64Plus FZ, Redream/Flycast).
  - Maybe: a "picture at the top" choice without a bezel (item 9's extra).
- **Done in fork.32 (2026-11-27), untested on a device:** the six queued to-dos.
  1. Portrait: `PortraitActivity` (subclass of MainActivity, manifest screenOrientation=portrait, disabled by default) is the only launcher entry while Orientation = Portrait (`SlotOrientation.useLauncher`, also synced in onCreate); MainActivity is manifest landscape again. Applies from the next launch. SlotHomeActivity starts whichever entry is enabled.
  2. Fresh-install defaults: `Settings::first_install_defaults` (+ `seed_fresh_install` writes Gambatte and Integer for GB/GBC into Config/default_core.ini and video_mode.ini when unset). 3:2 screens no longer turn the home bezels off on a fresh install.
  3. Wallpaper: `PersonalDefaults.seed` copies assets/personal/wallpaper.png to Wallpapers/user.png on a fresh install; the fresh settings then pick File(user.png).
  4. Emulation bar: `menu_cursor()` (was a hard-coded 3); test `settings_pages_place_the_bar_by_their_own_row_count`.
  5. L1/R1 Rewind/FF default on gb/gbc/nes/tgcd (`Controls::shoulders`), `Hotkeys.shoulders` so the custom-hotkey path and labels follow.
  6. `BootEditionTag.kt` draws AJ EDITION from the wordmark vector's frame.
- **Parked to-do (Andrew, 2026-11-27): carry labels to a new device without re-fetching.** User-initiated only: Library > Export Labels / Import Labels (the import offer on choosing a ROM folder only as a yes/no prompt, if at all; no automatic export in the first version).
  - Today: labels live in slot.'s private storage as `Labels/<key>.png` (+ `.title` band marker, `.picked` own-pick marker), Cart Studio cart shapes as `Studio/<key>.shell`. `<key>` = FNV-1a hash of the cart's content:// URI (core_selection::key / CartLabelPaths.file), so a plain copy only lines up if the new device sees the exact same URI (same SD card, same folder picked). The real-label cache `home-real-labels/<crc>.png` is portable as is.
  - Plan: Library > Export Labels writes them next to the ROMs, keyed by the ROM's path inside the ROM folder instead of the URI hash: `<ROM folder>/slot-labels/<system folder>/<ROM file name>.png` plus one `slot-labels/labels.json` (title band, own pick, Cart Studio shape per cart). It travels with the ROMs (SD card, Syncthing). Library > Import Labels (also offered once after a ROM folder is chosen on a device with no labels): for every cart, find its file by relative path, then by system + file name; copy into Labels under the new key; restore the markers; then a library refresh. Optional later: keep the export up to date automatically after each label is saved.
  - Open choices for Andrew: inside the ROM folder (travels with the ROMs) vs a folder he picks; copy (default) vs move; whether per-game settings keyed the same way (core choice, screen size, Pixel Transparency etc. in Config/*.ini) come along too.
- **Next:** Andrew installs fork.37 (also in `E:\Projects\_SLOT-DROPIN\_apks`) and works through its `What's new.txt`. Drop-in playtest folder: `E:\Projects\_SLOT-DROPIN`, rebuilt by `_NEEDS-PLAYTEST\make_slot_dropin.py --apply`.
- **No upstream PRs (Andrew, 2026-11-23):** the fork no longer lines up with upstream. Work goes straight onto `build/test-apk-v2.2.2`, the fork's main line; the `pr/*` branches are history.
- **Releases (from run 25):** every push to the build branch publishes GitHub release `v2.2.2-fork.N` (N = CI run number; versionCode 357000 + N) with `slot-for-android-2.2.2-fork.N-arm64.apk` + `SHA256SUMS.txt`, and notes listing the commits since the last fork release. The in-app updater (`SlotUpdater.kt`) reads only `andrewjose/slot-for-kpa` and offers each newer run. The CI step refuses an APK containing `assets/bios/`. The personal APK (BIOS added, same test key, same version) is built from that release asset and never published. BIOS seeded once stay in app storage across OTA updates. Commits that don't change the app use `[skip ci]`. Runs 19-23 still point at upstream, so the first move onto the fork channel is installing run 25 by hand.
- **Logs:** `adb logcat -c`, launch, `adb logcat -d > E:\Projects\slot-for-kpa\slot-log.txt`; failures read `SlotKONKR: Couldn't start <core>. (<reason>)`.
- **Parked:** CRT beam simulator; disc cheats; narrow-screen logo strips.
- **Upstream 3.0 (checked 2026-11-23):** Andrew is happy with the fork as a personal build; no rebase planned. v2.2.2 -> v3.0.0 is 151 commits / ~42k lines: six new systems (SMS, GG, NGP, WonderSwan, Lynx, Pokemon mini), RomM saves/states sync, slot. folder sync, themes/UI redesign, touch, RG Rotate, crt-royale, GBA Video, faster cold start. No PS1/TG-CD or any disc support upstream. 67 of our 99 changed files also changed upstream (runtime.rs ~4.5k lines, MainActivity ~1.7k, settings.rs ~1.4k), the Platform enum grew by six, and 3.0 re-keys labels/saves/states by system + file name (`library::game_key`), which our disc links, label markers and saves-with-ROMs rely on. A rebase = re-porting each pr/* branch onto 3.0, starting with pr/tgcd; days of work, and everything confirmed on the device would need retesting.

## 1. Layout
| Where | What |
|---|---|
| GitHub `andrewjose/slot-for-kpa` | Fork of slot. v2.2.2 |
| `E:\Projects\slot-for-kpa\` | Test builds: the newest at top level, older ones in `archived\` |
| `E:\Projects\slot-for-kpa\HANDOFF.md` | This file |
| `E:\Projects\slot-for-kpa\test ROM set\` | 71 test games across gb/gbc/gba/nes/snes/genesis/psx/tg-cd (~5.1 GB), each picked for a core or feature; README.md is the checklist. Rebuilt by `tooling\make_test_set.py` (also `tools/fork-dev/` in the repo) |
| `E:\Projects\slot-for-kpa\tooling\` | Personal-APK, shader-check, label-guess and kotlin-typecheck tools, plus a README |
| `E:\Projects\slot-for-kpa\tooling\docs\` | `overview.md` (plain language), `engineering.md` (for software engineers), `technical.md` (deep internals) |
| repo `tools/fork-dev/` (build branch) | Same tooling and docs, without the BIOS |
| `F:\Curated ROMS\bios` | BIOS bundled into the personal APK. They are never committed to git (`assets/bios/` is gitignored) |

## 2. Branches (origin)
| Branch | Head | Content |
|---|---|---|
| `pr/tgcd` | 05eaba8 | TG-CD core, PSX/TG save and ROM folders, BIOS import, Watomsk PS1/TG/PCE overlays |
| `pr/smart-labels` | dcaab6e | Every cart and disc gets a label: hacks borrow their game's label; PS1 is matched by serial |
| `pr/multi-disc` | bc415ff | One cart per multi-disc game, Change Disc, one shared memory card |
| `pr/bfi` | 9133ae8 | Black Frame Insertion, per system, with brightness settings. Stacked on `pr/multi-disc` |
| `pr/rom-folder-saves` | 5004f70 | Saves and states with the ROMs by default. Based on `pr/tgcd` |
| `pr/disc-real-path` | 65348d1 | Cores open the real disc path (All files access). Based on `pr/multi-disc` |
| `pr/ayaneo-builtin` | e298898 | AYANEO controller = built-in. Based on `pr/tgcd` |
| `pr/jewel-case` | 844c2a8 | CD jewel-case mould for PS1/TG-CD. Based on `pr/tgcd` |
| `pr/keyboard-repeat` | — | Held delete repeats on the Search keyboard. Based on `pr/tgcd` |
| `pr/cue-iso` | — | BIN/CUE and ISO discs. Based on `pr/disc-real-path` |
| `pr/borrowed-label-title` | 13d37c3 | File name band on borrowed labels. Based on `pr/smart-labels` |
| `pr/label-name-toggle` | see git | Cart Studio File name row, X in the SELECT menu. Based on `pr/borrowed-label-title` |
| `pr/crt-interlace` | ae029c5 | CRT shaders use 240-line pitch for 480-line PS1 modes. Based on `pr/tgcd` |
| `pr/oled-black` | 5a05b95 | OLED Black wallpaper. Based on `pr/tgcd` |
| `pr/motion-control` | 673fd89 | Tilt/gyro games and Sensitivity (upstream v2.3.0). Based on `pr/tgcd` |
| `pr/faster-start` | see git | Start pictures off the GL thread, cached on disk (upstream 3.0). Based on `pr/tgcd` |
| `build/test-apk-v2.2.2` | see git | The fork's main line: everything above, CI, the test key and the release workflow. Work is committed here directly now |

## 3. Test runs
| Run | Content | Where |
|---|---|---|
| 9–11 | Overlays, labels, multi-disc, BIOS | `archived\` |
| 12 | Run 11 plus the CRT 480-line fix and BFI. Crashed on the shelf (label regex) | `archived\` |
| 13 | Cancelled (superseded by 14) | — |
| 14 | Crash fix, disc loading attempt (didn't fix it), AYANEO controls, saves with the ROMs | `archived\` |
| 15 | Run 14 plus the jewel case | `archived\` |
| 16 | Run 15 plus the disc start fix (digest-named link to the real file). PS1/TG-CD confirmed | `archived\` |
| 17-18 | Intermediate CI builds (18 failed a stale test) | — |
| 19 | Keyboard delete repeat, BIN/CUE + ISO, filename band on borrowed labels | `archived\` |
| 20 | Run 19 plus the Cart Studio File name toggle | `archived\` |
| 21 | Cancelled (superseded by 22) | — |
| 22 | Run 20 plus OLED Black, motion control, faster cold start (first CI try hit a Maven 429; re-run passed) | `archived\` |
| 23 | Run 22 plus X in the SELECT menu toggling the file name band, and the face cache fix | `archived\` |
| 24-25 | 24 cancelled (tooling commit); 25 failed reading the version | — |
| 26 | First fork release `v2.2.2-fork.26`: OTA from the fork | `archived\` |
| 27 | Queue items 2, 10, 11, 12, 14 plus PS1 pad/cards, curvature, bands, NES/SNES sizes (public release, no personal build) | — |
| 28 | Failed a Kotlin test (CSO) | — |
| 29 | fork.27 plus the PSP launcher shelf | `archived\` |
| 30 | fork.29 plus L1/R1 Rewind / Fast Forward (L2/R2 screen size) | `archived\` |
| 31 | Kotlin compile error (Context in the poll loop) | — |
| 32 | AJ EDITION: portrait start, fresh-install defaults + personal wallpaper, Emulation bar fix, L1/R1 Rewind/FF default, boot screen tag | `archived\` |
| 33-34 | Kotlin test failures (artwork lambda argument, bracketed title) | — |
| 35 | fork.32 plus Japanese box art + title bar for translations | `archived\` |
| 36 | fork.35 plus Library > Redo All Labels (keep picks or not) | Top level |

## 4. Still to verify on a device
- Run 12 installs over run 11, and the BIOS show up in slot.'s BIOS folder.
- PS1 and TG-CD discs get covers. Cart Studio can switch a wrong guess off, or Search game… can replace it.
- A multi-disc set appears as one cart.
  - Change Disc reaches disc 2.
  - The save carries over between discs.
  - An old per-disc save is adopted.
- A CRT shader on a 480-line PS1 screen (menus, FMV) looks the same as at 240 lines.
- Run 22: OLED Black; WarioWare: Twisted! / Yoshi Topsy-Turvy / Kirby Tilt 'n' Tumble respond to motion, and Sensitivity changes it; the second cold start is faster (`start pictures` in logcat).
- BFI on a 120 Hz screen (RP Nova, if available).
  - The menu entry appears.
  - Motion is sharper.
  - The boost, dark-level and frames settings work.
  - It stays hidden at 60 Hz (KONKR).

## 5. Verified so far (off-device)
- The label matcher picks the right game for about 65 of Andrew's real filenames.
- Product codes are read from real CHDs (SimCity 2000, SLUS-00113; SOTN QoL, SLUS-00067).
- All 26 shader modes compile under GLES2, and the CRT renders at 240 and 480 lines match.
- 444 Rust runtime tests pass (run 22 build), plus the Kotlin unit tests.
- CI runs 9–12 are green, and the personal APK passes v2 signature verification.

## 6. Conventions
- Commits are authored as `andrewjose` with his GitHub no-reply email.
- Features go on `pr/*` branches. Only plumbing goes on the build branch.
- Builds are named `slot v<ver> (<code>) test run N - <features>`. The previous run moves to `archived\`.
- The personal APK and the BIOS are never published.
