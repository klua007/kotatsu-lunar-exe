# Building, testing and packaging

The desktop build needs exactly two tools: a **JDK 17+** (for `javac`-level bytecode target,
`jar` and `jpackage`) and the **Kotlin command line compiler 2.1.x** (or Gradle with network access
to Maven Central). No third-party runtime libraries are used.

## 1. Windows (produces the `.exe`)

```powershell
# 1. tools
#    JDK 17+:  https://adoptium.net/    (jpackage is included)
#    Kotlin:   https://github.com/JetBrains/kotlin/releases  -> kotlin-compiler-2.1.21.zip
$env:JAVA_HOME    = "C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot"
$env:KOTLINC_HOME = "C:\tools\kotlin-compiler-2.1.21"

# 2. build
pwsh scripts/build-windows.ps1                 # add -SkipTests to skip the suite
```

Result:

| Artifact | Path | What it is |
| --- | --- | --- |
| jar | `build\dist\kotatsu-lunar.jar` | runnable with `java -jar` |
| app image | `build\package\KotatsuLunar\KotatsuLunar.exe` | native launcher + bundled runtime |
| portable zip | `build\dist\KotatsuLunar-0.1.0-windows-x64-portable.zip` | unzip and run, no install |
| MSI | `build\dist\KotatsuLunar-0.1.0.msi` | installer (needs the WiX toolset) |

The script fails if the jar or the launcher cannot run `--version` and `--smoke-test`, so a broken
package can never be uploaded.

### MSI notes

`jpackage --type msi` requires the WiX toolset 3.x (`choco install wixtoolset`). Without it the script
prints a warning and skips the installer; the portable zip is unaffected. The CI job installs WiX and
uploads the MSI when it is produced.

### Signing (optional)

The produced `.exe`/`.msi` are unsigned, so Windows SmartScreen will warn on first run. To sign:

```powershell
signtool sign /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 /a "build\package\KotatsuLunar\KotatsuLunar.exe"
```

## 2. Linux / macOS (verification build)

```bash
KOTLINC_HOME=/opt/kotlin JAVA_HOME=/usr/lib/jvm/temurin-17 scripts/build.sh
# or: SKIP_TESTS=1 scripts/build.sh
```

This is the pipeline that was used to verify the port in a Linux container without network access to
Maven; it produces the same jar (the app-image/MSI steps need a Windows JDK and are skipped).

## 3. Gradle

```bash
cd desktop
gradle desktopTests      # test suite
gradle jar               # build/libs/kotatsu-lunar.jar
gradle packageImage      # build/package/KotatsuLunar/KotatsuLunar.exe  (Windows)
gradle packageMsi        # build/package/*.msi                        (Windows + WiX)
gradle packagePortable   # build/dist/KotatsuLunar-<v>-windows-x64-portable.zip
```

The Gradle build only needs the Kotlin JVM plugin (it downloads the Kotlin compiler from Maven
Central once). It is a convenience path; CI uses the scripts above so that the build does not depend
on plugin resolution.

## 4. Tests

The suite is a plain Kotlin program (no JUnit), 78 checks:

```bash
java -Dkotatsu.test=1 -Djava.awt.headless=true \
     -cp build/classes/main:build/classes/test:/path/to/kotlin-stdlib.jar \
     org.koitharu.kotatsu.desktop.test.TestSuiteKt
```

Coverage:

| Suite | What it protects |
| --- | --- |
| JSON | parser/writer round-trip, unicode, big ids, `NaN`/`Infinity` (upstream writes them), malformed input |
| Natural ordering | `2` before `10`, zero-padded names, chapter numbers |
| Reading progress | the ported upstream maths: page-as-read rule, 1.0 threshold, resume position, scroll mapping |
| Models and ids | deterministic ids, JSON round-trip with upstream column names, reader-mode ids |
| Translations | every key has English **and** pt-BR, placeholder counts match, language switching |
| CBZ/ZIP | filtering of `__MACOSX`/`Thumbs.db`, natural order, corrupt archive handling |
| Local import | single CBZ, folder of images, library folder, series folder, flat archive folder, re-import identity |
| Offline downloads | archive copy, folder→CBZ packing, fallback when the source disappears, reconcile |
| Stores | persistence, defaults on corruption (file kept as `.corrupt-*`), merge rules |
| Backup | section list, JSON array wrapping, round-trip, **Android-shaped fixture**, idempotency, merge safety |
| Reader geometry | fit width/height, zoom clamping, pan clamping, continuous scale |
| UI panels | every main panel is constructed and painted off-screen (headless) without errors |

If you add a feature, add a case to the matching suite; `desktopTests` is part of `check`.

## 5. Manual smoke test (what to check on a real Windows box)

```powershell
.\KotatsuLunar.exe --version
.\KotatsuLunar.exe --smoke-test        # 17 checks, exit code 0
.\KotatsuLunar.exe --data-dir
.\KotatsuLunar.exe --import "C:\manga\One Piece\"
```

Then, in the GUI: import a CBZ, open the details screen, read a chapter, switch all four modes,
zoom, press `T`/`N`/`S`, close and reopen to confirm the position was kept, toggle a favourite, check
History and Downloads, export a backup, and restore it into a second profile with
`KOTATSU_LUNAR_HOME` pointing elsewhere.

## 6. CI

`.github/workflows/build-windows.yml` runs on `windows-latest` for every push, pull request and tag:

1. JDK 17 (Temurin) + WiX toolset;
2. Kotlin compiler 2.1.21 downloaded from the official release;
3. `scripts/build-windows.ps1` → compile, test suite, jar, app image, portable zip, MSI;
4. verifies the packaged `KotatsuLunar.exe` with `--version` and `--smoke-test`;
5. uploads the portable zip, the jar and the MSI as artifacts (and to the release for `v*` tags).
