# Architecture and desktop strategy

## 1. What upstream actually is

Kotatsu-Redo is a **single-module Android application** (`app/`, ~1150 Kotlin files, Groovy
`build.gradle`, AGP 8.13, Compose + View system + ViewBinding, Hilt, Room, WorkManager, Coil, OkHttp,
Material Components). There is no shared Kotlin Multiplatform module and no desktop target.

Dependency audit of the parts that matter for a reader (from `app/build.gradle` and
`gradle/libs.versions.toml`):

| Area | Android-only pieces | Reusable on the JVM |
| --- | --- | --- |
| UI | `androidx.compose.*`, `appcompat`, fragments, RecyclerView, ViewPager2, Material, `subsampling-scale-image-view` | nothing directly (all `android.*`) |
| Data | Room + KSP, `androidx.collection`, `DocumentFile`, `SharedPreferences`, WorkManager | entity/column **design** and the backup JSON format |
| Sources | `com.github.clquwu:kotatsu-parsers-redo` — parsers built on Android APIs (`android.util`, `AppCompat` WebView for CloudFlare, `okhttp` Android TLS) | the parser *interfaces* (`Manga`, `MangaChapter`, `MangaSource`) conceptually |
| Images | Coil 3 with Android decoders (WebP/AVIF/JXL via native libs) | JPEG/PNG/GIF/BMP via ImageIO |
| Security/OS | biometrics, SAF, widgets, Discord RPC, Sentry Android, `conscrypt-android` | nothing |

So the honest diagnosis is: **no fragment of the Android UI runs as a desktop app**, and the
business logic is not separated into Kotlin/JVM modules — it is interleaved with `android.*` types
(flow of Room DAOs, `Uri`-based page fetching, `Context`-based preferences). A "just run it on the
desktop" approach would need an Android compatibility layer (AndroidX desktop/Compose Multiplatform
plus a re-implementation of SAF, Room, WorkManager…), which is not feasible offline and would still
leave the source parsers unusable.

## 2. Strategy chosen

**A dedicated Kotlin/JVM desktop module (`desktop/`) that reuses upstream's *design* and its
on-disk contract, not its Android code.**

Concretely:

1. **UI: Swing (JDK), Material-3-inspired palette.** Zero dependencies, works with the JDK alone,
   renders in a normal Windows window, and can be packaged by `jpackage`. Compose Multiplatform would
   give a closer look, but requires large Maven artifacts and a Compose compiler plugin; the offline
   build environment cannot resolve them, and the port would not be testable here. The UI is therefore
   an explicit re-implementation (grid, details, reader, settings, history, downloads).
2. **Business logic ported where the source is portable.** `ReadingProgress` (progress maths,
   thresholds, resume position) is ported verbatim from
   `app/.../list/domain/ReadingProgress.kt` and `ReaderViewModel.computePercent`, including the
   "current page counts as read" rule, so percentages match the phone. Reader modes keep upstream's
   numeric ids (`STANDARD=1, WEBTOON=2, REVERSED=3, VERTICAL=4`).
3. **Storage: JSON documents with the upstream column names** (`library.json`, `activity.json`,
   `settings.json`) instead of Room/SQLite. Reasons: no JDBC driver or native SQLite binary needed,
   human-inspectable and recoverable, atomic writes, and the mapping to the Android backup format is
   trivial because the field names already match (`manga_id`, `alt_title`, `nsfw`, `percent`, …).
   The store classes are the only place that knows about persistence, so swapping in SQLite later
   means re-implementing four small classes.
4. **Content: the local source only** (`source = "LOCAL"`). CBZ/ZIP and folders of images are read
   with `java.util.zip`/`java.nio`, pages decoded with `ImageIO`. Offline copies are plain CBZ files
   under the data directory, the same artefact the Android app produces.
5. **Interoperability with Android through the backup file.** `desktop/.../data/backup/` writes and
   reads the exact ZIP-of-JSON-arrays format of `BackupRepository`, so a phone backup can be restored
   and an exported backup can be taken back to the phone.

### Limitations of this choice (stated plainly)

* Not a Material 3 UI; it is an honest, functional desktop layout.
* Online sources, migrations between sources, sync and tracking are **not** available: they depend on
  the parsers library and on Android runtime services. Titles imported from those sources are kept as
  metadata (so a backup round-trips), and the UI says they cannot be opened.
* WebP/AVIF/JXL pages are not decoded (no native decoder); a message is shown instead of a blank page.
* Two sources of truth for Android-side state: the desktop stores only what it can interpret, and
  unknown backup sections are preserved verbatim in `settings.json` rather than dropped.

## 3. Module layout

```
desktop/src/main/kotlin/org/koitharu/kotatsu/desktop/
├── Main.kt                  entry point, CLI (--version/--help/--import/--smoke-test/--data-dir)
├── SmokeTest.kt             headless end-to-end check used by CI on the packaged .exe
├── core/
│   ├── AppDirs.kt           %APPDATA%/%LOCALAPPDATA% (or portable / KOTATSU_LUNAR_HOME)
│   ├── AtomicFiles.kt       atomic text/binary writes
│   ├── BuildInfo.kt         version + upstream commit
│   ├── Log.kt               log file + in-memory tail
│   ├── json/Json.kt         dependency-free JSON parser/writer (kotlinx-like model)
│   ├── i18n/                AppLanguage, I18n, K (keys), Strings (en + pt-BR)
│   └── util/Format.kt       natural ordering, sizes, dates, chapter numbers
├── data/
│   ├── AppRepository.kt     façade used by the UI (library, history, progress, covers…)
│   ├── model/Models.kt      entities mirroring the Android DB + JSON mapping
│   ├── model/Ids.kt         deterministic FNV-1a ids for local content
│   ├── store/               PersistentStore (debounced atomic JSON), Settings/Library/Activity
│   ├── local/               ArchiveReader, ChapterContent, ImageCache, LocalImport
│   ├── downloads/           DownloadService (offline CBZ)
│   └── backup/              BackupSection, BackupService (Android-compatible)
├── reader/ReadingProgress.kt  ported upstream progress logic
└── ui/                      MainWindow (menu, sidebar, drop target), library, details, history,
                             downloads, settings, dialogs, reader (paged + continuous canvases)
```

## 4. Threading and interactivity

Swing's EDT stays responsive: imports, chapter loading, cover generation and page decoding run on a
small worker pool (`ui/UiUtils.Async`) and results are delivered back to the EDT. Decoded pages live
in an LRU (`PageImageCache`, 12 entries) with background preloading of the next two pages. Progress
writes are debounced (250 ms) and flushed on close.

## 5. Reader implementation notes

* **Paged modes** (`STANDARD`, `REVERSED`) paint the current page on a `JPanel` canvas with a fit
  factor; zoom multiplies it; when the scaled page exceeds the viewport the canvas becomes pannable
  by dragging and the page stays clamped inside the window. Clicking the left/right third of the
  window pages backwards/forwards (mirrored in RTL); the middle toggles the toolbar.
* **Continuous modes** (`VERTICAL`, `WEBTOON`) paint all pages on one tall canvas with a placeholder
  height for pages still decoding, so scrolling never jumps; the visible page is derived from the
  scroll offset and drives progress saving.
* Progress is computed exactly as upstream: `percent = (chapterIndex + (page + 1) / pages) / chapters`,
  clamped to `[0, 1]`, with `-1` meaning "not started".

## 6. Why the Android project is kept

The upstream tree is kept intact (`app/`, `gradle/`, `metadata/`, root build files) because the GPL
requires the corresponding source of derived work to be available, and because keeping it preserves
provenance and lets the Android build keep working exactly as before. Nothing in `desktop/` is
compiled into the Android APK and `app/` is not modified by the port.
