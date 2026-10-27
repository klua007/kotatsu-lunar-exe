/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Offline copies. The Android app downloads chapters as CBZ files or folders and reads them without
 * a network; the desktop build does the same for imported content so that a title keeps working
 * when the original file is on a removable drive, moved, or renamed.
 */
package org.koitharu.kotatsu.desktop.data.downloads

import org.koitharu.kotatsu.desktop.core.AppDirs
import org.koitharu.kotatsu.desktop.core.AtomicFiles
import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.core.util.NaturalOrder
import org.koitharu.kotatsu.desktop.data.local.ArchiveReader
import org.koitharu.kotatsu.desktop.data.local.ChapterContent
import org.koitharu.kotatsu.desktop.data.model.Chapter
import org.koitharu.kotatsu.desktop.data.store.LibraryStore
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DownloadService(private val library: LibraryStore) {

	private companion object {
		const val TAG = "Downloads"
	}

	fun chapterDirectory(mangaId: Long): Path = AppDirs.downloadsDir.resolve(mangaId.toString())

	/** The offline copy of a chapter, or null when it was not downloaded. */
	fun offlineFile(chapter: Chapter): Path? {
		val file = chapterDirectory(chapter.mangaId).resolve("${chapter.chapterId}.cbz")
		return file.takeIf { Files.isRegularFile(it) }
	}

	fun offlineFileForChapterId(mangaId: Long, chapterId: Long): Path? {
		val file = chapterDirectory(mangaId).resolve("$chapterId.cbz")
		return file.takeIf { Files.isRegularFile(it) }
	}

	fun isDownloaded(chapter: Chapter): Boolean = offlineFile(chapter) != null

	/** Where the offline copy of [chapter] lives (created by the online downloader as well). */
	fun chapterTarget(chapter: Chapter): Path =
		chapterDirectory(chapter.mangaId).resolve("${chapter.chapterId}.cbz")

	/**
	 * Creates the offline copy of a chapter.
	 *
	 * A chapter that already is a single archive is copied as-is (fast, lossless); a folder chapter
	 * is packed into a CBZ whose entries keep the natural page order.
	 */
	@Throws(IOException::class)
	fun download(chapter: Chapter): Path {
		val target = chapterDirectory(chapter.mangaId).resolve("${chapter.chapterId}.cbz")
		if (Files.isRegularFile(target)) {
			library.setChapterDownloaded(chapter.chapterId, true)
			return target
		}
		Files.createDirectories(target.parent)
		val source = runCatching { Paths.get(chapter.url) }.getOrNull()
			?: throw IOException("Chapter location is not a local path: ${chapter.url}")
		when {
			Files.isRegularFile(source) && ArchiveReader.isArchive(source) -> {
				val temp = target.resolveSibling("${target.fileName}.tmp")
				Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING)
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
			}

			Files.isDirectory(source) -> writeFolderAsCbz(source, target)

			else -> throw IOException("Nothing to download at ${chapter.url}")
		}
		library.setChapterDownloaded(chapter.chapterId, true)
		return target
	}

	fun delete(chapter: Chapter): Boolean {
		val file = chapterDirectory(chapter.mangaId).resolve("${chapter.chapterId}.cbz")
		val deleted = runCatching { Files.deleteIfExists(file) }.getOrDefault(false)
		library.setChapterDownloaded(chapter.chapterId, false)
		return deleted
	}

	fun deleteForManga(mangaId: Long) {
		val directory = chapterDirectory(mangaId)
		runCatching {
			if (Files.isDirectory(directory)) {
				Files.list(directory).use { stream -> stream.forEach { Files.deleteIfExists(it) } }
				Files.deleteIfExists(directory)
			}
		}
	}

	fun totalSize(): Long = runCatching {
		if (!Files.isDirectory(AppDirs.downloadsDir)) return 0L
		Files.walk(AppDirs.downloadsDir).use { stream ->
			stream.filter { Files.isRegularFile(it) }.mapToLong { Files.size(it) }.sum()
		}
	}.getOrDefault(0L)

	/** Reads a page of a chapter, using the offline copy when available. */
	fun contentOf(chapter: Chapter): ChapterContent = ChapterContent.load(chapter, offlineFile(chapter))

	/** Keeps the store in sync with what is actually on disk (e.g. after a manual deletion). */
	fun reconcile(): Int {
		var fixed = 0
		for (chapter in library.allChapters()) {
			val exists = offlineFile(chapter) != null
			if (exists != chapter.isDownloaded) {
				library.setChapterDownloaded(chapter.chapterId, exists)
				fixed++
			}
		}
		return fixed
	}

	private fun writeFolderAsCbz(directory: Path, target: Path) {
		val images = ArchiveReader.listImageFiles(directory)
			.sortedWith { a, b -> NaturalOrder.compare(a.fileName.toString(), b.fileName.toString()) }
		if (images.isEmpty()) throw IOException("No images in $directory")
		val temp = target.resolveSibling("${target.fileName}.tmp")
		Files.newOutputStream(temp).use { output ->
			ZipOutputStream(output).use { zip ->
				images.forEachIndexed { index, image ->
					val name = image.fileName.toString()
					val extension = name.substringAfterLast('.', "jpg")
					val entryName = String.format("%04d.%s", index + 1, extension)
					zip.putNextEntry(ZipEntry(entryName))
					Files.newInputStream(image).use { it.copyTo(zip) }
					zip.closeEntry()
				}
			}
		}
		AtomicFiles.writeBytes(target, Files.readAllBytes(temp))
		Files.deleteIfExists(temp)
		Log.i(TAG, "Packed ${images.size} pages into $target")
	}
}
