# Feature matrix — Android (upstream) vs. Windows desktop (this port)

Status values: **implemented** (works, covered by tests), **partial** (works with limits, described),
**pending** (not implemented — the app says so in the UI instead of pretending).

Last updated for `desktop` **0.2.1** (upstream commit `d45b7c1`). See `docs/SOURCES.md` for the
source-by-source breakdown and `docs/VERIFICATION.md` for how each row was checked.

Changes in 0.2.1 (details in [`CHANGELOG.md`](../CHANGELOG.md)): the MangaDex chapter feed stopped
sending `contentRating[]=porn` (the API enumerates `pornographic`, and the wrong value rejected the
*whole* request with HTTP 400 — it broke open details, refresh chapters and add to library, while
browse and title search kept working); `status` is sent as the required `status[]` array; `limit`
and `offset + limit` are clamped to the API bounds; API error messages are surfaced in the UI; and
the theme is installed as a Look and Feel, so the dark palette is no longer overridden by the native
Windows controls.

## 1. Reading flow (priority of this port)

| Android feature | Windows state | Notes |
| --- | --- | --- |
| Open a title and read chapters | implemented | Local archives/folders (`LOCAL`) **and** online chapters from the implemented sources (MangaDex, OPDS). |
| Page navigation | implemented | Arrows, Space, PgUp/PgDn, Home/End, wheel, click zones, drag-pan. |
| Reading modes: standard | implemented | LTR, one page at a time, fit width/height. |
| Reading modes: right-to-left | implemented | `ReaderMode.REVERSED`, ids match upstream. |
| Reading modes: vertical | implemented | Continuous vertical strip, fit width. |
| Reading modes: Webtoon | implemented | Continuous, zero gap, fit width. |
| Zoom | implemented | 50–400 %, Ctrl+wheel, `+`/`-`/`0`, fit width/height. |
| Keyboard controls | implemented | Full list in **Help → Keyboard shortcuts**. |
| Save progress / resume | implemented | Same percent semantics as upstream (`computePercent`). |
| Jump to next/previous chapter | implemented | `N`/`P`, toolbar, end-of-chapter behaviour. |
| Table of contents | implemented | Side panel with double-click. |
| Save a page as image | implemented | Writes the original page bytes. |
| Page rotation / double-page spread | pending | Not implemented. |
| Tap-grid zones, volume-key paging, e-ink mode | pending | Desktop-specific alternative: keyboard + mouse. |
| Reader colour filters / custom brightness | pending | Only background colour is configurable. |

## 2. Content sources

| Android feature | Windows state | Notes |
| --- | --- | --- |
| MangaDex (large catalogue, public API) | implemented | Search by title, **publication status** (`status[]`), adult switch, chapter languages (en + pt-br), tag filter, chapter feed with volume/number/scanlator, page images, covers. `limit <= 100` and `offset + limit <= 10 000` are clamped, API error bodies are shown in the UI. The exact query strings are asserted by `MangaDexContractTests`, and `scripts/check_mangadex.py` re-checks them against the live API (read-only). |
| OPDS 1.2 catalogues (Komga/Kavita/self-hosted) | implemented | User adds a catalogue URL in the Hub; navigation feeds, OpenSearch templates, CBZ/ZIP chapters (EPUB/PDF are skipped). |
| 900+ sources from `kotatsu-parsers-redo` | pending | The parser pack is not bundled: no published JVM artifact, Maven access required to build it, and some parsers need a WebView. Listed in the Hub as unavailable **with the reason**. See `docs/SOURCES.md` §2. |
| Source catalogue, enable/disable, pinning, availability | implemented | `Explore` → sources column; state in `sources.json`; "Check" performs a cheap round trip and stores latency/result. |
| Mirror / alternative base URL | partial | Configurable base URL for MangaDex plus sticky failover between the two defaults; OPDS catalogues are identity-based (add another catalogue instead). |
| Search across sources (title/author/genre, filters) | implemented | Fan-out over the enabled sources; per-source failures are reported separately instead of hiding results. Local library search unchanged. |
| Explore / browse ("popular now") | partial | Browsing uses each source's own ordering (MangaDex: most followed); no editorial recommendations. |
| Alternatives / migrate manga between sources | pending | — |
| Community comments, ratings, source reliability | pending | Out of scope for the desktop MVP. |

## 3. Library, history, favourites

| Android feature | Windows state | Notes |
| --- | --- | --- |
| Library grid with covers | implemented | Local titles: first page of the first chapter. Online titles: the source cover image. Both cached as PNG. |
| Search, sort, filters | implemented | All / favourites / downloaded; newest, oldest, title, progress, last read. |
| Details screen | implemented | Chapter list, progress, tags, author, read/download flags. |
| Favourites + custom categories | partial | Favourites toggle, add/remove; categories are read, imported and exported, but there is no category-management UI yet. |
| Reading history | implemented | One row per title, continue/remove/clear. |
| Bookmarks | partial | Stored, imported and exported (page-level), no dedicated UI. |
| Incognito mode | pending | — |
| Titles with new chapters / updates feed | partial | "Refresh chapters" on a title's details fetches the current chapter list and keeps read/offline flags; there is no automatic background scheduler. |
| Reading statistics | pending | Imported statistics are preserved verbatim, not displayed. |
| Widgets, rich presence, tracking (AniList/MAL/Kitsu/Shikimori) | pending | Android-only concepts. |

## 4. Files and offline

| Android feature | Windows state | Notes |
| --- | --- | --- |
| CBZ / ZIP reading | implemented | `java.util.zip`, `__MACOSX` and `Thumbs.db` filtered, natural page order. |
| Folder-of-images reading | implemented | Pages ordered naturally (`2` before `10`). |
| Offline downloads | implemented | Local: copy/pack into `downloads/<mangaId>/<chapterId>.cbz`. Online: pages are fetched into a CBZ, OPDS archives are stored as-is. Used automatically when the original is gone or the network is down. |
| Download manager (queue, progress, cancel) | partial | Synchronous per chapter; no queue UI. |
| SAF / `content://` URIs, SD cards as Android sees them | pending | Desktop uses normal Windows paths; Android-style content URIs are not resolved. |
| WebP / AVIF / JXL page images | pending | ImageIO decodes JPEG/PNG/GIF/BMP; online pages in another format show "this page uses an image format this build cannot display" (no third-party decoder is bundled). |
| ComicInfo.xml metadata | partial | `Series`, `Title`, `Writer`, `Genre` read when importing a CBZ. |
| Automatic backup to a folder / Telegram | partial | Manual export only (no scheduler, no Telegram). |

## 5. Data, sync, backup

| Android feature | Windows state | Notes |
| --- | --- | --- |
| Import Android backup (`*.zip`) | implemented | Sections: index, categories, favourites, history, bookmarks, settings, sources; unknown sections preserved. Source entries restore enable/pin flags and re-add OPDS catalogues. |
| Export a backup Android can restore | implemented | Same entry names and column names; arrays are JSON-wrapped. |
| Merge without data loss | implemented | Newer local progress wins; nothing is deleted; per-section report. |
| Sync server (self-hosted) | pending | — |
| Multiple profiles | pending | One profile per data directory (`KOTATSU_LUNAR_HOME` / `portable.marker`). |

## 6. Platform and UI

| Android feature | Windows state | Notes |
| --- | --- | --- |
| Material You / Compose screens | partial | Swing UI following the same dark-first palette; not a pixel copy (see `docs/ARCHITECTURE.md`). |
| Tablet/phone layouts | partial | Resizable window, wrapping cover grid, minimum 940×620. |
| 60+ locales | partial | English and Brazilian Portuguese shipped; the upstream resources are untouched in `app/` for the Android build. |
| Themes (light/dark, pure black) | implemented | Dark (near-black) and light, installed as a Look and Feel (`KotatsuMetalTheme`) so every Swing control is painted by the app: buttons, fields, combo boxes, check boxes, lists, tables, menus, tooltips, disabled text and focus rings. Text pairs meet WCAG AA (4.5:1; 3:1 for borders and the progress bar) — asserted by `ThemeTests` for both themes. No dynamic colour from wallpaper. |
| Interface scale / DPI | partial | A 100–200 % setting scales all application fonts live; window resizing and the cover grid follow the screen. High-DPI *icons* are raster-scaled, and no per-monitor re-scale on drag between monitors. |
| App lock (password / biometrics), screenshot protection | pending | Android-only security features. |
| Network tools (proxy, DoH, image proxy, CloudFlare) | pending | Online sources use the JDK HTTP client directly; no proxy UI and no Cloudflare solving (sources needing it are unavailable). |
| Crash reporting (Sentry) | pending | Log file + `--smoke-test` instead; no telemetry is sent. |

## 7. Packaging and delivery

| Requirement | State |
| --- | --- |
| Native windowed application (resizable, mouse + keyboard) | implemented |
| `.exe` executable | implemented — `jpackage` app-image launcher (`KotatsuLunar.exe`) |
| `.exe` installer | partial — `.msi` built by CI when WiX is available (documented; see `docs/BUILD.md`) |
| Bundled runtime (no dev tools for the user) | implemented — the app image contains a trimmed Java runtime |
| No emulator, no APK, no web-only build | satisfied |
| Windows CI workflow producing artifacts | implemented — `.github/workflows/build-windows.yml` |
| Verified on Windows | partial — CI runs the packaged `.exe` (`--version`, `--smoke-test`, plus an assertion that it reports the version declared in `BuildInfo.kt`) on `windows-latest`; see `docs/VERIFICATION.md` for what was and was not exercised locally |
| Version single-sourced | implemented — `BuildInfo.kt` → build scripts, Gradle, `User-Agent`, CI; CI fails if the jar or the `.exe` reports a different version |
