# slot. for KONKR (andrewjose/slot-for-kpa) — HANDOFF / status

_Last updated 2026-11-22._

## 0. Resume here
- **Current build:** `slot v2.2.2 (357) test run 12 - CRT 480-line fix, Black Frame Insertion (personal)`.
  - It includes the BIOS and is personal only: never upload it.
  - It installs over run 11 as `fyi.slot.konkr.diag`.
- **Next steps:**
  1. Andrew playtests run 12 (checklist in section 4).
  2. Fix whatever turns up, on the `pr/*` branch, then merge into the build branch.
  3. Prepare the upstream PRs. Andrew opens them himself; PR text comes from the `upstream-pr-prep` skill.
- **Parked:**
  - CRT beam simulator (Andrew: "leave for now").
  - Cheats for disc systems.
  - Logo strips for narrow screens.
- **Earlier open item:** the author-fix force-push of the older PR branches and their re-port. This is still in the tracker. Check it before opening PRs.

## 1. Layout
| Where | What |
|---|---|
| GitHub `andrewjose/slot-for-kpa` | Fork of slot. v2.2.2 |
| `E:\Projects\slot-for-kpa\` | Test builds: the newest at top level, older ones in `archived\` |
| `E:\Projects\slot-for-kpa\HANDOFF.md` | This file |
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
| `pr/crt-interlace` | ae029c5 | CRT shaders use 240-line pitch for 480-line PS1 modes. Based on `pr/tgcd` |
| `build/test-apk-v2.2.2` | see git | Merges all of the above, plus CI and the test key. Never open a PR from this branch |

## 3. Test runs
| Run | Content | Where |
|---|---|---|
| 9–11 | Overlays, labels, multi-disc, BIOS | `archived\` |
| 12 | Run 11 plus the CRT 480-line fix and BFI (off by default, toggled per system) | Top level, with `What's new.txt` and before/after PNGs |

## 4. Still to verify on a device
- Run 12 installs over run 11, and the BIOS show up in slot.'s BIOS folder.
- PS1 and TG-CD discs get covers. Cart Studio can switch a wrong guess off, or Search game… can replace it.
- A multi-disc set appears as one cart.
  - Change Disc reaches disc 2.
  - The save carries over between discs.
  - An old per-disc save is adopted.
- A CRT shader on a 480-line PS1 screen (menus, FMV) looks the same as at 240 lines.
- BFI on a 120 Hz screen (RP Nova, if available).
  - The menu entry appears.
  - Motion is sharper.
  - The boost, dark-level and frames settings work.
  - It stays hidden at 60 Hz (KONKR).

## 5. Verified so far (off-device)
- The label matcher picks the right game for about 65 of Andrew's real filenames.
- Product codes are read from real CHDs (SimCity 2000, SLUS-00113; SOTN QoL, SLUS-00067).
- All 26 shader modes compile under GLES2, and the CRT renders at 240 and 480 lines match.
- 438 Rust runtime tests pass, plus the Kotlin unit tests.
- CI runs 9–12 are green, and the personal APK passes v2 signature verification.

## 6. Conventions
- Commits are authored as `andrewjose` with his GitHub no-reply email.
- Features go on `pr/*` branches. Only plumbing goes on the build branch.
- Builds are named `slot v<ver> (<code>) test run N - <features>`. The previous run moves to `archived\`.
- The personal APK and the BIOS are never published.
