# slot-for-kpa — tooling

Everything used to build, check and package Andrew's slot. fork that isn't in
the repository itself. The fork's source is on GitHub (andrewjose/slot-for-kpa);
tools that belong to the code (the Watomsk overlay exporter, the art harvesters)
live in the repo's `tools/` folder.

| Folder | What it does |
|---|---|
| `personal-apk/` | Makes the personal APK with the BIOS packed in (never published): `make-personal-apk.sh`, `add_bios.py`, `v2sign.py` |
| `shader-check/` | Compiles every game-shader mode as GLSL ES 1.00 on Mesa, and renders the CRT shaders on a 240-line and a 480-line picture |
| `label-guess/` | Runs ArtGuess (the label matcher) on a list of filenames against the real DATs and box-art lists |
| `kotlin-typecheck/` | Android stubs so new Kotlin files can be type-checked without the Android SDK |
| `docs/` | Three-level docs: `overview.md`, `engineering.md`, `technical.md` |

## Why some of these exist
The cloud workspace that builds this fork can't reach Google's servers, so there
is no Android SDK there: APKs are built by GitHub Actions on the fork, and these
tools cover what has to happen outside it.

## personal-apk
```
make-personal-apk.sh test-v2.2.2-mac-lab-357-run12 "<folder with only the BIOS to bundle>" personal.apk
```
Downloads the CI build for that run, adds `assets/bios/<name, lowercase>`,
zip-aligns it (`repro-apk zipalign -P 16 4`), signs it with APK Signature Scheme
v2 using the fork's committed test key, and verifies it. The app copies those
files into its BIOS folder on first launch (`BiosLibrary.seedBundled`).

`v2sign.py` is a small v2 signer written here because Google's `apksigner`
couldn't be downloaded. It was checked against an independent verifier
(`apksigtool verify`) and the certificate fingerprint matches the CI builds.
BIOS used so far: `F:\Curated ROMS\bios` (PS1 scph1001/101/5500/5501/5502/7001,
syscard1-3, gb/gbc/gba). `gexpress.pce` is left out (32 KB; slot. only accepts
256 KB System Cards).

## shader-check
Needs Mesa (libEGL) and `pip install PyOpenGL numpy pillow`.
```
EGL_PLATFORM=surfaceless python3 check.py <repo>/crates/slot-gfx/src/shaders.rs
EGL_PLATFORM=surfaceless python3 render.py <repo>/crates/slot-gfx/src/shaders.rs 960 640 testpat.png out.png
```
`check.py` builds GAME_FRAG once per mode (u_mode as a constant, as
`pipeline.rs` does) and prints OK/FAIL with the compiler's errors. `render.py`
draws each CRT shader on a 320x240 picture and the same picture doubled to
640x480, side by side: after the 480-line fix the two columns match.

## label-guess
```
kotlinc <repo>/android/app/src/main/java/fyi/slot/konkr/ArtGuess.kt Check.kt -include-runtime -d check.jar
java -jar check.jar cases.tsv
```
`Check.kt` reads the DATs and box-art lists from `/tmp` (edit the paths):
libretro-database `metadat/no-intro/*.dat` and `metadat/redump/Sony - PlayStation.dat`
(raw.githubusercontent.com), and the `Named_Boxarts` file lists of
libretro-thumbnails' `Sony_-_PlayStation` and `NEC_-_PC_Engine_CD_-_TurboGrafx-CD`
repos (`git clone --filter=blob:none --no-checkout`, then `git ls-tree -r HEAD`).
`cases.tsv` is `system<TAB>filename<TAB>optional GBA code`, taken from Andrew's
F:\Curated ROMS folders.

## kotlin-typecheck
kotlinc 2.0.21 (JetBrains release zip). Compile the new files together with
these stubs and the project files they use, e.g.:
```
kotlinc kotlin-typecheck/*.kt <konkr>/{ArtGuess,ArtCatalogs,CartGuess,DiscLabels,DiscId,DiscGroups,DiscSource,RomLibrary,Systems,RomSource,CartLabelPaths,AutoLabelRetryPolicy,OfficialArtMatcher,SlotArtSet,AutoCartLabelDiagnostics}.kt -d /tmp/out
```
Only errors in the project files count; "unresolved" errors about Android APIs
mean a stub is missing. `JUnitStub.kt` + `Run*Test.kt` run the JVM unit tests
(ArtGuessTest, DiscGroupsTest) without Gradle.

## Rust checks that aren't obvious
- The runtime (menus, frame loop) is only compiled on host with its feature:
  `RUSTFLAGS="-L <dir with libEGL.so>" cargo +stable test -p slot-android --features runtime-tests --lib`
  (link `libEGL.so` to `libEGL.so.1` if the dev symlink is missing).
- `cargo test -p slot-android --lib` alone skips the runtime, so it can pass
  while runtime code doesn't compile.
- Overlay previews: `SLOT_OVERLAY_PREVIEW=<dir> cargo test -p slot-android --lib watomsk_preview -- --ignored`
  (panels are listed in `video_mode.rs`'s preview test).
- A real CHD's product code: `SLOT_TEST_CHD="a.chd|b.chd" cargo test -p slot-android --lib disc_serial -- --include-ignored --nocapture`.
