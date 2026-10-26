/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file, but the data classes mirror the Android database entities of Kotatsu-Redo
 * (package org.koitharu.kotatsu.core.db.entity and the favourites, history, bookmarks and backups
 * modules), so that Android backups can be imported and so that a future SQL storage backend can
 * reuse the same columns. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.data.model

import org.koitharu.kotatsu.desktop.core.json.JsonValue
import org.koitharu.kotatsu.desktop.core.json.asBooleanOrNull
import org.koitharu.kotatsu.desktop.core.json.asFloatOrNull
import org.koitharu.kotatsu.desktop.core.json.asLongOrNull
import org.koitharu.kotatsu.desktop.core.json.add
import org.koitharu.kotatsu.desktop.core.json.asObj
import org.koitharu.kotatsu.desktop.core.json.asStringOrNull
import org.koitharu.kotatsu.desktop.core.json.floatJson
import org.koitharu.kotatsu.desktop.core.json.intJson
import org.koitharu.kotatsu.desktop.core.json.jsonArray
import org.koitharu.kotatsu.desktop.core.json.jsonObject
import org.koitharu.kotatsu.desktop.core.json.longJson
import org.koitharu.kotatsu.desktop.core.json.stringJson

/** Same sentinel as upstream `Manga.RATING_UNKNOWN`. */
const val RATING_UNKNOWN = -1f

/** Same sentinel as upstream `ReadingProgress.PROGRESS_NONE`. */
const val PROGRESS_NONE = -1f

const val PROGRESS_COMPLETED = 1f

/** Identifier of the only source implemented by the desktop build so far. */
const val SOURCE_LOCAL = "LOCAL"

data class Tag(
	val id: Long,
	val title: String,
	val key: String,
	val source: String = SOURCE_LOCAL,
)

data class Manga(
	val id: Long,
	val title: String,
	val altTitles: String? = null,
	/** Upstream `url` column: for local content this is the absolute archive/folder path. */
	val url: String,
	val publicUrl: String = url,
	val rating: Float = RATING_UNKNOWN,
	val isNsfw: Boolean = false,
	val contentRating: String? = null,
	val coverUrl: String? = null,
	val largeCoverUrl: String? = null,
	val state: String? = null,
	val authors: String? = null,
	val tags: List<Tag> = emptyList(),
	val source: String = SOURCE_LOCAL,
	/** Epoch millis; desktop-only bookkeeping columns. */
	val createdAt: Long = 0L,
	val updatedAt: Long = 0L,
) {

	val displayTitle: String
		get() = title.trim().ifEmpty { url.substringAfterLast('\\').substringAfterLast('/') }
}

data class Chapter(
	val chapterId: Long,
	val mangaId: Long,
	val title: String,
	val number: Float = 0f,
	val volume: Int = 0,
	/** Location of the pages: an archive path, or a directory path. */
	val url: String,
	val scanlator: String? = null,
	val uploadDate: Long = 0L,
	val branch: String? = null,
	val source: String = SOURCE_LOCAL,
	val index: Int = 0,
	/** Number of readable image entries, when known. */
	val pagesCount: Int = 0,
	/** True when a local offline copy exists under `<data>/downloads`. */
	val isDownloaded: Boolean = false,
	val isRead: Boolean = false,
)

data class MangaWithChapters(
	val manga: Manga,
	val chapters: List<Chapter>,
) {
	val sortedChapters: List<Chapter> get() = chapters.sortedBy { it.index }
}

enum class ReaderMode(val id: Int) {
	STANDARD(1),
	REVERSED(3),
	VERTICAL(4),
	WEBTOON(2),
	;

	companion object {

		fun valueOf(id: Int): ReaderMode? = entries.firstOrNull { it.id == id }

		fun fromName(name: String?): ReaderMode? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
	}
}

enum class ListSortOrder {
	NEWEST,
	OLDEST,
	ALPHABETIC,
	PROGRESS,
	LAST_READ,
	;

	companion object {

		fun fromName(name: String?): ListSortOrder? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
	}
}

data class HistoryEntry(
	val mangaId: Long,
	val chapterId: Long,
	val page: Int = 0,
	val scroll: Int = 0,
	val percent: Float = PROGRESS_NONE,
	val createdAt: Long = 0L,
	val updatedAt: Long = 0L,
	val chaptersCount: Int = 0,
)

data class FavouriteCategory(
	val categoryId: Int,
	val title: String,
	val createdAt: Long = 0L,
	val sortKey: Int = 0,
	val order: String = ListSortOrder.NEWEST.name,
	val track: Boolean = true,
	val isVisibleInLibrary: Boolean = true,
) {
	companion object {

		const val DEFAULT_CATEGORY_ID = 0
		const val DEFAULT_CATEGORY_TITLE = "Default"

		fun default(now: Long = System.currentTimeMillis()) = FavouriteCategory(
			categoryId = DEFAULT_CATEGORY_ID,
			title = DEFAULT_CATEGORY_TITLE,
			createdAt = now,
		)
	}
}

data class Favourite(
	val mangaId: Long,
	val categoryId: Int = FavouriteCategory.DEFAULT_CATEGORY_ID,
	val sortKey: Int = 0,
	val isPinned: Boolean = false,
	val createdAt: Long = 0L,
)

data class Bookmark(
	val mangaId: Long,
	val pageId: Long,
	val chapterId: Long,
	val page: Int,
	val scroll: Int = 0,
	val imageUrl: String = "",
	val createdAt: Long = 0L,
	val percent: Float = PROGRESS_NONE,
)

// ---------------------------------------------------------------------------------------------
// JSON mapping, using the column names of the Android database / backup format
// ---------------------------------------------------------------------------------------------

fun Manga.toJson(): JsonValue.Obj = jsonObject {
	put("manga_id", longJson(id))
	put("title", stringJson(title))
	put("alt_title", stringJson(altTitles))
	put("url", stringJson(url))
	put("public_url", stringJson(publicUrl))
	put("rating", floatJson(rating))
	put("nsfw", JsonValue.Bool(isNsfw))
	put("content_rating", stringJson(contentRating))
	put("cover_url", stringJson(coverUrl))
	put("large_cover_url", stringJson(largeCoverUrl))
	put("state", stringJson(state))
	put("author", stringJson(authors))
	put("source", stringJson(source))
	put("created_at", longJson(createdAt))
	put("updated_at", longJson(updatedAt))
	put(
		"tags",
		jsonArray {
			tags.forEach { tag ->
				add(
					jsonObject {
						put("tag_id", longJson(tag.id))
						put("title", stringJson(tag.title))
						put("key", stringJson(tag.key))
						put("source", stringJson(tag.source))
					},
				)
			}
		},
	)
}

fun mangaFromJson(value: JsonValue?): Manga? {
	val obj = value.asObj() ?: return null
	val title = obj["title"].asStringOrNull() ?: return null
	val url = obj["url"].asStringOrNull() ?: return null
	val id = obj["manga_id"].asLongOrNull() ?: obj["id"].asLongOrNull() ?: return null
	val tags = buildList {
		val array = obj["tags"] as? JsonValue.Arr
		array?.items?.forEach { item ->
			val tag = item.asObj() ?: return@forEach
			val tagTitle = tag["title"].asStringOrNull() ?: return@forEach
			val tagKey = tag["key"].asStringOrNull() ?: tagTitle.lowercase()
			add(
				Tag(
					id = tag["tag_id"].asLongOrNull() ?: tag["id"].asLongOrNull() ?: stableId(tagKey),
					title = tagTitle,
					key = tagKey,
					source = tag["source"].asStringOrNull() ?: SOURCE_LOCAL,
				),
			)
		}
	}
	return Manga(
		id = id,
		title = title,
		altTitles = obj["alt_title"].asStringOrNull(),
		url = url,
		publicUrl = obj["public_url"].asStringOrNull() ?: url,
		rating = obj["rating"].asFloatOrNull() ?: RATING_UNKNOWN,
		isNsfw = obj["nsfw"].asBooleanOrNull() ?: false,
		contentRating = obj["content_rating"].asStringOrNull(),
		coverUrl = obj["cover_url"].asStringOrNull(),
		largeCoverUrl = obj["large_cover_url"].asStringOrNull(),
		state = obj["state"].asStringOrNull(),
		authors = obj["author"].asStringOrNull(),
		tags = tags,
		source = obj["source"].asStringOrNull() ?: SOURCE_LOCAL,
		createdAt = obj["created_at"].asLongOrNull() ?: 0L,
		updatedAt = obj["updated_at"].asLongOrNull() ?: 0L,
	)
}

fun Chapter.toJson(): JsonValue.Obj = jsonObject {
	put("chapter_id", longJson(chapterId))
	put("manga_id", longJson(mangaId))
	put("name", stringJson(title))
	put("number", floatJson(number))
	put("volume", intJson(volume))
	put("url", stringJson(url))
	put("scanlator", stringJson(scanlator))
	put("upload_date", longJson(uploadDate))
	put("branch", stringJson(branch))
	put("source", stringJson(source))
	put("index", intJson(index))
	put("pages_count", intJson(pagesCount))
	put("downloaded", JsonValue.Bool(isDownloaded))
	put("read", JsonValue.Bool(isRead))
}

fun chapterFromJson(value: JsonValue?): Chapter? {
	val obj = value.asObj() ?: return null
	return Chapter(
		chapterId = obj["chapter_id"].asLongOrNull() ?: return null,
		mangaId = obj["manga_id"].asLongOrNull() ?: return null,
		title = obj["name"].asStringOrNull() ?: "?",
		number = obj["number"].asFloatOrNull() ?: 0f,
		volume = obj["volume"].asLongOrNull()?.toInt() ?: 0,
		url = obj["url"].asStringOrNull() ?: return null,
		scanlator = obj["scanlator"].asStringOrNull(),
		uploadDate = obj["upload_date"].asLongOrNull() ?: 0L,
		branch = obj["branch"].asStringOrNull(),
		source = obj["source"].asStringOrNull() ?: SOURCE_LOCAL,
		index = obj["index"].asLongOrNull()?.toInt() ?: 0,
		pagesCount = obj["pages_count"].asLongOrNull()?.toInt() ?: 0,
		isDownloaded = obj["downloaded"].asBooleanOrNull() ?: false,
		isRead = obj["read"].asBooleanOrNull() ?: false,
	)
}

fun HistoryEntry.toJson(): JsonValue.Obj = jsonObject {
	put("manga_id", longJson(mangaId))
	put("chapter_id", longJson(chapterId))
	put("page", intJson(page))
	put("scroll", intJson(scroll))
	put("percent", floatJson(percent))
	put("created_at", longJson(createdAt))
	put("updated_at", longJson(updatedAt))
	put("chapters", intJson(chaptersCount))
}

fun historyFromJson(value: JsonValue?): HistoryEntry? {
	val obj = value.asObj() ?: return null
	return HistoryEntry(
		mangaId = obj["manga_id"].asLongOrNull() ?: return null,
		chapterId = obj["chapter_id"].asLongOrNull() ?: 0L,
		page = obj["page"].asLongOrNull()?.toInt() ?: 0,
		scroll = obj["scroll"].asLongOrNull()?.toInt() ?: 0,
		percent = obj["percent"].asFloatOrNull() ?: PROGRESS_NONE,
		createdAt = obj["created_at"].asLongOrNull() ?: 0L,
		updatedAt = obj["updated_at"].asLongOrNull() ?: 0L,
		chaptersCount = obj["chapters"].asLongOrNull()?.toInt() ?: 0,
	)
}

fun Favourite.toJson(): JsonValue.Obj = jsonObject {
	put("manga_id", longJson(mangaId))
	put("category_id", intJson(categoryId))
	put("sort_key", intJson(sortKey))
	put("pinned", JsonValue.Bool(isPinned))
	put("created_at", longJson(createdAt))
}

fun favouriteFromJson(value: JsonValue?): Favourite? {
	val obj = value.asObj() ?: return null
	return Favourite(
		mangaId = obj["manga_id"].asLongOrNull() ?: return null,
		categoryId = obj["category_id"].asLongOrNull()?.toInt() ?: FavouriteCategory.DEFAULT_CATEGORY_ID,
		sortKey = obj["sort_key"].asLongOrNull()?.toInt() ?: 0,
		isPinned = obj["pinned"].asBooleanOrNull() ?: false,
		createdAt = obj["created_at"].asLongOrNull() ?: 0L,
	)
}

fun FavouriteCategory.toJson(): JsonValue.Obj = jsonObject {
	put("category_id", intJson(categoryId))
	put("created_at", longJson(createdAt))
	put("sort_key", intJson(sortKey))
	put("title", stringJson(title))
	put("order", stringJson(order))
	put("track", JsonValue.Bool(track))
	put("show_in_lib", JsonValue.Bool(isVisibleInLibrary))
}

fun categoryFromJson(value: JsonValue?): FavouriteCategory? {
	val obj = value.asObj() ?: return null
	return FavouriteCategory(
		categoryId = obj["category_id"].asLongOrNull()?.toInt() ?: return null,
		title = obj["title"].asStringOrNull() ?: FavouriteCategory.DEFAULT_CATEGORY_TITLE,
		createdAt = obj["created_at"].asLongOrNull() ?: 0L,
		sortKey = obj["sort_key"].asLongOrNull()?.toInt() ?: 0,
		order = obj["order"].asStringOrNull() ?: ListSortOrder.NEWEST.name,
		track = obj["track"].asBooleanOrNull() ?: true,
		isVisibleInLibrary = obj["show_in_lib"].asBooleanOrNull() ?: true,
	)
}

fun Bookmark.toJson(): JsonValue.Obj = jsonObject {
	put("manga_id", longJson(mangaId))
	put("page_id", longJson(pageId))
	put("chapter_id", longJson(chapterId))
	put("page", intJson(page))
	put("scroll", intJson(scroll))
	put("image_url", stringJson(imageUrl))
	put("created_at", longJson(createdAt))
	put("percent", floatJson(percent))
}

fun bookmarkFromJson(value: JsonValue?): Bookmark? {
	val obj = value.asObj() ?: return null
	return Bookmark(
		mangaId = obj["manga_id"].asLongOrNull() ?: return null,
		pageId = obj["page_id"].asLongOrNull() ?: 0L,
		chapterId = obj["chapter_id"].asLongOrNull() ?: 0L,
		page = obj["page"].asLongOrNull()?.toInt() ?: 0,
		scroll = obj["scroll"].asLongOrNull()?.toInt() ?: 0,
		imageUrl = obj["image_url"].asStringOrNull() ?: "",
		createdAt = obj["created_at"].asLongOrNull() ?: 0L,
		percent = obj["percent"].asFloatOrNull() ?: PROGRESS_NONE,
	)
}
