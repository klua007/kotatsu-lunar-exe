# User guide

## Starting the application

Run `KotatsuLunar.exe` (portable build or installed copy) or `java -jar kotatsu-lunar.jar`.
The window is resizable, remembers its size and position, and can be maximised (`F11` inside the
reader).

Command line:

```
KotatsuLunar.exe --version          print the version
KotatsuLunar.exe --help             options
KotatsuLunar.exe --data-dir         print the data directory
KotatsuLunar.exe --import <paths>   import archives/folders without opening a window
KotatsuLunar.exe --smoke-test       run the built-in self test and exit (exit code 0 = success)
```

## Importing content

Use **File → Open CBZ/ZIP…** (`Ctrl+O`), **File → Import folder…**, the buttons on the library
screen, or drag files/folders onto the window. Local files stay on your disk; the application never
uploads anything, ships no content and only connects to the servers of the online sources you enable
(see [Online sources — the Hub](#online-sources--the-hub)).

Recognised layouts:

| Layout | Result |
| --- | --- |
| `Manga.cbz` (or `.zip`) | one title, one chapter named after the file |
| `Folder\` containing images | one title, one chapter |
| `Folder\A\*.png`, `Folder\B\*.png` | one title per sub-folder |
| `Series\Chapter 1\*.png`, `Series\Chapter 2.cbz` | one title, chapters from sub-folders and archives |
| `Folder\One.cbz`, `Folder\Two.cbz` | one title per archive |

A sub-folder is treated as a chapter when its name looks like a chapter (`1`, `07`, `Ch. 12`,
`Chapter 3`, `Vol. 2`, `Capítulo 5`, …). Non-image entries and junk files (`__MACOSX`, `Thumbs.db`,
dot-files) are ignored. When a CBZ contains `ComicInfo.xml`, `Series`, `Title`, `Writer` and `Genre`
are used for the title, author and tags.

Re-importing or refreshing (**Refresh** button, `F5`) re-scans the paths: chapter ids are stable, so
reading progress survives. Files that disappeared are kept in the library but cannot be opened until
they are back or downloaded offline.

## Library screen

* Search box filters by title, alternative titles, author and tags (the **local** library). To search
  online, use **Explore** (`Ctrl+E`).
* Filters: **All / Favourites / Downloaded**. Sorting: date added, title, progress, last read.
* Double-click a cover → details. Right-click → open, continue, favourite, remove.

## Details screen

* Cover, title, author, source, tags, current progress.
* **Continue reading** resumes at the saved chapter/page.
* Chapter list: page count, offline marker (⬇) and read marker (✓). Double-click to read.
* **Download chapter** stores an offline copy; **Delete** removes it; **Mark as read** toggles.
* **Remove** deletes the title from the library (files on disk are untouched).

## Online sources — the Hub

Open **Explore** (`Ctrl+E`, or the **Explore** entry in the sidebar / **View** menu). The screen has
two halves: the sources on the left, the results on the right.

### Sources

Each card shows the source, its language, whether it is enabled and the result of the last check.

| Control | What it does |
| --- | --- |
| **Enabled / Disabled** | includes or excludes the source from searches and from "Popular now" |
| **Pin / Unpin** | keeps the source on top of the list |
| **Check** | makes one cheap request and stores latency or the reason it failed |
| **Alternative base URL** | points MangaDex at another API host (empty = back to the defaults) |
| **Remove** (OPDS only) | deletes a catalogue you added |
| **Add OPDS catalogue…** | registers your own server's catalogue |
| **Check all** | checks every enabled source, one after the other |

Sources this build cannot run are listed in grey **with the reason** (parser pack not bundled,
JavaScript-only site, Cloudflare challenge…). See [`SOURCES.md`](SOURCES.md) for the details.

### Searching and browsing

* Type a term and press **Search** (or `Enter`). **Popular now** lists what each source considers
  current — MangaDex is queried by "most followed".
* **Tags** (comma separated) and **Include adult content** are applied to the sources that support
  them; filters a source cannot honour are not silently claimed.
* Results from every enabled source are merged. If a source fails, the other results are still shown
  and the failure appears in red at the bottom, next to the source name.
* **Load more** asks for the next page of results.
* Click a card to add the title to the library and open its details. Cards already in the library
  read **In library** and just open the stored title.

### Reading online chapters

Online chapters behave like local ones: the reader, progress, modes and history are the same. Pages
are downloaded on demand into the page cache
(`%LOCALAPPDATA%\KotatsuLunar\cache\remote\pages`), so going back is instant, and the **Download**
button in the details screen stores a CBZ in `downloads/` for reading with no network.

Errors are explicit: "no connection", "timed out", "the source refused the request (429, retrying
later)", "title not found", "invalid response from the source". A single page that cannot be loaded
shows a message in the page area instead of an endless spinner.

### Your own OPDS server

**Add OPDS catalogue…** takes a name and a URL. For Komga:

```
http://<host>:25600/opds/v1.2/catalog
```

Kavita, Calibre-Web and most self-hosted comic servers expose an OPDS 1.2 entry point as well. If the
catalogue advertises an OpenSearch template it is used for searches; otherwise the shelf is filtered
locally. Only CBZ/ZIP chapters are offered — EPUB and PDF links are skipped, because this build
cannot render them.

### What to expect from the network

* Requests are sequential per source and never run on the UI thread.
* A 429 answer is reported with its `Retry-After` information instead of being retried in a loop.
* MangaDex has two default API hosts; if one stops answering, the next is used for the rest of the
  session, and **Alternative base URL** overrides both.

## Reader

Controls: toolbar at the top, status bar at the bottom, contents panel on the right.

| Action | Keys / mouse |
| --- | --- |
| Next page | `→` `↓` `Space` `Page Down`, wheel down, right click, click right third |
| Previous page | `←` `↑` `Page Up`, wheel up, click left third |
| First / last page | `Home` / `End` |
| Next / previous chapter | `N` / `P` (or toolbar) |
| Reading mode | `M` or the mode selector |
| Zoom | `+` / `-` / `Ctrl`+wheel, `0` resets, toolbar buttons |
| Fit width / height | `W` / `H` |
| Contents | `T` or toolbar |
| Save current page | `S` |
| Hide toolbar | click the middle of the page |
| Pan a zoomed page | drag with the left mouse button |
| Full screen (reader) | `F11` |
| Exit reader / close contents | `Esc` |

Modes:

* **Standard** — one page at a time, left to right.
* **Right to left** — same, paging mirrored (manga order).
* **Vertical** — continuous column, fitted to width.
* **Webtoon** — continuous, no gap between pages, fitted to width.

Progress is saved automatically (debounced), on chapter change and when the window closes.

## History

Every title you open is listed with the chapter, the percentage and the last-read time. Double-click
or **Continue reading** to resume, **Remove** to delete one entry, **Clear** to erase the list
(favourites and library are kept).

## Downloads

Lists titles with offline chapters, with **Read** and **Delete** per chapter, plus a button that opens
the downloads folder. Offline copies are `.cbz` files, so they can be copied to another machine and
read with any comic reader.

## Backup and migration from Android

* **Create backup…** writes a `.zip` identical in structure to the Android backup.
* **Restore backup…** merges a backup made on the phone.

See [`BACKUP_FORMAT.md`](BACKUP_FORMAT.md) for exactly what is migrated.

## Settings

* Interface language: English or Português (Brasil). Restart to apply everywhere.
* Theme: dark or light, applied immediately — the whole window is repainted, no restart needed.
* Interface scale: 100–200 %, applied immediately to every application font (useful on high-DPI
  screens).
* Reader defaults: reading mode, background (black / gray / white), page gap, preloading, automatic
  progress saving, Ctrl+wheel zoom.
* Storage: shows the data/cache/download folders and the cache size; **Clear cache** removes decoded
  covers and pages (library and progress are untouched).
* Online sources: page-cache size and **Clear page cache** (separate from the image cache).
* Backup buttons, and a note about titles that come from Android sources this build cannot run.
* About: version, upstream commit, licence.

## Troubleshooting

| Symptom | What to do |
| --- | --- |
| A page shows "formato não suportado" | the page is WebP/AVIF/JXL; convert it to JPEG/PNG (see feature matrix). |
| A title shows no pages | the path moved or the archive is damaged; use **Refresh**, or re-import. |
| **Explore** shows "no connection" | the machine is offline or blocked by a firewall; **Check** a source to see the exact failure. |
| An online title will not open | the source may be down (check the card) or the chapter was removed upstream; refresh the chapter list. |
| "The source answered with HTTP error 400" | the response body of the API is shown after the status: read it, it names the parameter the server refused. Check the log (**Help → Open log folder**) for the URL — query values of `token`/`key`/`secret`/`password`/`session`/`cookie` are masked there. |
| Dark-theme buttons are hard to read | fixed in 0.2.1: the theme is installed as a Look and Feel, so the native Windows colours no longer override it. If a control still looks wrong, raise **Interface scale** and report the component. |
| Pages from an online source do not appear | the image host may be blocked; open the log (**Help → Open log folder**) — the failing URL is recorded. |
| Library looks empty after moving the profile | check `%APPDATA%\KotatsuLunar`; the app reads `library.json` there (or in the portable folder). |
| Something crashes | `%APPDATA%\KotatsuLunar\logs\kotatsu.log` has the stack trace; **Help → Open log folder**. |
| Antivirus/SmartScreen warning | the binaries are unsigned; use "More info → Run anyway", or sign them yourself. |
