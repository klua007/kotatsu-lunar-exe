/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * Ported from app/src/main/kotlin/org/koitharu/kotatsu/backups/domain/BackupSection.kt
 * Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.data.backup

import java.util.Locale
import java.util.zip.ZipEntry

/** Entry names are byte-for-byte the ones the Android app uses, so backups interoperate. */
enum class BackupSection(val entryName: String) {

	INDEX("index"),
	HISTORY("history"),
	CATEGORIES("categories"),
	FAVOURITES("favourites"),
	SETTINGS("settings"),
	SETTINGS_READER_GRID("reader_grid"),
	BOOKMARKS("bookmarks"),
	SOURCES("sources"),
	SCROBBLING("scrobbling"),
	STATS("statistics"),
	SAVED_FILTERS("saved_filters"),
	IDENTITY("identity"),
	;

	companion object {

		fun of(entry: ZipEntry): BackupSection? = of(entry.name)

		fun of(name: String): BackupSection? {
			val normalized = name.lowercase(Locale.ROOT)
			return entries.firstOrNull { it.entryName == normalized }
		}
	}
}
