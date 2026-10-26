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
import org.koitharu.kotatsu.desktop.data.model.Tag
import org.koitharu.kotatsu.desktop.data.model.PROGRESS_NONE
import org.koitharu.kotatsu.desktop.data.model.SOURCE_LOCAL
import org.koitharu.kotatsu.desktop.data.store.ActivityStore
import org.koitharu.kotatsu.desktop.data.store.LibraryStore
import org.koitharu.kotatsu.desktop.data.store.SettingsStore
import org.koitharu.kotatsu.desktop.data.store.SourcesStore
import org.koitharu.kotatsu.desktop.reader.ReadingProgress
import org.koitharu.kotatsu.desktop.sources.ChapterKind
import org.koitharu.kotatsu.desktop.sources.MangaSource
import org.koitharu.kotatsu.desktop.sources.RemoteChapter
import org.koitharu.kotatsu.desktop.sources.RemoteContentService
import org.koitharu.kotatsu.desktop.sources.RemoteManga
import org.koitharu.kotatsu.desktop.sources.SearchFilters
import org.koitharu.kotatsu.desktop.sources.SourceEntry
import org.koitharu.kotatsu.desktop.sources.SourceException
import org.koitharu.kotatsu.desktop.sources.SourceFailure
import org.koitharu.kotatsu.desktop.sources.SourceOutcome
import org.koitharu.kotatsu.desktop.sources.SourceRegistry
import org.koitharu.kotatsu.desktop.sources.net.HttpRequestSpec
import org.koitharu.kotatsu.desktop.sources.net.HttpTransport
import org.koitharu.kotatsu.desktop.sources.net.JdkHttpTransport
import org.koitharu.kotatsu.desktop.data.model.stableId
import java.nio.file.Files
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

/** Outcome of a search that fans out over several sources. */
data class RemoteSearchResult(
	val items: List<RemoteManga>,
	val failures: List<Pair<String, SourceFailure>> = emptyList(),
) {
	val isEmpty: Boolean get() = items.isEmpty()
}

class AppRepository(
	val settings: SettingsStore,
	val library: LibraryStore,
	val activity: ActivityStore,
	/** `sources.json`; created next to the other stores when the caller does not pass one. */
	sourcesStore: SourcesStore? = null,
	/** Injected by the tests so that no test ever touches the real network. */
	transport: HttpTransport? = null,
	private val sourceTimeoutMs: Long = HttpRequestSpec.DEFAULT_TIMEOUT_MS,
) {

	val sourceStore: SourcesStore = sourcesStore ?: SourcesStore(AppDirs.dataDir.resolve("sources.json"))
	private val http: HttpTransport = transport ?: JdkHttpTransport()
	val registry: SourceRegistry = SourceRegistry(sourceStore, http, sourceTimeoutMs)

	val downloads = DownloadService(library)
	val importer = LocalLibraryImporter(library)
	val backupService = BackupService(library, activity, settings, sourceStore)
	private val remoteContent = RemoteContentService(
		transport = http,
		cacheDir = AppDirs.cacheDir.resolve("remote"),
		timeoutMs = sourceTimeoutMs,
	)

	fun init() {
		AppDirs.ensureCreated()
		settings.load()
		library.load()
		activity.load()
		sourceStore.load()
		registry.rebuild()
		val fixed = downloads.reconcile()
		if (fixed > 0) Log.i(TAG, "Reconciled $fixed offline chapter flags")
	}

	fun flush() {
		settings.flush()
		library.flush()
		activity.flush()
		sourceStore.flush()
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

	/** Titles that came from an Android online source this build cannot open yet. */
	fun unsupportedMangaCount(): Int = library.allManga().count { manga ->
		manga.source != SOURCE_LOCAL && registry.get(manga.source) == null
	}

	/** Titles from online sources that this build *can* open. */
	fun onlineMangaCount(): Int = library.allManga().count { manga ->
		manga.source != SOURCE_LOCAL && registry.get(manga.source) != null
	}

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
			isReadable = when {
				manga.source != SOURCE_LOCAL -> registry.get(manga.source) != null
				else -> chapters.isNotEmpty() || runCatching {
					ArchiveReader.isArchive(Paths.get(manga.url)) || Files.isDirectory(Paths.get(manga.url))
				}.getOrDefault(false)
			},
		)
	}

	fun details(mangaId: Long): MangaWithChapters? = library.getWithChapters(mangaId)

	fun searchAll(query: String): List<LibraryItem> = libraryItems(query = query, filter = LibraryFilter.ALL)

	/** Refreshes chapters of every local title by re-scanning its path. */
	fun refreshLibrary(): ImportResult {
		val paths = library.allManga()
			.filter { it.source == SOURCE_LOCAL }
			.mapNotNull { runCatching { Paths.get(it.url) }.getOrNull() }
			.filter { Files.exists(it) }
		return importer.importPaths(paths)
	}

	fun importPaths(paths: List<Path>): ImportResult = importer.importPaths(paths)

	fun removeManga(mangaId: Long) {
		library.chaptersOf(mangaId).forEach { remoteContent.clearChapter(it.chapterId) }
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
		if (manga.source != SOURCE_LOCAL) {
			val url = manga.coverUrl ?: manga.largeCoverUrl ?: return null
			return CoverCache.getOrCreate(manga.id) {
				val result = runCatching {
					http.execute(HttpRequestSpec(url = url, timeoutMs = sourceTimeoutMs, headers = mapOf("Accept" to "image/*")))
				}.getOrNull()
				val bytes = result?.takeIf { it.isSuccessful && it.body.isNotEmpty() }?.body
					?: return@getOrCreate null
				ImageDecoder.decode(bytes)
			}
		}
		val chapter = library.chaptersOf(manga.id).firstOrNull() ?: return null
		return CoverCache.getOrCreate(manga.id) {
			val content = downloads.contentOf(chapter)
			if (content.pageCount == 0) return@getOrCreate null
			val bytes = content.readPage(0) ?: return@getOrCreate null
			ImageDecoder.decode(bytes)
		}
	}

	// --- Reader -------------------------------------------------------------------------------

	fun chapterContent(chapter: Chapter): ChapterContent =
		chapterContentResult(chapter).getOrNull() ?: ChapterContent.EMPTY

	/**
	 * Resolves the pages of a chapter, online or local. The reader uses this variant so that a
	 * network failure is shown as a message instead of an empty book.
	 */
	fun chapterContentResult(chapter: Chapter): SourceOutcome<ChapterContent> {
		if (chapter.source == SOURCE_LOCAL) {
			return SourceOutcome.Success(downloads.contentOf(chapter))
		}
		val offline = downloads.offlineFile(chapter)
		if (offline != null) {
			return SourceOutcome.Success(ChapterContent.fromArchive(chapter.chapterId, offline))
		}
		val source = registry.get(chapter.source)
			?: return SourceOutcome.Failure(SourceFailure.unavailable("source ${chapter.source} is not installed"))
		if (chapter.remoteKind == ChapterKind.ARCHIVE.name) {
			return remoteContent.archiveContent(chapter, null)
		}
		return when (val pages = source.pages(chapter.toRemoteChapter())) {
			is SourceOutcome.Success -> SourceOutcome.Success(
				remoteContent.pageContent(source, chapter, pages.value),
			)

			is SourceOutcome.Failure -> pages
		}
	}

	fun downloadChapter(chapter: Chapter): Result<Path> {
		if (chapter.source == SOURCE_LOCAL) {
			return runCatching { downloads.download(chapter) }
		}
		val source = registry.get(chapter.source)
			?: return Result.failure(
				SourceFailure.unavailable("source ${chapter.source} is not installed").asException(),
			)
		val target = downloads.chapterTarget(chapter)
		val outcome = if (chapter.remoteKind == ChapterKind.ARCHIVE.name) {
			remoteContent.downloadArchive(chapter.url, target)
		} else {
			when (val pages = source.pages(chapter.toRemoteChapter())) {
				is SourceOutcome.Success -> remoteContent.downloadPages(
					source = source,
					pages = pages.value,
					chapterId = chapter.chapterId,
					target = target,
				)

				is SourceOutcome.Failure -> pages
			}
		}
		return when (outcome) {
			is SourceOutcome.Success -> {
				library.setChapterDownloaded(chapter.chapterId, true)
				Result.success(outcome.value)
			}

			is SourceOutcome.Failure -> Result.failure(outcome.failure.asException())
		}
	}

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
			if (manga.source != SOURCE_LOCAL && registry.get(manga.source) == null) return@mapNotNull null
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
		.mapNotNull { manga ->
			val chapters = library.chaptersOf(manga.id).filter { it.isDownloaded }
			if (chapters.isEmpty()) null else manga to chapters
		}
		.sortedBy { it.first.title.lowercase() }
		.toList()

	fun downloadsSize(): Long = downloads.totalSize()

	// --- Backup -------------------------------------------------------------------------------

	fun exportBackup(target: Path): Path = backupService.export(target)

	fun importBackup(source: Path): BackupReport = backupService.import(source).also {
		// A restored backup can add OPDS catalogues and enable sources, so rebuild the catalogue.
		registry.rebuild()
	}

	// --- Online sources -------------------------------------------------------------------------

	fun sourceCatalog(): List<SourceEntry> = registry.catalog()

	/** Calls the source that owns [manga], or null when it is not installed in this build. */
	fun sourceFor(manga: Manga): MangaSource? = registry.get(manga.source)

	fun browseSource(sourceId: String, page: Int = 0): SourceOutcome<List<RemoteManga>> {
		val source = registry.get(sourceId)
			?: return SourceOutcome.Failure(SourceFailure.unavailable("source '$sourceId' is not installed"))
		if (!registry.isEnabled(source.descriptor)) {
			return SourceOutcome.Failure(SourceFailure.unavailable("source '$sourceId' is disabled"))
		}
		return source.popular(page)
	}

	/**
	 * Searches every enabled source (or only [sourceIds], when the user unchecked some of them).
	 * Sources are queried one after the other so that the per-host rate limits are respected; a
	 * failing source does not hide the results of the others, it is reported separately.
	 */
	fun searchRemote(
		query: String,
		page: Int = 0,
		filters: SearchFilters = SearchFilters(),
		sourceIds: List<String>? = null,
	): RemoteSearchResult {
		val targets = if (sourceIds == null) {
			registry.enabledSources()
		} else {
			sourceIds.mapNotNull { registry.get(it) }.filter { registry.isEnabled(it.descriptor) }
		}
		val items = ArrayList<RemoteManga>()
		val failures = ArrayList<Pair<String, SourceFailure>>()
		targets.forEach { source ->
			val outcome = if (query.isBlank()) source.popular(page) else source.search(query, page, filters)
			when (outcome) {
				is SourceOutcome.Success -> items += outcome.value
				is SourceOutcome.Failure -> {
					failures += source.descriptor.title to outcome.failure
					Log.w(TAG, "Search on ${source.descriptor.id} failed: ${outcome.failure.kind}")
				}
			}
		}
		return RemoteSearchResult(items, failures)
	}

	fun sourceForId(sourceId: String): MangaSource? = registry.get(sourceId)

	/** The library entry that already represents [remote], if the user added it before. */
	fun findInLibrary(remote: RemoteManga): Manga? {
		val id = mangaIdFor(remote)
		return library.getManga(id) ?: library.allManga().firstOrNull {
			it.source == remote.sourceId && it.remoteId == remote.remoteId
		}
	}

	/**
	 * Adds a search result to the library (fetching its chapter list) or refreshes it when it is
	 * already there. Existing read/offline flags are preserved by [LibraryStore.upsert].
	 */
	fun addFromSource(remote: RemoteManga): SourceOutcome<Manga> {
		val source = registry.get(remote.sourceId)
			?: return SourceOutcome.Failure(SourceFailure.unavailable("source '${remote.sourceId}' is not installed"))
		return when (val details = source.details(remote.remoteId)) {
			is SourceOutcome.Failure -> details
			is SourceOutcome.Success -> {
				val value = details.value
				val mangaId = mangaIdFor(remote)
				val existing = library.getManga(mangaId) ?: findInLibrary(remote)
				val manga = mangaFromRemote(value.manga, existing)
				val chapters = value.chapters.mapIndexed { index, chapter ->
					chapterFromRemote(mangaId, manga.source, chapter, index)
				}
				library.upsert(manga, chapters)
				Log.i(TAG, "Added ${manga.title} (${chapters.size} chapters) from ${manga.source}")
				SourceOutcome.Success(manga)
			}
		}
	}

	/** Re-fetches the chapter list of an online title. Returns the number of chapters seen. */
	fun refreshRemote(mangaId: Long): SourceOutcome<Int> {
		val manga = library.getManga(mangaId)
			?: return SourceOutcome.Failure(SourceFailure.unavailable("title $mangaId is not in the library"))
		val source = registry.get(manga.source)
			?: return SourceOutcome.Failure(SourceFailure.unavailable("source '${manga.source}' is not installed"))
		val remoteId = manga.remoteId
			?: return SourceOutcome.Failure(SourceFailure.invalidResponse("title has no remote id"))
		return when (val details = source.details(remoteId)) {
			is SourceOutcome.Failure -> details
			is SourceOutcome.Success -> {
				val value = details.value
				val updated = mangaFromRemote(value.manga, manga)
				val chapters = value.chapters.mapIndexed { index, chapter ->
					chapterFromRemote(mangaId, manga.source, chapter, index)
				}
				library.upsert(updated, chapters)
				SourceOutcome.Success(chapters.size)
			}
		}
	}

	/** Downloads an image without touching the library (used for Hub thumbnails). */
	fun fetchRemoteImage(url: String): ByteArray? {
		val result = runCatching {
			http.execute(
				HttpRequestSpec(
					url = url,
					timeoutMs = sourceTimeoutMs,
					headers = mapOf("Accept" to "image/*"),
				),
			)
		}.getOrNull() ?: return null
		return result.body.takeIf { result.isSuccessful && it.isNotEmpty() }
	}

	/** Number of pages of a chapter that are already cached on disk (for the reader status line). */
	fun cachedPageCount(chapter: Chapter): Int = remoteContent.cachedPageCount(chapter.chapterId)

	fun pageCacheSize(): Long = remoteContent.pageCacheSize()

	fun clearPageCache() {
		remoteContent.clearAll()
		org.koitharu.kotatsu.desktop.data.local.PageImageCache.clear()
	}

	private fun mangaIdFor(remote: RemoteManga): Long = stableId("remote", remote.sourceId, remote.remoteId)

	private fun mangaFromRemote(remote: RemoteManga, existing: Manga?): Manga {
		val now = System.currentTimeMillis()
		val mangaId = existing?.id ?: mangaIdFor(remote)
		val tags = remote.tags.map { name ->
			Tag(
				id = stableId("tag", remote.sourceId, name.lowercase()),
				title = name,
				key = name.lowercase(),
				source = remote.sourceId,
			)
		}
		return Manga(
			id = mangaId,
			title = remote.title,
			altTitles = remote.altTitles.takeIf { it.isNotEmpty() }?.joinToString(", "),
			url = remote.url,
			publicUrl = remote.url,
			rating = remote.rating,
			isNsfw = remote.isNsfw,
			contentRating = remote.contentRating,
			coverUrl = remote.coverUrl,
			largeCoverUrl = remote.coverUrl,
			state = remote.state,
			authors = remote.authors.takeIf { it.isNotEmpty() }?.joinToString(", "),
			tags = tags,
			source = remote.sourceId,
			createdAt = existing?.createdAt?.takeIf { it > 0 } ?: now,
			updatedAt = now,
			remoteId = remote.remoteId,
		)
	}

	private fun chapterFromRemote(
		mangaId: Long,
		sourceId: String,
		chapter: RemoteChapter,
		index: Int,
	): Chapter = Chapter(
		chapterId = stableId("chapter", sourceId, mangaId.toString(), chapter.remoteId),
		mangaId = mangaId,
		title = chapter.title,
		number = chapter.number,
		volume = chapter.volume,
		url = chapter.url,
		scanlator = chapter.scanlator,
		uploadDate = chapter.uploadedAt,
		branch = null,
		source = sourceId,
		index = index,
		pagesCount = chapter.pagesCount,
		isDownloaded = false,
		isRead = false,
		remoteId = chapter.remoteId,
		remoteKind = chapter.kind.name,
	)

	companion object {
		private const val TAG = "Repository"
	}
}

/** Rebuilds the [RemoteChapter] a stored [Chapter] points at. */
fun Chapter.toRemoteChapter(): RemoteChapter = RemoteChapter(
	remoteId = remoteId ?: url,
	title = title,
	url = url,
	number = number,
	volume = volume,
	uploadedAt = uploadDate,
	pagesCount = pagesCount,
	scanlator = scanlator,
	kind = if (remoteKind == ChapterKind.ARCHIVE.name) ChapterKind.ARCHIVE else ChapterKind.PAGES,
)

/** Converts a failure into an exception whose message is the translated user-facing text. */
fun SourceFailure.asException(): SourceException = SourceException(this)
