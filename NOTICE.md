# NOTICE — credits, origin and modifications

## What this repository is

**Kotatsu Lunar** is an **unofficial desktop (Windows) port** of
[Kotatsu-Redo](https://github.com/Kotatsu-Redo/Kotatsu-Redo), a free and open-source manga reader for
Android.

It is **not** an official Kotatsu or Kotatsu-Redo product. It is not affiliated with, endorsed by,
or supported by the Kotatsu-Redo maintainers. Bug reports about this port belong here, not upstream.

## Upstream project

| Item | Value |
| --- | --- |
| Project | Kotatsu-Redo |
| Repository | https://github.com/Kotatsu-Redo/Kotatsu-Redo |
| Commit this port started from | `d45b7c13de260b6c78de78ccbf968daf2187f1cf` (2026-10-16) |
| Version at that commit | 9.8.5 (versionCode 2047) |
| Licence | GNU General Public License v3.0 (see `LICENSE`, copied verbatim from upstream) |
| Original author / maintainers | KotatsuApp / Koitharu and the Kotatsu-Redo contributors |

The complete upstream Android source tree is kept in this repository under `app/`, `gradle/`,
`metadata/`, `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`,
`CONTRIBUTING.md`, `.editorconfig` and `.weblate`, together with `LICENSE`
(`app/` is byte-for-byte upstream at the commit above; the desktop port does not modify it).
The upstream README is preserved in [`docs/UPSTREAM_README.md`](docs/UPSTREAM_README.md).

## Licence

This repository, including the desktop port, is distributed under the **GNU General Public License
version 3.0 only** (GPL-3.0), the same licence as upstream. See [`LICENSE`](LICENSE). Any
modification or redistribution must remain under the GPL and must keep the credits in this file.

Third-party components used by the **desktop** build (all of them part of, or required by, the
Kotlin/JVM toolchain and not bundled as separate libraries):

| Component | Version | Licence |
| --- | --- | --- |
| Kotlin standard library (`kotlin-stdlib.jar`, merged into the produced jar) | 2.1.21 | Apache License 2.0 |
| Java SE / OpenJDK runtime image produced by `jpackage` | 17+ | GPL-2.0 with the Classpath Exception |

The Android application under `app/` keeps its own third-party dependency list, unchanged from
upstream (see `app/build.gradle` and `gradle/libs.versions.toml`).

## Modifications made by this port (GPL §5)

New files are marked with a header comment stating whether they are new or ported from upstream.
The list, kept up to date:

### Added

* `desktop/` — a new Kotlin/JVM desktop application (Swing UI, local storage, CBZ reader, backup
  import/export, unit tests). See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
* `desktop/src/main/kotlin/.../reader/ReadingProgress.kt` — progress maths ported from
  `app/src/main/kotlin/org/koitharu/kotatsu/list/domain/ReadingProgress.kt` and
  `ReaderViewModel.computePercent`.
* `desktop/src/main/kotlin/.../data/backup/` — the backup ZIP/JSON format ported from
  `app/src/main/kotlin/org/koitharu/kotatsu/backups/` (`BackupSection`, `BackupRepository`), with
  entry names and column names kept identical for interoperability.
* `desktop/src/main/kotlin/.../data/model/Models.kt` — entity definitions mirroring
  `org.koitharu.kotatsu.core.db.entity`, `HistoryEntity`, `FavouriteEntity`,
  `FavouriteCategoryEntity` and `BookmarkEntity`.
* `scripts/build.sh`, `scripts/build-windows.ps1`, `scripts/make_jar.py` — dependency-free builds
  (CI uses these so the Windows `.exe` is built and smoke-tested on a real Windows runner).
* `desktop/build.gradle.kts`, `desktop/settings.gradle.kts` — standalone Gradle build for the
  desktop module (`jpackage` tasks included).
* `desktop/resources/icon.ico`, `desktop/resources/icon.png`, `desktop/tools/make_icon.py` — an
  original icon for this port (not the upstream launcher icon).
* `.github/workflows/build-windows.yml` — Windows CI that compiles, tests, packages and verifies the
  produced `.exe`.
* `docs/` — architecture, feature matrix, build guide, user guide and backup format notes.
* `README.md` — rewritten for this port (the upstream version is kept in `docs/UPSTREAM_README.md`).

### Removed (repository housekeeping only)

* `.github/workflows/new-commit-discord.yml`, `new-issue-discord.yml`, `new-release-discord.yml` —
  upstream Discord notification workflows tied to the upstream project's secrets; they cannot work in
  this repository and would fail on every push. No application code was affected.
* `.idea/` — IDE project files of the upstream maintainers.

### Not modified

`app/` (the Android application), `gradle/`, `metadata/`, `LICENSE`, `CONTRIBUTING.md`,
`build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew*`, `.editorconfig`, `.weblate`.

## Content notice (kept from upstream)

The developers of this application have no affiliation with the content available in the app and do
not store or distribute any content. This application should be considered a viewer for files the
user already has: the desktop build reads only archives and folders on the user's own disk. No
copyrighted material is included in this repository or in the produced packages, and no DRM or
third-party protection mechanism is bypassed.

## Trademarks

"Kotatsu" and "Kotatsu-Redo" refer to the upstream project. They are used here only to describe the
origin of the code, as required by the GPL and by basic honesty about provenance.
