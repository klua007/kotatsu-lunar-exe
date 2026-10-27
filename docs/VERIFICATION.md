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

### 1.1: 0.2.1 re-run (after the MangaDex and theme fixes)

```
KOTLINC_HOME=<kotlin> JAVA_HOME=<jdk> OUT_DIR=<out> bash scripts/build.sh
```

```
kotlinc : <kotlinc>/bin/kotlinc
version : 0.2.1                      <- read from BuildInfo.kt, not typed into the script
-- compiling 49 source files
-- compiling 11 test files
142/142 tests passed
Kotatsu Lunar 0.2.1
Smoke test: 22 checks, 0 failure(s)
Build OK
wrote <out>/dist/kotatsu-lunar.jar 2409461 bytes
```

### 1.0: the 0.2.0 baseline this session started from

Result:

```
KOTLINC_HOME=<kotlinc> JAVA_HOME=<jdk> OUT_DIR=<out> bash scripts/build.sh
```

```
-- compiling 48 source files
112/112 tests passed
-- writing kotatsu-lunar.jar
wrote .../kotatsu-lunar.jar 2376185 bytes
-- smoke test
Kotatsu Lunar 0.2.0
Smoke test: 22 checks, 0 failure(s)
Build OK
```

Additional checks (142-case suite):

| Check | Result |
| --- | --- |
| Test suite (`TestSuiteKt`) | 142/142 passed |
| `java -jar kotatsu-lunar.jar --version` | `Kotatsu Lunar 0.2.1` |
| `java -jar kotatsu-lunar.jar --smoke-test` | 22 checks, 0 failures |
| MangaDex request contract (`MangaDexContractTests`) | passed — asserts the exact query string for browse, search, status filter, adult content ratings, `limit`/`offset` bounds, error bodies and pagination flow, against a local server |
| Theme (`ThemeTests`) | passed — both palettes meet WCAG AA (4.5:1 text, 3:1 for borders and the progress bar), the palette reaches `UIManager`, the Look and Feel is the application's own, themed controls are legible including disabled text, and the focus ring is painted. Found and fixed one real defect: the progress bar's accent on its track was 2.84:1 (now 4.6:1). |
| Online source layer | exercised end to end against a local `com.sun.net.httpserver` instance inside the suite: search, browse, details, chapter feed, page images, covers, CBZ/archive download, OPDS catalogues, mirror failover, availability checks |
| Failure paths | offline port, timeout, 404, 429 + `Retry-After`, 500, malformed JSON, malformed XML, empty feed — each mapped to a distinct message (tests) |
| Source state | `sources.json` enable/pin/mirror/availability round-trip, OPDS add/remove/dedupe, backup export/import of sources |
| Hub rendering | the Hub is constructed, laid out and painted off-screen (headless) with the real catalogue, then inspected as an image; the same was done for library, details (local and online title), history, downloads and settings |
| Layout defects found by that inspection and fixed | (1) source cards clipped their text and pushed buttons outside the column; (2) the Hub's search bar wrapped into unlabelled fields; (3) **every list row rendered as a coloured band with no text** — custom `JPanel` row renderers are painted by `CellRendererPane` without being validated, so their labels kept zero bounds. `Ui.layoutTree` now lays out the subtree while the row paints, and a regression test asserts that a real history row produces visible labels |
| `--import` on a generated CBZ via CLI | imports 1 title / 1 chapter |
| Backup export → fresh profile import | favourites + history restored |
| Corrupt store file | renamed to `*.corrupt-*`, app starts with defaults (test) |
| Corrupt CBZ | `IOException`, message path exercised (test) |
| UI panels headless paint (library, hub, details, history, downloads, settings) | passed (off-screen rendering) |
| Reader window | **not** executed here: the container is headless (no X server), so `MainWindow`, the reader canvases and the menus were only compiled and code-reviewed, never displayed |

## 2. Windows verification (executed)

The workflow `.github/workflows/build-windows.yml` runs on `windows-latest`. It has been executed on
this repository and **passes**:

| Step | Result |
| --- | --- |
| Resolve application version from `BuildInfo.kt` | `0.2.1` |
| Compile desktop sources (Kotlin 2.1.21, JDK 17 Temurin) | passed |
| Run test suite (`TestSuiteKt`, 142 checks) | passed |
| Build runnable jar | passed |
| Smoke test the jar (`java -jar … --version`, asserted against `BuildInfo.kt`, then `--smoke-test`) | passed |
| Package native application (`jpackage` app-image, WiX installed) | passed |
| **Verify the produced `.exe`** (`KotatsuLunar.exe --version` asserted against `BuildInfo.kt`, and `--smoke-test`, exit code checked) | passed |
| Build portable `.zip` and `.msi` | passed |
| Upload artifacts | `kotatsu-lunar-windows-x64-portable`, `kotatsu-lunar-jar`, `kotatsu-lunar-windows-x64-msi` (all named after the resolved version) |
| `MangaDex read-only check` job (live API, GET only, non-blocking) | passed — 11/11 checks; report uploaded as `mangadex-readonly-check` and commented on the pull request |

So, on Windows: the application **compiles, its tests pass, the packaged `KotatsuLunar.exe` starts,
prints the version and completes the 22-check end-to-end smoke test** (create CBZ → import → read
pages → save progress → offline copy → backup export/import) — all verified by the workflow, which
fails if any of those steps returns a non-zero exit code.

What the workflow cannot check automatically, and therefore still needs a human on a Windows desktop:

* the interactive GUI: window sizing/DPI, cover grid wrapping, double-click behaviour, drag and drop
  from Explorer, mouse panning in the reader, font rendering. The *colours* are now covered by
  `ThemeTests` (contrast ratios computed from the palette, both themes, including disabled text and
  the focus ring), but nothing has looked at a real screen — the first Windows run should confirm the
  dark theme reads as intended;
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
* **The sandbox itself cannot reach MangaDex** (outbound access is limited to GitHub/PyPI/npm, so
  `scripts/check_mangadex.py` reports `TLS/SSL connection has been closed` there). The live, read-only
  verification therefore happened in two places:
  * by issuing the exact URLs the parser builds through an HTTP fetcher that does have access — browse,
    search, `status[]`, `contentRating[]=pornographic`, details, feed and `/at-home/server` all answered
    `result":"ok"`, and the *old* `contentRating[]=porn` and scalar `status=` were rejected with HTTP 400
    (recorded in `docs/SOURCES.md` §3.4);
  * by the CI job `MangaDex read-only check`, which runs the same script on a GitHub runner with real
    internet access and ended **success** (all 11 checks: 8 endpoints + 3 regression guards). Its
    report is attached to the run as the artifact `mangadex-readonly-check` and commented on the pull
    request.
  The OPDS client is still only validated against a local server reproducing the protocol, never against
  a live self-hosted catalogue — an OPDS user is the one who should try that first.
* Sync and tracking are still not implemented, and the ~900 Android parsers are not bundled
  (see `FEATURE_MATRIX.md` and `SOURCES.md`).
* The Hub was painted off-screen and inspected as an image; like every other screen it has never been
  shown on a real display in this session. The reader's network-error path is covered by a test at
  the repository level, but the corresponding canvas was not rendered on screen.
* The Android app itself was not rebuilt in this session (no Android SDK, no Maven access): `app/` is
  untouched upstream code, which keeps building as before.
