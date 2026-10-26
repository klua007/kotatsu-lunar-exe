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

## 2. Windows verification

The workflow `.github/workflows/build-windows.yml` runs on `windows-latest` and

1. compiles with the official Kotlin compiler on Windows,
2. runs the same 78 tests,
3. builds the jar and the `jpackage` app image (`KotatsuLunar.exe`) with a trimmed Java runtime,
4. **executes the packaged `.exe`** with `--version` and `--smoke-test` (headless), failing the build
   if either exits non-zero,
5. uploads the portable zip, the jar and the MSI (when WiX produced it).

That is the authoritative Windows verification; it has to be read from the workflow run of this
repository (Actions → *Build Windows desktop app*). No Windows machine was available inside this
session, therefore the *interactive* GUI on Windows (window rendering, DPI scaling, drag and drop
from Explorer) remains to be confirmed by that run and by a human.

## 3. Known gaps and risks

* The GUI has never been *displayed* in this session (headless container). Every main panel is
  constructed and painted off-screen by `UiTests`, and the reader geometry is unit-tested, but
  nothing was rendered on a real screen: visual defects are possible. The first Windows run should
  check window sizing/DPI, cover grid wrapping, reader zoom/pan and drag and drop from Explorer.
* `jpackage` requires a JDK on the build machine; the offline `scripts/build.sh` path only produces
  the jar because `jpackage` is not in the JRE used here. On Windows (CI) the `.exe` and `.msi` are
  produced by `scripts/build-windows.ps1`.
* The MSI needs the WiX 3.x toolset; if `choco install wixtoolset` fails in CI, the portable zip is
  still produced (the step is `continue-on-error` and the packaging script warns).
* WebP/AVIF/JXL pages are not decoded.
* Online sources, sync and tracking are not implemented (see `FEATURE_MATRIX.md`).
* The Android app itself was not rebuilt in this session (no Android SDK, no Maven access): `app/` is
  untouched upstream code, which keeps building as before.
