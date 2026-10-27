# Backup format and Android migration

The desktop build reads and writes the **same backup format as the Android application**
(`BackupRepository` in `app/src/main/kotlin/org/koitharu/kotatsu/backups/data/BackupRepository.kt`),
so a backup made on the phone can be restored on the PC and vice versa.

## 1. File format

A plain ZIP file. Each entry is named after a section and contains **one JSON array**:

| Entry | Content | Desktop action |
| --- | --- | --- |
| `index` | `[{ "app_id", "app_version", "created_at" }]` | read; reported only |
| `categories` | `CategoryBackup[]` (`category_id`, `title`, `order`, `sort_key`, `track`, `show_in_lib`) | merged (existing categories kept) |
| `favourites` | `FavouriteBackup[]` (`manga_id`, `category_id`, `sort_key`, `pinned`, `created_at`, `manga`) | merged, manga metadata imported |
| `history` | `HistoryBackup[]` (`manga_id`, `chapter_id`, `page`, `scroll`, `percent`, `chapters`, `created_at`, `updated_at`, `manga`) | merged, newest position wins |
| `bookmarks` | `BookmarkBackup[]` (`manga`, `tags`, `bookmarks[]`) | merged (deduplicated by `manga_id` + `page_id`) |
| `settings` | `[{ …flat SharedPreferences map… }]` | recognised keys applied, unknown keys preserved |
| `sources` | `SourceBackup[]` (`source`, `sort_key`, `used_at`, `added_in`, `pinned`, `enabled`) | kept verbatim in `settings.json` |
| `reader_grid`, `statistics`, `scrobbling`, `saved_filters`, `identity` | upstream-only data | **preserved verbatim** as `imported_<section>` in `settings.json` |
| anything else | unknown | counted as skipped, no error |

`manga` objects keep upstream's field names (`id`, `title`, `alt_title`, `url`, `public_url`,
`rating`, `nsfw`, `content_rating`, `cover_url`, `large_cover_url`, `state`, `author`, `source`,
`tags[]`). The desktop reader also accepts the desktop spelling (`manga_id`, `tag_id`) when reading
its own files, so both spellings round-trip.

When exporting, the desktop writes `manga_id` inside `favourites`/`history`/`bookmarks` and `id`
inside the nested `manga` object — exactly what the Android importer reads.

## 2. What is migrated

| Data | Migrated? | How |
| --- | --- | --- |
| Favourites | yes | Toggled state per title, category id, pin and creation date kept. |
| Categories | yes | Created if missing; the `Default` category always exists (`category_id = 0`). |
| History / progress | yes | Page, scroll, percent, timestamps. **A locally newer position is not overwritten.** |
| Bookmarks | yes | Stored and exported; there is no dedicated UI yet. |
| Reader settings | yes | Reading mode, theme, page gap, preload, etc. |
| Unrelated Android preferences | preserved | Copied verbatim into `settings.json` (never interpreted, never lost). |
| Titles from online sources | yes, when the source exists here | MangaDex and OPDS titles can be opened and read. Titles from sources this build cannot run (the Android parser pack) are kept as metadata so the backup round-trips, and the UI says they cannot be opened. |
| Source settings (enabled, pinned, OPDS catalogues) | yes | Restored into `sources.json`; a source that is not available on the PC keeps its flags for a future version. |
| Downloaded chapters (CBZ files) | **no** | They live in the phone's private storage (`/data/…/downloads` or SD card) and are not part of the backup ZIP. Copy the CBZ files manually to `%APPDATA%\KotatsuLunar\downloads\<mangaId>\<chapterId>.cbz` if you need them, or re-download from the source file on the PC. |
| Tracking accounts, sync key, statistics, saved filters | preserved only | Kept verbatim for a future version; nothing is sent anywhere. |
| App password / proxy credentials / incognito flag | **never imported** | Explicitly excluded, matching upstream, which strips them from its own dumps. |

## 3. Safety rules

* **Nothing is deleted.** Import is a merge: new rows are added, missing manga are created,
  existing local values are updated only when they are older.
* A history entry with a newer `updated_at` on the PC wins over the phone's entry.
* Favourites and bookmarks already present are left as they are (re-importing the same file is
  idempotent; the report shows what was actually added).
* A file that is not a ZIP, or a ZIP with no known section, is rejected with an error and **no
  changes** are applied (`BackupService` counts recognised sections before returning).
* Every import ends with a report window listing per-section counts, the number of titles that come
  from unsupported sources, the preserved sections and the reminder that local data was not deleted.
* Fresh data files are written atomically; a damaged JSON file is renamed to `*.corrupt-<timestamp>`
  and reported in the log instead of being deleted.

## 4. Taking a desktop backup back to Android

The exported ZIP contains the sections the Android app knows (`index`, `categories`, `favourites`,
`history`, `bookmarks`, `settings`, `sources`). Restoring it on the phone merges favourites, history
and categories. Notes:

* `app_id` in `index` is `org.koitharu.kotatsu.desktop`; the Android app ignores that field.
* Paths of local titles (`url` = `C:\...`) are meaningless on the phone: they appear in the library
  but cannot be opened there. Only progress and favourites for *online* titles are meaningful in both
  directions.
* Sections the desktop does not produce (`statistics`, `saved_filters`, `identity`) are absent from a
  desktop-created backup; missing sections are simply skipped by both importers.

## 5. Manual export outside the app

`--import` and the GUI are the only supported entry points. The data directory can be copied as a
whole (`library.json`, `activity.json`, `settings.json`) to move a profile between PCs; that is the
desktop equivalent of a full backup and includes the offline copies under `downloads/`.
