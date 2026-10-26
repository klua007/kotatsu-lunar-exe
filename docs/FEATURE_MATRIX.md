# Feature matrix — Android (upstream) vs. Windows desktop (this port)

Status values: **implemented** (works, covered by tests), **partial** (works with limits, described),
**pending** (not implemented — the app says so in the UI instead of pretending).

Last updated for `desktop` 0.1.0 (upstream commit `d45b7c1`).

## 1. Reading flow (priority of this port)

| Android feature | Windows state | Notes |
| --- | --- | --- |
| Open a title and read chapters | implemented | Local archives/folders only (`LOCAL` source). |
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
| 900+ online sources (`kotatsu-parsers-redo`) | pending | Those parsers are Android/JVM libraries that depend on Android APIs (`android.util`, WebView, OkHttp+Android); the desktop build has no source layer yet. Titles coming from them are stored as metadata only and say so. |
| Source catalog, pinning, mirror switching | pending | Depends on the source layer. |
| Search across sources (title/author/genre, filters) | partial | Search works over the **local** library (title, alt titles, author, tags). |
| Alternatives / migrate manga between sources | pending | — |
| Explore / suggestions / recommendations | pending | — |
| Community comments, ratings, source reliability | pending | Out of scope for the desktop MVP. |

## 3. Library, history, favourites

| Android feature | Windows state | Notes |
| --- | --- | --- |
| Library grid with covers | implemented | Cover = first page of the first chapter, cached as PNG. |
| Search, sort, filters | implemented | All / favourites / downloaded; newest, oldest, title, progress, last read. |
| Details screen | implemented | Chapter list, progress, tags, author, read/download flags. |
| Favourites + custom categories | partial | Favourites toggle, add/remove; categories are read, imported and exported, but there is no category-management UI yet. |
| Reading history | implemented | One row per title, continue/remove/clear. |
| Bookmarks | partial | Stored, imported and exported (page-level), no dedicated UI. |
| Incognito mode | pending | — |
| Titles with new chapters / updates feed | pending | Requires the source layer. |
| Reading statistics | pending | Imported statistics are preserved verbatim, not displayed. |
| Widgets, rich presence, tracking (AniList/MAL/Kitsu/Shikimori) | pending | Android-only concepts. |

## 4. Files and offline

| Android feature | Windows state | Notes |
| --- | --- | --- |
| CBZ / ZIP reading | implemented | `java.util.zip`, `__MACOSX` and `Thumbs.db` filtered, natural page order. |
| Folder-of-images reading | implemented | Pages ordered naturally (`2` before `10`). |
| Offline downloads | implemented | Copy/pack into `downloads/<mangaId>/<chapterId>.cbz`; used automatically when the source file is gone. |
| Download manager (queue, progress, cancel) | partial | Synchronous per chapter; no queue UI. |
| SAF / `content://` URIs, SD cards as Android sees them | pending | Desktop uses normal Windows paths; Android-style content URIs are not resolved. |
| WebP / AVIF / JXL page images | pending | ImageIO decodes JPEG/PNG/GIF/BMP; a page in another format shows a message. The Android app bundles Coil decoders for those. |
| ComicInfo.xml metadata | partial | `Series`, `Title`, `Writer`, `Genre` read when importing a CBZ. |
| Automatic backup to a folder / Telegram | partial | Manual export only (no scheduler, no Telegram). |

## 5. Data, sync, backup

| Android feature | Windows state | Notes |
| --- | --- | --- |
| Import Android backup (`*.zip`) | implemented | Sections: index, categories, favourites, history, bookmarks, settings, sources; unknown sections preserved. |
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
| Themes (light/dark, pure black) | partial | Dark (near-black) and light; no dynamic colour from wallpaper. |
| App lock (password / biometrics), screenshot protection | pending | Android-only security features. |
| Network tools (proxy, DoH, image proxy, CloudFlare) | pending | Only relevant once online sources exist. |
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
| Verified on Windows | partial — CI runs the packaged `.exe` (`--version`, `--smoke-test`) on `windows-latest`; see `docs/VERIFICATION.md` for what was and was not exercised locally |
