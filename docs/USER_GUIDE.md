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
screen, or drag files/folders onto the window. Everything stays on your disk: the application never
uploads, downloads or ships any manga.

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

* Search box filters by title, alternative titles, author and tags (it searches the **local** library;
  online source search does not exist yet).
* Filters: **All / Favourites / Downloaded**. Sorting: date added, title, progress, last read.
* Double-click a cover → details. Right-click → open, continue, favourite, remove.

## Details screen

* Cover, title, author, source, tags, current progress.
* **Continue reading** resumes at the saved chapter/page.
* Chapter list: page count, offline marker (⬇) and read marker (✓). Double-click to read.
* **Download chapter** stores an offline copy; **Delete** removes it; **Mark as read** toggles.
* **Remove** deletes the title from the library (files on disk are untouched).

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
* Theme: dark or light. Restart to repaint every component.
* Reader defaults: reading mode, background (black / gray / white), page gap, preloading, automatic
  progress saving, Ctrl+wheel zoom.
* Storage: shows the data/cache/download folders and the cache size; **Clear cache** removes decoded
  covers and pages (library and progress are untouched).
* Backup buttons, and a note about titles that come from Android online sources.
* About: version, upstream commit, licence.

## Troubleshooting

| Symptom | What to do |
| --- | --- |
| A page shows "formato não suportado" | the page is WebP/AVIF/JXL; convert it to JPEG/PNG (see feature matrix). |
| A title shows no pages | the path moved or the archive is damaged; use **Refresh**, or re-import. |
| Library looks empty after moving the profile | check `%APPDATA%\KotatsuLunar`; the app reads `library.json` there (or in the portable folder). |
| Something crashes | `%APPDATA%\KotatsuLunar\logs\kotatsu.log` has the stack trace; **Help → Open log folder**. |
| Antivirus/SmartScreen warning | the binaries are unsigned; use "More info → Run anyway", or sign them yourself. |
