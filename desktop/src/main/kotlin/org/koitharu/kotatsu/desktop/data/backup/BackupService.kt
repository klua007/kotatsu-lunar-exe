/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * Ported from app/src/main/kotlin/org/koitharu/kotatsu/backups/data/BackupRepository.kt
 * (read/write of the ZIP-of-JSON-arrays format). The database side is replaced by the JSON stores
 * of the desktop build. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Compatibility notes (see also docs/BACKUP_FORMAT.md):
 *  - every entry is a JSON array; the Android app writes `[obj,obj,…]` and reads it with
 *    kotlinx.serialization's ARRAY_WRAPPED sequence mode;
 *  - `settings` is a one-element array holding the flattened SharedPreferences map;
 *  - unknown or unsupported sections are preserved in the settings store instead of being dropped,
 *    so a backup can be imported, exported and taken back to Android without losing information.
 */
package org.koitharu.kotatsu.desktop.data.backup

import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.core.json.Json
import org.koitharu.kotatsu.desktop.core.json.JsonValue
import org.koitharu.kotatsu.desktop.core.json.add
import org.koitharu.kotatsu.desktop.core.json.asIntOrNull
import org.koitharu.kotatsu.desktop.core.json.asObj
import org.koitharu.kotatsu.desktop.core.json.asBooleanOrNull
import org.koitharu.kotatsu.desktop.core.json.asStringOrNull
import org.koitharu.kotatsu.desktop.core.json.boolJson
import org.koitharu.kotatsu.desktop.core.json.jsonArray
import org.koitharu.kotatsu.desktop.core.json.jsonObject
import org.koitharu.kotatsu.desktop.core.json.stringJson
import org.koitharu.kotatsu.desktop.data.model.Bookmark
import org.koitharu.kotatsu.desktop.data.model.Favourite
import org.koitharu.kotatsu.desktop.data.model.FavouriteCategory
import org.koitharu.kotatsu.desktop.data.model.SOURCE_LOCAL
import org.koitharu.kotatsu.desktop.data.model.bookmarkFromJson
import org.koitharu.kotatsu.desktop.data.model.categoryFromJson
import org.koitharu.kotatsu.desktop.data.model.historyFromJson
import org.koitharu.kotatsu.desktop.data.model.mangaFromJson
import org.koitharu.kotatsu.desktop.data.model.toJson
import org.koitharu.kotatsu.desktop.data.store.ActivityStore
import org.koitharu.kotatsu.desktop.data.store.LibraryStore
import org.koitharu.kotatsu.desktop.data.store.SettingsStore
import org.koitharu.kotatsu.desktop.data.store.OpdsCatalog
import org.koitharu.kotatsu.desktop.data.store.SourcesStore
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupReport(
	val backupAppId: String? = null,
	val backupAppVersion: Int = 0,
	val favourites: Int = 0,
	val history: Int = 0,
	val categories: Int = 0,
	val bookmarks: Int = 0,
	val sources: Int = 0,
	val settingsApplied: Int = 0,
	val mangaAdded: Int = 0,
	/** Distinct titles coming from Android online sources (metadata only in this build). */
	val unsupportedMangaIds: Set<Long> = emptySet(),
	val preservedSections: List<String> = emptyList(),
	val skipped: Int = 0,
	val invalid: Int = 0,
	val messages: List<String> = emptyList(),
) {

	val mangaFromUnsupportedSources: Int get() = unsupportedMangaIds.size
}

class BackupService(
	private val library: LibraryStore,
	private val activity: ActivityStore,
	private val settings: SettingsStore,
	/** Optional so that the existing tests can keep constructing the service with three stores. */
	private val sourcesStore: SourcesStore? = null,
) {

	private companion object {
		const val TAG = "Backup"
		const val APP_ID = "org.koitharu.kotatsu.desktop"
		const val APP_VERSION = 1
	}

	// --- Export -------------------------------------------------------------------------------

	/** Writes a backup compatible with the Android app and returns the created file. */
	@Throws(IOException::class)
	fun export(target: Path): Path {
		Files.createDirectories(target.parent)
		Files.newOutputStream(target).use { output ->
			ZipOutputStream(output).use(::writeBackup)
		}
		Log.i(TAG, "Backup written to $target")
		return target
	}

	private fun writeBackup(zip: ZipOutputStream) {
		writeJsonArray(
			zip,
			BackupSection.INDEX,
			listOf(
				jsonObject {
					put("app_id", stringJson(APP_ID))
					put("app_version", JsonValue.Number(APP_VERSION.toDouble(), APP_VERSION.toString()))
					put("created_at", JsonValue.Number(System.currentTimeMillis().toDouble(), System.currentTimeMillis().toString()))
				},
			),
		)
		writeJsonArray(
			zip,
			BackupSection.CATEGORIES,
			activity.categories().map { listOf(it.toJson()) }.flatten(),
		)
		val mangaById = library.allManga().associateBy { it.id }
		writeJsonArray(
			zip,
			BackupSection.FAVOURITES,
			activity.allFavourites().mapNotNull { favourite ->
				val manga = mangaById[favourite.mangaId] ?: return@mapNotNull null
				jsonObject {
					put("manga_id", JsonValue.Number(favourite.mangaId.toDouble(), favourite.mangaId.toString()))
					put("category_id", JsonValue.Number(favourite.categoryId.toDouble(), favourite.categoryId.toString()))
					put("sort_key", JsonValue.Number(favourite.sortKey.toDouble(), favourite.sortKey.toString()))
					put("pinned", JsonValue.Bool(favourite.isPinned))
					put("created_at", JsonValue.Number(favourite.createdAt.toDouble(), favourite.createdAt.toString()))
					put("manga", manga.toJson())
				}
			},
		)
		writeJsonArray(
			zip,
			BackupSection.HISTORY,
			activity.history().mapNotNull { entry ->
				val manga = mangaById[entry.mangaId] ?: return@mapNotNull null
				jsonObject {
					put("manga_id", JsonValue.Number(entry.mangaId.toDouble(), entry.mangaId.toString()))
					put("created_at", JsonValue.Number(entry.createdAt.toDouble(), entry.createdAt.toString()))
					put("updated_at", JsonValue.Number(entry.updatedAt.toDouble(), entry.updatedAt.toString()))
					put("chapter_id", JsonValue.Number(entry.chapterId.toDouble(), entry.chapterId.toString()))
					put("page", JsonValue.Number(entry.page.toDouble(), entry.page.toString()))
					put("scroll", JsonValue.Number(entry.scroll.toDouble(), entry.scroll.toString()))
					put("percent", JsonValue.Number(entry.percent.toDouble(), entry.percent.toString()))
					put("chapters", JsonValue.Number(entry.chaptersCount.toDouble(), entry.chaptersCount.toString()))
					put("manga", manga.toJson())
				}
			},
		)
		writeJsonArray(
			zip,
			BackupSection.BOOKMARKS,
			library.allManga().mapNotNull { manga ->
				val bookmarks = activity.bookmarksFor(manga.id)
				if (bookmarks.isEmpty()) return@mapNotNull null
				jsonObject {
					put("manga", manga.toJson())
					put("tags", jsonArray { })
					put(
						"bookmarks",
						jsonArray {
							bookmarks.forEach { bookmark ->
								add(
									jsonObject {
										put("manga_id", JsonValue.Number(bookmark.mangaId.toDouble(), bookmark.mangaId.toString()))
										put("page_id", JsonValue.Number(bookmark.pageId.toDouble(), bookmark.pageId.toString()))
										put("chapter_id", JsonValue.Number(bookmark.chapterId.toDouble(), bookmark.chapterId.toString()))
										put("page", JsonValue.Number(bookmark.page.toDouble(), bookmark.page.toString()))
										put("scroll", JsonValue.Number(bookmark.scroll.toDouble(), bookmark.scroll.toString()))
										put("image_url", stringJson(bookmark.imageUrl))
										put("created_at", JsonValue.Number(bookmark.createdAt.toDouble(), bookmark.createdAt.toString()))
										put("percent", JsonValue.Number(bookmark.percent.toDouble(), bookmark.percent.toString()))
									},
								)
							}
						},
					)
				}
			},
		)
		writeObjectArray(
			zip,
			BackupSection.SETTINGS,
			jsonObject { settings.snapshot().forEach { (key, value) -> put(key, value) } },
		)
		writeJsonArray(zip, BackupSection.SOURCES, sourceEntriesForBackup())
	}

	private fun writeJsonArray(zip: ZipOutputStream, section: BackupSection, items: List<JsonValue>) {
		zip.putNextEntry(ZipEntry(section.entryName))
		zip.write("[")
		items.forEachIndexed { index, item ->
			if (index > 0) zip.write(",")
			zip.write(Json.stringify(item))
		}
		zip.write("]")
		zip.closeEntry()
		zip.flush()
	}

	private fun writeObjectArray(zip: ZipOutputStream, section: BackupSection, value: JsonValue) {
		zip.putNextEntry(ZipEntry(section.entryName))
		zip.write("[")
		zip.write(Json.stringify(value))
		zip.write("]")
		zip.closeEntry()
		zip.flush()
	}

	// --- Import -------------------------------------------------------------------------------

	/**
	 * Merges a backup into the local store. This never deletes anything: existing manga keep their
	 * chapters, a locally newer reading position wins over the imported one, and sections the
	 * desktop build cannot interpret are preserved in the settings store.
	 */
	@Throws(IOException::class)
	fun import(source: Path): BackupReport {
		var report = BackupReport()
		Files.newInputStream(source).use { input ->
			ZipInputStream(input).use { zip -> report = readBackup(zip) }
		}
		Log.i(
			TAG,
			"Backup import: favourites=${report.favourites} history=${report.history} " +
				"categories=${report.categories} bookmarks=${report.bookmarks} invalid=${report.invalid}",
		)
		return report
	}

	private fun readBackup(zip: ZipInputStream): BackupReport {
		var report = BackupReport()
		var recognizedSections = 0
		var entry = zip.nextEntry
		while (entry != null) {
			if (!entry.isDirectory) {
				val section = BackupSection.of(entry)
				if (section != null) recognizedSections++
				val payload = zip.readBytes().decodeToString()
				report = when (section) {
					BackupSection.INDEX -> mergeReport(report, readIndex(payload))
					BackupSection.CATEGORIES -> mergeReport(report, importCategories(payload))
					BackupSection.FAVOURITES -> mergeReport(report, importFavourites(payload))
					BackupSection.HISTORY -> mergeReport(report, importHistory(payload))
					BackupSection.BOOKMARKS -> mergeReport(report, importBookmarks(payload))
					BackupSection.SETTINGS -> mergeReport(report, importSettings(payload))
					BackupSection.SOURCES -> mergeReport(report, importSources(payload))
					BackupSection.SETTINGS_READER_GRID,
					BackupSection.SCROBBLING,
					BackupSection.STATS,
					BackupSection.SAVED_FILTERS,
					BackupSection.IDENTITY,
					-> preserveSection(report, section, payload)

					null -> report.copy(skipped = report.skipped + 1)
				}
			}
			zip.closeEntry()
			entry = zip.nextEntry
		}
		if (recognizedSections == 0) {
			throw IOException("The selected file is not a Kotatsu backup (no known sections found)")
		}
		return report
	}

	private fun mergeReport(target: BackupReport, other: BackupReport): BackupReport = BackupReport(
		backupAppId = other.backupAppId ?: target.backupAppId,
		backupAppVersion = if (other.backupAppVersion != 0) other.backupAppVersion else target.backupAppVersion,
		favourites = target.favourites + other.favourites,
		history = target.history + other.history,
		categories = target.categories + other.categories,
		bookmarks = target.bookmarks + other.bookmarks,
		sources = target.sources + other.sources,
		settingsApplied = target.settingsApplied + other.settingsApplied,
		mangaAdded = target.mangaAdded + other.mangaAdded,
		unsupportedMangaIds = target.unsupportedMangaIds + other.unsupportedMangaIds,
		preservedSections = target.preservedSections + other.preservedSections,
		skipped = target.skipped + other.skipped,
		invalid = target.invalid + other.invalid,
		messages = target.messages + other.messages,
	)

	private fun parseItems(payload: String): List<JsonValue>? {
		val root = runCatching { Json.parse(payload.trim()) }.getOrNull() ?: return null
		return when (root) {
			is JsonValue.Arr -> root.items
			is JsonValue.Obj -> listOf(root) // tolerate a non-wrapped document
			else -> null
		}
	}

	private fun readIndex(payload: String): BackupReport {
		val item = parseItems(payload)?.firstOrNull()?.asObj() ?: return BackupReport(invalid = 1)
		return BackupReport(
			backupAppId = item["app_id"].asStringOrNull(),
			backupAppVersion = item["app_version"].asIntOrNull() ?: 0,
		)
	}

	private fun importCategories(payload: String): BackupReport {
		val items = parseItems(payload) ?: return BackupReport(invalid = 1)
		var count = 0
		var invalid = 0
		items.forEach { item ->
			val category = categoryFromJson(item)
			if (category == null) {
				invalid++
			} else {
				if (activity.getCategory(category.categoryId) == null) {
					activity.putCategory(category)
				}
				count++
			}
		}
		return BackupReport(categories = count, invalid = invalid)
	}

	private fun importFavourites(payload: String): BackupReport {
		val items = parseItems(payload) ?: return BackupReport(invalid = 1)
		var count = 0
		var invalid = 0
		var added = 0
		val unsupported = mutableSetOf<Long>()
		items.forEach { item ->
			val obj = item.asObj()
			val manga = mangaFromJson(obj?.get("manga"))
			if (obj == null || manga == null) {
				invalid++
				return@forEach
			}
			if (library.getManga(manga.id) == null) {
				if (library.insertIfAbsent(manga, emptyList())) added++
			}
			if (manga.source != SOURCE_LOCAL) unsupported.add(manga.id)
			val favourite = Favourite(
				mangaId = obj["manga_id"].asIntOrNull()?.toLong() ?: manga.id,
				categoryId = obj["category_id"].asIntOrNull() ?: FavouriteCategory.DEFAULT_CATEGORY_ID,
				sortKey = obj["sort_key"].asIntOrNull() ?: 0,
				isPinned = obj["pinned"]?.let { (it as? JsonValue.Bool)?.value } ?: false,
				createdAt = obj["created_at"].asIntOrNull()?.toLong() ?: System.currentTimeMillis(),
			)
			if (activity.favouriteOf(favourite.mangaId) == null) {
				activity.putFavourite(favourite)
				count++
			}
		}
		return BackupReport(
			favourites = count,
			mangaAdded = added,
			unsupportedMangaIds = unsupported,
			invalid = invalid,
		)
	}

	private fun importHistory(payload: String): BackupReport {
		val items = parseItems(payload) ?: return BackupReport(invalid = 1)
		var count = 0
		var invalid = 0
		var added = 0
		val unsupported = mutableSetOf<Long>()
		items.forEach { item ->
			val obj = item.asObj()
			val manga = mangaFromJson(obj?.get("manga"))
			val entry = historyFromJson(obj)
			if (obj == null || manga == null || entry == null) {
				invalid++
				return@forEach
			}
			if (library.getManga(manga.id) == null) {
				if (library.insertIfAbsent(manga, emptyList())) added++
			}
			if (manga.source != SOURCE_LOCAL) unsupported.add(manga.id)
			if (activity.mergeHistory(entry.copy(mangaId = entry.mangaId))) count++
		}
		return BackupReport(
			history = count,
			mangaAdded = added,
			unsupportedMangaIds = unsupported,
			invalid = invalid,
		)
	}

	private fun importBookmarks(payload: String): BackupReport {
		val items = parseItems(payload) ?: return BackupReport(invalid = 1)
		var count = 0
		var invalid = 0
		val unsupported = mutableSetOf<Long>()
		items.forEach { item ->
			val obj = item.asObj()
			val manga = mangaFromJson(obj?.get("manga"))
			if (obj == null || manga == null) {
				invalid++
				return@forEach
			}
			if (library.getManga(manga.id) == null) {
				library.insertIfAbsent(manga, emptyList())
			}
			if (manga.source != SOURCE_LOCAL) unsupported.add(manga.id)
			val array = obj["bookmarks"] as? JsonValue.Arr
			array?.items?.forEach { bookmarkItem ->
				val bookmark: Bookmark = bookmarkFromJson(bookmarkItem) ?: run {
					invalid++
					return@forEach
				}
				activity.addBookmark(bookmark)
				count++
			}
		}
		return BackupReport(
			bookmarks = count,
			unsupportedMangaIds = unsupported,
			invalid = invalid,
		)
	}

	private fun importSettings(payload: String): BackupReport {
		val item = parseItems(payload)?.firstOrNull()?.asObj() ?: return BackupReport(invalid = 1)
		val applied = settings.applyImported(item.fields, SettingsStore.KNOWN_KEYS)
		return BackupReport(settingsApplied = applied)
	}

	private fun importSources(payload: String): BackupReport {
		val items = parseItems(payload) ?: return BackupReport(invalid = 1)
		// Enabled/pinned flags of sources this build knows are restored; the raw list is also kept
		// in settings so that ids belonging to other builds (the ~900 Android parsers) survive a
		// round trip instead of being dropped.
		settings.putRaw("imported_sources", JsonValue.Str(payload))
		val store = sourcesStore ?: return BackupReport(sources = items.size)
		var applied = 0
		items.forEach { item ->
			val obj = item.asObj() ?: return@forEach
			val id = obj["source"].asStringOrNull()?.takeIf { it.isNotBlank() } ?: return@forEach
			if (id == SOURCE_LOCAL) return@forEach
			val enabled = obj["enabled"].asBooleanOrNull() ?: true
			val pinned = obj["pinned"].asBooleanOrNull() ?: false
			val url = obj["url"].asStringOrNull()?.takeIf { it.startsWith("http") }
			if (url != null && id.startsWith("OPDS")) {
				if (store.catalogs().none { it.id == id }) {
					store.addCatalog(obj["title"].asStringOrNull() ?: id, url)
				}
			}
			if (store.importState(id, enabled, pinned)) applied++
		}
		return BackupReport(sources = items.size, settingsApplied = applied)
	}

	/** Sources known to this build, plus the always-present local shelf. */
	private fun sourceEntriesForBackup(): List<JsonValue> {
		val now = System.currentTimeMillis()
		val result = ArrayList<JsonValue>()
		result += jsonObject {
			put("source", stringJson(SOURCE_LOCAL))
			put("sort_key", JsonValue.Number(0.0, "0"))
			put("used_at", JsonValue.Number(now.toDouble(), now.toString()))
			put("added_in", JsonValue.Number(1.0, "1"))
			put("pinned", JsonValue.Bool(true))
			put("enabled", JsonValue.Bool(true))
		}
		val states = sourcesStore?.allStates().orEmpty()
		val catalogById = sourcesStore?.catalogs()?.associateBy(OpdsCatalog::id).orEmpty()
		states.forEach { (id, state) ->
			if (id == SOURCE_LOCAL) return@forEach
			result += jsonObject {
				put("source", stringJson(id))
				put("enabled", boolJson(state.enabled))
				put("pinned", JsonValue.Bool(state.pinned))
				put("url", stringJson(catalogById[id]?.url))
				put("title", stringJson(catalogById[id]?.title))
				put("sort_key", JsonValue.Number(0.0, "0"))
				put("added_in", JsonValue.Number(1.0, "1"))
			}
		}
		return result
	}

	private fun preserveSection(report: BackupReport, section: BackupSection, payload: String): BackupReport {
		settings.putRaw("imported_${section.entryName}", JsonValue.Str(payload))
		return report.copy(preservedSections = report.preservedSections + section.entryName)
	}

	// --- Helpers used by tests -----------------------------------------------------------------

	/** Builds the export document without touching the file system (used by the unit tests). */
	fun exportToStream(output: OutputStream) {
		ZipOutputStream(output).use(::writeBackup)
	}

	fun importFromStream(input: InputStream): BackupReport = ZipInputStream(input).use(::readBackup)
}

private fun OutputStream.write(text: String) = write(text.toByteArray(Charsets.UTF_8))
