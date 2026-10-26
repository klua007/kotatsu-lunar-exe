# Verification log

What was actually executed, where, and what remains unverified. Keep this file honest: everything
below was run, and anything not run is listed in the last section.

## 1. Offline verification environment (Linux container, this session)

The sandbox has network access only to GitHub, PyPI and npm, so no Maven artifact can be resolved
(the Android project's dependencies, Kotlin Gradle plugins, JUnit…). To still compile and run the
desktop module, the toolchain was obtained from allowed hosts:

* JDK: `jdk4py` 21.0.8.2 wheel from PyPI (Temurin 21 JRE, used as `JAVA_HOME`).
* Kotlin compiler: `kotlin-compiler` 2.1.21 npm package (the official compiler distribution).

Commands run:

```bash
KOTLINC_HOME=<kotlin> JAVA_HOME=<jdk> OUT_DIR=/tmp/build-offline bash scripts/build.sh
```

Result:

```
-- compiling 39 source files
78/78 tests passed
-- writing kotatsu-lunar.jar
wrote .../kotatsu-lunar.jar 2154966 bytes
-- smoke test
Kotatsu Lunar 0.1.0
Smoke test: 17 checks, 0 failure(s)
Build OK
```

Additional checks:

| Check | Result |
| --- | --- |
| Test suite (`TestSuiteKt`) | 78/78 passed |
| `java -jar kotatsu-lunar.jar --version` | `Kotatsu Lunar 0.1.0` |
| `java -jar kotatsu-lunar.jar --smoke-test` | 17 checks, 0 failures |
| `--import` on a generated CBZ via CLI | imports 1 title / 1 chapter |
| Backup export → fresh profile import | favourites + history restored |
| Corrupt store file | renamed to `*.corrupt-*`, app starts with defaults (test) |
| Corrupt CBZ | `IOException`, message path exercised (test) |
| UI panels headless paint (library, details, history, downloads, settings) | passed (off-screen rendering) |
| Reader window | **not** executed here: the container is headless (no X server), so `MainWindow`, the reader canvases and the menus were only compiled and code-reviewed, never displayed |

## 2. Windows verification (executed)

The workflow `.github/workflows/build-windows.yml` runs on `windows-latest`. It has been executed on
this repository and **passes**:

| Step | Result |
| --- | --- |
| Compile desktop sources (Kotlin 2.1.21, JDK 17 Temurin) | passed |
| Run test suite (`TestSuiteKt`, 78 checks) | passed |
| Build runnable jar | passed |
| Smoke test the jar (`java -jar … --version`, `--smoke-test`) | passed |
| Package native application (`jpackage` app-image, WiX installed) | passed |
| **Verify the produced `.exe`** (`KotatsuLunar.exe --version` and `--smoke-test`, exit code checked) | passed |
| Build portable `.zip` and `.msi` | passed |
| Upload artifacts | `kotatsu-lunar-windows-x64-portable` (48 MB), `kotatsu-lunar-jar` (1 MB), `kotatsu-lunar-windows-x64-msi` (48 MB) |

So, on Windows: the application **compiles, its tests pass, the packaged `KotatsuLunar.exe` starts,
prints the version and completes the 17-check end-to-end smoke test** (create CBZ → import → read
pages → save progress → offline copy → backup export/import) — all verified by the workflow, which
fails if any of those steps returns a non-zero exit code.

What the workflow cannot check automatically, and therefore still needs a human on a Windows desktop:

* the interactive GUI: window sizing/DPI, cover grid wrapping, double-click behaviour, drag and drop
  from Explorer, mouse panning in the reader, font rendering;
* the `.msi` installation flow (install, Start-menu shortcut, uninstall);
* behaviour with real content in exotic layouts (nested folders, very large archives).

## 3. Known gaps and risks

* The GUI has never been *displayed* in this session (headless container) nor by CI. Every main panel is
  constructed and painted off-screen by `UiTests`, and the reader geometry is unit-tested, but
  nothing was rendered on a real screen: visual defects are possible. The first Windows run should
  check window sizing/DPI, cover grid wrapping, reader zoom/pan and drag and drop from Explorer.
* `jpackage` requires a JDK on the build machine; the offline `scripts/build.sh` path only produces
  the jar because `jpackage` is not in the JRE used here. On Windows (CI) the `.exe` and `.msi` are
  produced from the same sources.
* The MSI needs the WiX 3.x toolset; CI installs it with Chocolatey (`continue-on-error`, so a failure
  only drops the MSI, never the portable package).
* WebP/AVIF/JXL pages are not decoded.
* Online sources, sync and tracking are not implemented (see `FEATURE_MATRIX.md`).
* The Android app itself was not rebuilt in this session (no Android SDK, no Maven access): `app/` is
  untouched upstream code, which keeps building as before.
