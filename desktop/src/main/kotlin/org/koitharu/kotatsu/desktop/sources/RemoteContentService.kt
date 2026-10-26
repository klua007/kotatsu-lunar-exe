/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Turns online chapters into something the reader and the download manager can use:
 *
 *  - PAGES chapters (MangaDex and most sites): every page image is fetched on demand, written to
 *    the page cache once and then read from disk, so paging back is free and works offline until the
 *    cache is cleared.
 *  - ARCHIVE chapters (OPDS acquisition links): the CBZ/ZIP is downloaded once and opened exactly
 *    like a locally imported archive, which is also what "download for offline" stores.
 *
 * Nothing here touches the Swing UI; failures are returned as [SourceOutcome] and rendered by the
 * caller with the translated messages from SourceFailure.
 */
package org.koitharu.kotatsu.desktop.sources

import org.koitharu.kotatsu.desktop.core.AtomicFiles
import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.data.local.ChapterContent
import org.koitharu.kotatsu.desktop.data.local.PageSource
import org.koitharu.kotatsu.desktop.data.model.Chapter
import org.koitharu.kotatsu.desktop.sources.net.HttpRequestSpec
import org.koitharu.kotatsu.desktop.sources.net.HttpTransport
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RemoteContentService(
	private val transport: HttpTransport,
	/** Directory for the page/archive cache, normally `<cache>/remote`. */
	private val cacheDir: Path,
	private val timeoutMs: Long = HttpRequestSpec.DEFAULT_TIMEOUT_MS,
) {

	// --- Reading -------------------------------------------------------------------------------

	/** Builds the reader content of a PAGES chapter from its page URL list. */
	fun pageContent(source: MangaSource, chapter: Chapter, pages: List<RemotePage>): ChapterContent {
		if (pages.isEmpty()) return ChapterContent.EMPTY
		val indexes = HashMap<String, Int>(pages.size)
		val pageSources = pages.mapIndexed { index, page ->
			indexes.putIfAbsent(page.url, index)
			PageSource.Remote(url = page.url, referer = page.referer)
		}
		return ChapterContent(
			chapterId = chapter.chapterId,
			pages = pageSources,
			remoteLoader = { remote ->
				val index = indexes[remote.url] ?: 0
				fetchPage(source, chapter.chapterId, index, RemotePage(remote.url, remote.referer))
			},
		)
	}

	/** Reader content for an ARCHIVE chapter: uses the offline copy when available. */
	fun archiveContent(chapter: Chapter, offlineFile: Path?): SourceOutcome<ChapterContent> {
		offlineFile?.takeIf { Files.isRegularFile(it) }?.let {
			return SourceOutcome.Success(ChapterContent.fromArchive(chapter.chapterId, it))
		}
		val cached = cachedArchive(chapter.chapterId)
		if (Files.isRegularFile(cached)) {
			return SourceOutcome.Success(ChapterContent.fromArchive(chapter.chapterId, cached))
		}
		val downloaded = fetchArchive(chapter.url, chapter.chapterId, cached)
		return when (downloaded) {
			is SourceOutcome.Success -> SourceOutcome.Success(
				ChapterContent.fromArchive(chapter.chapterId, downloaded.value),
			)

			is SourceOutcome.Failure -> downloaded
		}
	}

	/** Fetches one page image, using the on-disk cache when present. */
	fun fetchPage(source: MangaSource, chapterId: Long, index: Int, page: RemotePage): ByteArray? {
		val directory = pageDirectory(chapterId)
		val cached = findCachedPage(directory, index)
		if (cached != null) {
			return runCatching { Files.readAllBytes(cached) }.getOrNull()
		}
		val result = try {
			transport.execute(source.imageRequest(page, timeoutMs))
		} catch (e: SourceException) {
			Log.w(TAG, "Page $index of chapter $chapterId failed: ${e.failure.kind}")
			return null
		} catch (e: Exception) {
			Log.w(TAG, "Page $index of chapter $chapterId failed", e)
			return null
		}
		if (!result.isSuccessful || result.body.isEmpty()) {
			Log.w(TAG, "Page $index of chapter $chapterId: HTTP ${result.code}")
			return null
		}
		val extension = detectExtension(page.url, result.body)
		val target = directory.resolve(String.format("%05d.%s", index, extension))
		runCatching {
			Files.createDirectories(directory)
			AtomicFiles.writeBytes(target, result.body)
		}.onFailure { Log.w(TAG, "Cannot cache page $target", it) }
		return result.body
	}

	/** True when the page is already on disk (used by tests and the reader's status line). */
	fun isPageCached(chapterId: Long, index: Int): Boolean = findCachedPage(pageDirectory(chapterId), index) != null

	fun cachedPageCount(chapterId: Long): Int {
		val directory = pageDirectory(chapterId)
		if (!Files.isDirectory(directory)) return 0
		return runCatching {
			Files.list(directory).use { stream -> stream.filter { Files.isRegularFile(it) }.count().toInt() }
		}.getOrDefault(0)
	}

	fun clearChapter(chapterId: Long) {
		val directory = pageDirectory(chapterId)
		runCatching {
			if (Files.isDirectory(directory)) {
				Files.list(directory).use { stream -> stream.forEach { Files.deleteIfExists(it) } }
				Files.deleteIfExists(directory)
			}
		}
		runCatching { Files.deleteIfExists(cachedArchive(chapterId)) }
	}

	/** Removes every cached page and archive (settings → "clear page cache"). */
	fun clearAll() {
		runCatching {
			if (!Files.isDirectory(cacheDir)) return
			Files.walk(cacheDir).use { stream ->
				stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
			}
		}
	}

	fun pageCacheSize(): Long = runCatching {
		if (!Files.isDirectory(cacheDir)) return 0L
		Files.walk(cacheDir).use { stream -> stream.filter { Files.isRegularFile(it) }.mapToLong { Files.size(it) }.sum() }
	}.getOrDefault(0L)

	// --- Downloads -----------------------------------------------------------------------------

	/**
	 * Downloads a PAGES chapter as a CBZ into [target] (the same location the local downloader
	 * uses), so an online chapter becomes readable offline with no special casing afterwards.
	 */
	fun downloadPages(
		source: MangaSource,
		pages: List<RemotePage>,
		chapterId: Long,
		target: Path,
	): SourceOutcome<Path> = SourceOutcome.catching(url = target.toString()) {
		if (pages.isEmpty()) {
			throw SourceException(SourceFailure.invalidResponse("chapter has no pages"))
		}
		Files.createDirectories(target.parent)
		val temp = target.resolveSibling("${target.fileName}.part")
		var written = 0
		try {
			Files.newOutputStream(temp).use { output ->
				ZipOutputStream(output).use { zip ->
					pages.forEachIndexed { index, page ->
						val bytes = fetchPage(source, chapterId, index, page)
							?: throw SourceException(
								SourceFailure.io("page ${index + 1}/${pages.size} could not be downloaded", page.url),
							)
						val extension = detectExtension(page.url, bytes)
						zip.putNextEntry(ZipEntry(String.format("%05d.%s", index, extension)))
						zip.write(bytes)
						zip.closeEntry()
						written++
					}
				}
			}
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
		} catch (e: Throwable) {
			runCatching { Files.deleteIfExists(temp) }
			throw e
		}
		Log.i(TAG, "Downloaded $written pages of chapter $chapterId to $target")
		target
	}

	/** Downloads an OPDS acquisition archive straight into [target]. */
	fun downloadArchive(url: String, target: Path): SourceOutcome<Path> = SourceOutcome.catching(url = url) {
		Files.createDirectories(target.parent)
		val temp = target.resolveSibling("${target.fileName}.part")
		val result = try {
			transport.execute(HttpRequestSpec(url = url, timeoutMs = timeoutMs, headers = mapOf("Accept" to "*/*")))
		} catch (e: SourceException) {
			throw e
		}
		if (!result.isSuccessful) {
			throw SourceException(SourceFailure.http(result.code, url, result.retryAfterSeconds()))
		}
		if (!isZip(result.body)) {
			throw SourceException(
				SourceFailure.io("not a ZIP/CBZ archive (only zip-based downloads are supported)", url),
			)
		}
		try {
			AtomicFiles.writeBytes(temp, result.body)
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
		} catch (e: IOException) {
			runCatching { Files.deleteIfExists(temp) }
			throw SourceException(SourceFailure.io(e.message ?: "cannot write $target", url))
		}
		Log.i(TAG, "Downloaded archive ${result.body.size} bytes to $target")
		target
	}

	// --- Internals -----------------------------------------------------------------------------

	private fun fetchArchive(url: String, chapterId: Long, target: Path): SourceOutcome<Path> {
		if (!url.startsWith("http")) {
			return SourceOutcome.Failure(SourceFailure.invalidResponse("chapter has no download URL"))
		}
		return downloadArchive(url, target)
	}

	private fun cachedArchive(chapterId: Long): Path = cacheDir.resolve("archives").resolve("$chapterId.cbz")

	private fun pageDirectory(chapterId: Long): Path = cacheDir.resolve("pages").resolve(chapterId.toString())

	private fun findCachedPage(directory: Path, index: Int): Path? {
		if (!Files.isDirectory(directory)) return null
		val prefix = String.format("%05d.", index)
		return runCatching {
			Files.list(directory).use { stream ->
				stream.filter { Files.isRegularFile(it) && it.fileName.toString().startsWith(prefix) }
					.findFirst()
					.orElse(null)
			}
		}.getOrNull()
	}

	companion object {

		private const val TAG = "RemoteContent"

		private val EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "avif")

		/** Extension from the URL, falling back to the magic bytes of the response. */
		fun detectExtension(url: String, bytes: ByteArray): String {
			val fromUrl = url.substringBefore('?')
				.substringBefore('#')
				.substringAfterLast('/')
				.substringAfterLast('.', "")
				.lowercase()
			if (fromUrl in EXTENSIONS) return fromUrl
			return sniffExtension(bytes)
		}

		fun sniffExtension(bytes: ByteArray): String {
			if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "jpg"
			if (bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) return "png"
			if (bytes.size >= 4 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte()) return "gif"
			if (bytes.size >= 12 &&
				bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte()
			) {
				return "webp"
			}
			if (bytes.size >= 12 && bytes[4] == 'f'.code.toByte() && bytes[8] == 'a'.code.toByte()) return "avif"
			if (bytes.size >= 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) return "bmp"
			return "jpg"
		}

		fun isZip(bytes: ByteArray): Boolean =
			bytes.size >= 4 &&
				bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
				(bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())
	}
}
