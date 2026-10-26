/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Façade over the stores, used by the UI. Keeps Swing code free of persistence details and gives
 * the tests a single entry point.
 */
package org.koitharu.kotatsu.desktop.data

import org.koitharu.kotatsu.desktop.core.AppDirs
import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.data.backup.BackupReport
import org.koitharu.kotatsu.desktop.data.backup.BackupService
import org.koitharu.kotatsu.desktop.data.downloads.DownloadService
import org.koitharu.kotatsu.desktop.data.local.ArchiveReader
import org.koitharu.kotatsu.desktop.data.local.ChapterContent
import org.koitharu.kotatsu.desktop.data.local.CoverCache
import org.koitharu.kotatsu.desktop.data.local.ImageDecoder
import org.koitharu.kotatsu.desktop.data.local.ImportResult
import org.koitharu.kotatsu.desktop.data.local.LocalLibraryImporter
import org.koitharu.kotatsu.desktop.data.model.Chapter
import org.koitharu.kotatsu.desktop.data.model.HistoryEntry
import org.koitharu.kotatsu.desktop.data.model.ListSortOrder
import org.koitharu.kotatsu.desktop.data.model.Manga
import org.koitharu.kotatsu.desktop.data.model.MangaWithChapters
import org.koitharu.kotatsu.desktop.data.model.PROGRESS_NONE
import org.koitharu.kotatsu.desktop.data.model.SOURCE_LOCAL
import org.koitharu.kotatsu.desktop.data.store.ActivityStore
import org.koitharu.kotatsu.desktop.data.store.LibraryStore
import org.koitharu.kotatsu.desktop.data.store.SettingsStore
import org.koitharu.kotatsu.desktop.reader.ReadingProgress
import java.nio.file.Path
import java.nio.file.Paths

enum class LibraryFilter {
	ALL,
	FAVOURITES,
	DOWNLOADED,
}

data class LibraryItem(
	val manga: Manga,
	val chaptersCount: Int,
	val downloadedCount: Int,
	val isFavourite: Boolean,
	val progress: Float,
	val lastReadAt: Long,
	val isReadable: Boolean,
)

data class HistoryItem(
	val manga: Manga,
	val entry: HistoryEntry,
	val chapter: Chapter?,
	val chaptersCount: Int,
)

class AppRepository(
	val settings: SettingsStore,
	val library: LibraryStore,
	val activity: ActivityStore,
) {

	val downloads = DownloadService(library)
	val importer = LocalLibraryImporter(library)
	val backupService = BackupService(library, activity, settings)

	fun init() {
		AppDirs.ensureCreated()
		settings.load()
		library.load()
		activity.load()
		val fixed = downloads.reconcile()
		if (fixed > 0) Log.i(TAG, "Reconciled $fixed offline chapter flags")
	}

	fun flush() {
		settings.flush()
		library.flush()
		activity.flush()
	}

	// --- Library ------------------------------------------------------------------------------

	/** Titles that this build can actually open (local files only, for now). */
	fun libraryItems(
		query: String = "",
		filter: LibraryFilter = LibraryFilter.ALL,
		sort: ListSortOrder = settings.librarySortOrder,
	): List<LibraryItem> {
		val needle = query.trim().lowercase()
		val items = library.allManga()
			.asSequence()
			.filter { it.source == SOURCE_LOCAL }
			.map(::toLibraryItem)
			.filter { item -> matchesQuery(item.manga, needle) }
			.filter { item ->
				when (filter) {
					LibraryFilter.ALL -> true
					LibraryFilter.FAVOURITES -> item.isFavourite
					LibraryFilter.DOWNLOADED -> item.downloadedCount > 0
				}
			}
			.toList()
		return sortItems(items, sort)
	}

	fun unsupportedMangaCount(): Int = library.allManga().count { it.source != SOURCE_LOCAL }

	private fun sortItems(items: List<LibraryItem>, sort: ListSortOrder): List<LibraryItem> = when (sort) {
		ListSortOrder.NEWEST -> items.sortedByDescending { it.manga.createdAt }
		ListSortOrder.OLDEST -> items.sortedBy { it.manga.createdAt }
		ListSortOrder.ALPHABETIC -> items.sortedBy { it.manga.title.lowercase() }
		ListSortOrder.PROGRESS -> items.sortedByDescending { it.progress }
		ListSortOrder.LAST_READ -> items.sortedByDescending { it.lastReadAt }
	}

	private fun matchesQuery(manga: Manga, needle: String): Boolean {
		if (needle.isEmpty()) return true
		if (manga.title.lowercase().contains(needle)) return true
		if (manga.altTitles?.lowercase()?.contains(needle) == true) return true
		if (manga.authors?.lowercase()?.contains(needle) == true) return true
		return manga.tags.any { it.title.lowercase().contains(needle) }
	}

	private fun toLibraryItem(manga: Manga): LibraryItem {
		val chapters = library.chaptersOf(manga.id)
		val history = activity.historyFor(manga.id)
		return LibraryItem(
			manga = manga,
			chaptersCount = chapters.size,
			downloadedCount = chapters.count { it.isDownloaded },
			isFavourite = activity.isFavourite(manga.id),
			progress = history?.percent ?: PROGRESS_NONE,
			lastReadAt = history?.updatedAt ?: 0L,
			isReadable = chapters.isNotEmpty() || ArchiveReader.isArchive(Paths.get(manga.url)) ||
				java.nio.file.Files.isDirectory(Paths.get(manga.url)),
		)
	}

	fun details(mangaId: Long): MangaWithChapters? = library.getWithChapters(mangaId)

	fun searchAll(query: String): List<LibraryItem> = libraryItems(query = query, filter = LibraryFilter.ALL)

	/** Refreshes chapters of every local title by re-scanning its path. */
	fun refreshLibrary(): ImportResult {
		val paths = library.allManga()
			.filter { it.source == SOURCE_LOCAL }
			.mapNotNull { runCatching { Paths.get(it.url) }.getOrNull() }
			.filter { java.nio.file.Files.exists(it) }
		return importer.importPaths(paths)
	}

	fun importPaths(paths: List<Path>): ImportResult = importer.importPaths(paths)

	fun removeManga(mangaId: Long) {
		downloads.deleteForManga(mangaId)
		library.removeManga(mangaId)
		activity.removeHistory(mangaId)
		activity.removeFavourite(mangaId)
		CoverCache.evict(mangaId)
	}

	fun toggleFavourite(mangaId: Long): Boolean = activity.toggleFavourite(mangaId)

	// --- Covers -------------------------------------------------------------------------------

	/** Cover path for the library grid; decoded on demand and cached as a PNG. */
	fun coverFor(manga: Manga): Path? {
		CoverCache.existing(manga.id)?.let { return it }
		val chapter = library.chaptersOf(manga.id).firstOrNull() ?: return null
		return CoverCache.getOrCreate(manga.id) {
			val content = downloads.contentOf(chapter)
			if (content.pageCount == 0) return@getOrCreate null
			val bytes = content.readPage(0) ?: return@getOrCreate null
			ImageDecoder.decode(bytes)
		}
	}

	// --- Reader -------------------------------------------------------------------------------

	fun chapterContent(chapter: Chapter): ChapterContent = downloads.contentOf(chapter)

	fun downloadChapter(chapter: Chapter): Result<Path> = runCatching { downloads.download(chapter) }

	fun deleteDownload(chapter: Chapter) {
		downloads.delete(chapter)
	}

	/**
	 * Persists the reading position. The page currently on screen counts as read, matching the
	 * Android app's semantics, so an imported backup resumes at the same place.
	 */
	fun saveProgress(
		manga: Manga,
		chapter: Chapter,
		chapterIndex: Int,
		page: Int,
		pagesCount: Int,
		scroll: Int = 0,
	) {
		val chaptersCount = library.chaptersOf(manga.id).size.coerceAtLeast(1)
		val percent = ReadingProgress.percentOf(chapterIndex, chaptersCount, page, pagesCount)
		val now = System.currentTimeMillis()
		val previous = activity.historyFor(manga.id)
		activity.putHistory(
			HistoryEntry(
				mangaId = manga.id,
				chapterId = chapter.chapterId,
				page = page,
				scroll = scroll,
				percent = percent,
				createdAt = previous?.createdAt?.takeIf { it > 0 } ?: now,
				updatedAt = now,
				chaptersCount = chaptersCount,
			),
		)
		val isLastPage = pagesCount > 0 && page >= pagesCount - 1
		library.setChapterRead(manga.id, chapter.chapterId, isLastPage)
	}

	fun markChapterRead(manga: Manga, chapter: Chapter, read: Boolean) {
		library.setChapterRead(manga.id, chapter.chapterId, read)
	}

	/** Where the reader should start when the user hits "Continue reading". */
	fun resumePosition(mangaId: Long): Pair<Int, Int>? {
		val chapters = library.chaptersOf(mangaId)
		if (chapters.isEmpty()) return null
		val history = activity.historyFor(mangaId) ?: return 0 to 0
		val index = chapters.indexOfFirst { it.chapterId == history.chapterId }
		if (index < 0) {
			return ReadingProgress.resumePosition(history.percent, chapters.size, 0, 1)
		}
		val content = downloads.contentOf(chapters[index])
		val page = history.page.coerceIn(0, (content.pageCount - 1).coerceAtLeast(0))
		return index to page
	}

	// --- History ------------------------------------------------------------------------------

	fun historyItems(limit: Int = 100): List<HistoryItem> = activity.history()
		.asSequence()
		.mapNotNull { entry ->
			val manga = library.getManga(entry.mangaId) ?: return@mapNotNull null
			if (manga.source != SOURCE_LOCAL) return@mapNotNull null
			val chapters = library.chaptersOf(manga.id)
			HistoryItem(
				manga = manga,
				entry = entry,
				chapter = chapters.firstOrNull { it.chapterId == entry.chapterId },
				chaptersCount = chapters.size,
			)
		}
		.take(limit)
		.toList()

	fun clearHistory() = activity.clearHistory()

	fun removeHistory(mangaId: Long) = activity.removeHistory(mangaId)

	// --- Downloads ----------------------------------------------------------------------------

	fun downloadsByManga(): List<Pair<Manga, List<Chapter>>> = library.allManga()
		.asSequence()
		.filter { it.source == SOURCE_LOCAL }
		.mapNotNull { manga ->
			val chapters = library.chaptersOf(manga.id).filter { it.isDownloaded }
			if (chapters.isEmpty()) null else manga to chapters
		}
		.sortedBy { it.first.title.lowercase() }
		.toList()

	fun downloadsSize(): Long = downloads.totalSize()

	// --- Backup -------------------------------------------------------------------------------

	fun exportBackup(target: Path): Path = backupService.export(target)

	fun importBackup(source: Path): BackupReport = backupService.import(source)

	companion object {
		private const val TAG = "Repository"
	}
}
