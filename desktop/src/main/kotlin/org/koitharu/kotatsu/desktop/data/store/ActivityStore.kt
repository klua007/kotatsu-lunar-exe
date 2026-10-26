/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * History, favourites, categories and bookmarks: the frequently changing half of the local store.
 * Entities and column names follow the Android app (HistoryEntity, FavouriteEntity,
 * FavouriteCategoryEntity, BookmarkEntity).
 */
package org.koitharu.kotatsu.desktop.data.store

import org.koitharu.kotatsu.desktop.core.json.JsonValue
import org.koitharu.kotatsu.desktop.core.json.add
import org.koitharu.kotatsu.desktop.core.json.asObj
import org.koitharu.kotatsu.desktop.core.json.intJson
import org.koitharu.kotatsu.desktop.core.json.jsonArray
import org.koitharu.kotatsu.desktop.core.json.jsonObject
import org.koitharu.kotatsu.desktop.data.model.Bookmark
import org.koitharu.kotatsu.desktop.data.model.Favourite
import org.koitharu.kotatsu.desktop.data.model.FavouriteCategory
import org.koitharu.kotatsu.desktop.data.model.HistoryEntry
import org.koitharu.kotatsu.desktop.data.model.PROGRESS_NONE
import org.koitharu.kotatsu.desktop.data.model.bookmarkFromJson
import org.koitharu.kotatsu.desktop.data.model.categoryFromJson
import org.koitharu.kotatsu.desktop.data.model.favouriteFromJson
import org.koitharu.kotatsu.desktop.data.model.historyFromJson
import org.koitharu.kotatsu.desktop.data.model.toJson
import java.nio.file.Path

class ActivityStore(file: Path) : PersistentStore(file, debounceMillis = 250L) {

	private val historyByManga = LinkedHashMap<Long, HistoryEntry>()
	private val favourites = LinkedHashMap<Long, Favourite>()
	private val categories = LinkedHashMap<Int, FavouriteCategory>()
	private val bookmarks = ArrayList<Bookmark>()

	override fun onLoad(root: JsonValue?) {
		historyByManga.clear()
		favourites.clear()
		categories.clear()
		bookmarks.clear()
		val obj = root?.asObj() ?: run {
			categories[FavouriteCategory.DEFAULT_CATEGORY_ID] = FavouriteCategory.default()
			return
		}
		(obj["history"] as? JsonValue.Arr)?.items?.forEach { item ->
			historyFromJson(item)?.let { historyByManga[it.mangaId] = it }
		}
		(obj["favourites"] as? JsonValue.Arr)?.items?.forEach { item ->
			favouriteFromJson(item)?.let { favourites[it.mangaId] = it }
		}
		(obj["categories"] as? JsonValue.Arr)?.items?.forEach { item ->
			categoryFromJson(item)?.let { categories[it.categoryId] = it }
		}
		(obj["bookmarks"] as? JsonValue.Arr)?.items?.forEach { item ->
			bookmarkFromJson(item)?.let { bookmarks.add(it) }
		}
		if (categories.isEmpty()) {
			categories[FavouriteCategory.DEFAULT_CATEGORY_ID] = FavouriteCategory.default()
		}
	}

	override fun buildDocument(): JsonValue = jsonObject {
		put("schema_version", intJson(SCHEMA_VERSION))
		put("history", jsonArray { historyByManga.values.forEach { add(it.toJson()) } })
		put("favourites", jsonArray { favourites.values.forEach { add(it.toJson()) } })
		put("categories", jsonArray { categories.values.forEach { add(it.toJson()) } })
		put("bookmarks", jsonArray { bookmarks.forEach { add(it.toJson()) } })
	}

	// --- History ------------------------------------------------------------------------------

	/** Upstream keeps a single history row per manga, ordered by `updated_at` descending. */
	fun history(): List<HistoryEntry> = locked {
		historyByManga.values.sortedByDescending { it.updatedAt }
	}

	fun historyFor(mangaId: Long): HistoryEntry? = locked { historyByManga[mangaId] }

	fun putHistory(entry: HistoryEntry) = locked {
		val previous = historyByManga[entry.mangaId]
		historyByManga[entry.mangaId] = if (previous == null || entry.createdAt == 0L) {
			entry.copy(createdAt = if (previous?.createdAt ?: 0L != 0L) previous!!.createdAt else entry.createdAt)
		} else {
			entry.copy(createdAt = previous.createdAt)
		}
		markDirty()
	}

	fun removeHistory(mangaId: Long) = locked {
		historyByManga.remove(mangaId)
		markDirty()
	}

	fun clearHistory() = locked {
		historyByManga.clear()
		markDirty()
	}

	/**
	 * Merges an imported history entry. An existing, newer local position wins: restoring an old
	 * phone backup must not rewind the desktop reading position.
	 */
	fun mergeHistory(entry: HistoryEntry): Boolean = locked {
		val previous = historyByManga[entry.mangaId]
		if (previous != null && previous.updatedAt > entry.updatedAt) {
			return@locked false
		}
		historyByManga[entry.mangaId] = entry
		markDirty()
		true
	}

	// --- Favourites ---------------------------------------------------------------------------

	fun isFavourite(mangaId: Long): Boolean = locked { favourites.containsKey(mangaId) }

	fun favouriteOf(mangaId: Long): Favourite? = locked { favourites[mangaId] }

	fun allFavourites(): List<Favourite> = locked { favourites.values.toList() }

	fun toggleFavourite(mangaId: Long, now: Long = System.currentTimeMillis()): Boolean = locked {
		if (favourites.remove(mangaId) != null) {
			markDirty()
			false
		} else {
			favourites[mangaId] = Favourite(
				mangaId = mangaId,
				categoryId = FavouriteCategory.DEFAULT_CATEGORY_ID,
				createdAt = now,
			)
			markDirty()
			true
		}
	}

	fun putFavourite(favourite: Favourite) = locked {
		favourites[favourite.mangaId] = favourite
		markDirty()
	}

	fun removeFavourite(mangaId: Long) = locked {
		favourites.remove(mangaId)
		markDirty()
	}

	// --- Categories ---------------------------------------------------------------------------

	fun categories(): List<FavouriteCategory> = locked { categories.values.sortedBy { it.sortKey } }

	fun getCategory(categoryId: Int): FavouriteCategory? = locked { categories[categoryId] }

	fun putCategory(category: FavouriteCategory) = locked {
		categories[category.categoryId] = category
		markDirty()
	}

	fun removeCategory(categoryId: Int) = locked {
		if (categoryId == FavouriteCategory.DEFAULT_CATEGORY_ID) return@locked
		categories.remove(categoryId)
		favourites.values.filter { it.categoryId == categoryId }.forEach {
			favourites[it.mangaId] = it.copy(categoryId = FavouriteCategory.DEFAULT_CATEGORY_ID)
		}
		markDirty()
	}

	// --- Bookmarks ----------------------------------------------------------------------------

	fun bookmarks(): List<Bookmark> = locked { bookmarks.toList() }

	fun bookmarksFor(mangaId: Long): List<Bookmark> = locked {
		bookmarks.filter { it.mangaId == mangaId }.sortedByDescending { it.createdAt }
	}

	fun bookmarksForChapter(mangaId: Long, chapterId: Long): List<Bookmark> = locked {
		bookmarks.filter { it.mangaId == mangaId && it.chapterId == chapterId }.sortedBy { it.page }
	}

	fun addBookmark(bookmark: Bookmark) = locked {
		val existing = bookmarks.firstOrNull { it.mangaId == bookmark.mangaId && it.pageId == bookmark.pageId }
		if (existing != null) {
			bookmarks[bookmarks.indexOf(existing)] = bookmark
		} else {
			bookmarks.add(bookmark)
		}
		markDirty()
	}

	fun removeBookmark(mangaId: Long, pageId: Long) = locked {
		bookmarks.removeAll { it.mangaId == mangaId && it.pageId == pageId }
		markDirty()
	}

	// --- Backup helpers -----------------------------------------------------------------------

	fun putHistoryRaw(entry: HistoryEntry) = mergeHistory(entry)

	fun upsertFavouriteRaw(favourite: Favourite) = putFavourite(favourite)

	fun addBookmarkRaw(bookmark: Bookmark) = addBookmark(bookmark)

	fun percentFor(mangaId: Long): Float = locked { historyByManga[mangaId]?.percent ?: PROGRESS_NONE }

	companion object {
		const val SCHEMA_VERSION = 1
	}
}
