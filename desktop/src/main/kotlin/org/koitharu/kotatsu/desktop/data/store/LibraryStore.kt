/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * The Android app keeps everything in Room/SQLite. The desktop build stores the same entities in
 * two JSON documents instead of adding a SQLite JDBC dependency (see docs/ARCHITECTURE.md):
 *
 *  - `library.json`  — manga and chapters (changes only on import/update)
 *  - `activity.json` — history, favourites, categories and bookmarks (changes constantly)
 *
 * Column names are the upstream ones, which keeps the Android backup mapping trivial and leaves a
 * clear path to a real SQL backend later.
 */
package org.koitharu.kotatsu.desktop.data.store

import org.koitharu.kotatsu.desktop.core.json.JsonValue
import org.koitharu.kotatsu.desktop.core.json.add
import org.koitharu.kotatsu.desktop.core.json.asObj
import org.koitharu.kotatsu.desktop.core.json.intJson
import org.koitharu.kotatsu.desktop.core.json.jsonArray
import org.koitharu.kotatsu.desktop.core.json.jsonObject
import org.koitharu.kotatsu.desktop.data.model.Chapter
import org.koitharu.kotatsu.desktop.data.model.Manga
import org.koitharu.kotatsu.desktop.data.model.MangaWithChapters
import org.koitharu.kotatsu.desktop.data.model.chapterFromJson
import org.koitharu.kotatsu.desktop.data.model.mangaFromJson
import org.koitharu.kotatsu.desktop.data.model.toJson
import java.nio.file.Path

class LibraryStore(file: Path) : PersistentStore(file) {

	private val mangaById = LinkedHashMap<Long, Manga>()
	private val chaptersByManga = HashMap<Long, MutableList<Chapter>>()
	private var lastLoadCount = 0

	override fun onLoad(root: JsonValue?) {
		mangaById.clear()
		chaptersByManga.clear()
		val obj = root?.asObj() ?: return
		obj["manga"].let { array ->
			(array as? JsonValue.Arr)?.items?.forEach { item ->
				mangaFromJson(item)?.let { manga -> mangaById[manga.id] = manga }
			}
		}
		obj["chapters"].asObj()?.fields?.forEach { (key, value) ->
			val mangaId = key.toLongOrNull() ?: return@forEach
			val list = (value as? JsonValue.Arr)?.items?.mapNotNull(::chapterFromJson) ?: return@forEach
			chaptersByManga[mangaId] = list.sortedBy { it.index }.toMutableList()
		}
		lastLoadCount = mangaById.size
	}

	override fun buildDocument(): JsonValue = jsonObject {
		put("schema_version", intJson(SCHEMA_VERSION))
		put("manga", jsonArray { mangaById.values.forEach { add(it.toJson()) } })
		put(
			"chapters",
			jsonObject {
				chaptersByManga.forEach { (mangaId, list) ->
					put(
						mangaId.toString(),
						jsonArray { list.forEach { add(it.toJson()) } },
					)
				}
			},
		)
	}

	val loadedMangaCount: Int get() = lastLoadCount

	fun allManga(): List<Manga> = locked { mangaById.values.toList() }

	fun mangaCount(): Int = locked { mangaById.size }

	fun getManga(id: Long): Manga? = locked { mangaById[id] }

	fun findMangaByUrl(url: String): Manga? = locked { mangaById.values.firstOrNull { it.url == url } }

	fun chaptersOf(mangaId: Long): List<Chapter> = locked {
		chaptersByManga[mangaId]?.sortedBy { it.index } ?: emptyList()
	}

	fun getChapter(chapterId: Long): Chapter? = locked {
		chaptersByManga.values.asSequence().flatten().firstOrNull { it.chapterId == chapterId }
	}

	fun getWithChapters(mangaId: Long): MangaWithChapters? = locked {
		val manga = mangaById[mangaId] ?: return@locked null
		MangaWithChapters(manga, chaptersOf(mangaId))
	}

	fun allChapters(): List<Chapter> = locked { chaptersByManga.values.flatten() }

	fun downloadedChapters(): List<Chapter> = locked { chaptersByManga.values.flatten().filter { it.isDownloaded } }

	fun isKnownUrl(url: String): Boolean = findMangaByUrl(url) != null

	/**
	 * Inserts or replaces a manga together with its chapter list. Existing per-chapter flags that
	 * are not derived from the file (offline copy availability, read flag) are preserved.
	 */
	fun upsert(manga: Manga, chapters: List<Chapter>) = locked {
		val existing = chaptersByManga[manga.id].orEmpty().associateBy { it.chapterId }
		val merged = chapters.sortedBy { it.index }.map { chapter ->
			val previous = existing[chapter.chapterId]
			if (previous == null) {
				chapter
			} else {
				chapter.copy(
					isDownloaded = previous.isDownloaded,
					isRead = previous.isRead,
					uploadDate = if (chapter.uploadDate != 0L) chapter.uploadDate else previous.uploadDate,
				)
			}
		}
		mangaById[manga.id] = manga
		chaptersByManga[manga.id] = merged.toMutableList()
		markDirty()
	}

	fun updateChapter(chapter: Chapter) = locked {
		val list = chaptersByManga[chapter.mangaId] ?: return@locked
		val index = list.indexOfFirst { it.chapterId == chapter.chapterId }
		if (index < 0) return@locked
		list[index] = chapter
		markDirty()
	}

	fun setChapterDownloaded(chapterId: Long, downloaded: Boolean) = locked {
		chaptersByManga.values.forEach { list ->
			val index = list.indexOfFirst { it.chapterId == chapterId }
			if (index >= 0) {
				list[index] = list[index].copy(isDownloaded = downloaded)
				markDirty()
				return@locked
			}
		}
	}

	fun setChapterRead(mangaId: Long, chapterId: Long, read: Boolean) = locked {
		val list = chaptersByManga[mangaId] ?: return@locked
		val index = list.indexOfFirst { it.chapterId == chapterId }
		if (index < 0) return@locked
		list[index] = list[index].copy(isRead = read)
		markDirty()
	}

	fun removeManga(mangaId: Long) = locked {
		mangaById.remove(mangaId)
		chaptersByManga.remove(mangaId)
		markDirty()
	}

	/** Used by the backup importer: insert without touching any existing entry. */
	fun insertIfAbsent(manga: Manga, chapters: List<Chapter>): Boolean = locked {
		if (mangaById.containsKey(manga.id)) return@locked false
		mangaById[manga.id] = manga
		if (chapters.isNotEmpty()) {
			chaptersByManga[manga.id] = chapters.sortedBy { it.index }.toMutableList()
		}
		markDirty()
		return@locked true
	}

	companion object {
		const val SCHEMA_VERSION = 1
	}
}
