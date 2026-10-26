/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.data.local

import org.koitharu.kotatsu.desktop.data.model.Chapter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Where a single reader page comes from. */
sealed class PageSource {

	abstract val name: String

	data class ArchiveEntry(val archive: Path, val entryName: String) : PageSource() {
		override val name: String get() = entryName
	}

	data class FilePage(val file: Path) : PageSource() {
		override val name: String get() = file.fileName.toString()
	}
}

/**
 * A resolved, ordered page list for one chapter. [pages] is a snapshot taken when the chapter was
 * opened; this keeps the reader stable if the underlying archive is replaced while reading.
 */
class ChapterContent(
	val chapterId: Long,
	val pages: List<PageSource>,
) {

	val pageCount: Int get() = pages.size

	fun readPage(index: Int): ByteArray? {
		val page = pages.getOrNull(index) ?: return null
		return try {
			when (page) {
				is PageSource.ArchiveEntry -> ArchiveReader.readEntry(page.archive, page.entryName)
				is PageSource.FilePage -> Files.readAllBytes(page.file)
			}
		} catch (e: IOException) {
			null
		}
	}

	fun pageName(index: Int): String = pages.getOrNull(index)?.name ?: ""

	companion object {

		val EMPTY = ChapterContent(0L, emptyList())

		/**
		 * Builds the content for a chapter, preferring the offline copy when one exists.
		 *
		 * @param offlineFile an already downloaded CBZ for this chapter, if any.
		 */
		fun load(chapter: Chapter, offlineFile: Path? = null): ChapterContent {
			val location = offlineFile ?: runCatching { Paths.get(chapter.url) }.getOrNull() ?: return EMPTY
			return when {
				Files.isDirectory(location) -> ChapterContent(
					chapterId = chapter.chapterId,
					pages = ArchiveReader.listImageFiles(location).map(PageSource::FilePage),
				)

				Files.isRegularFile(location) && ArchiveReader.isArchive(location) -> ChapterContent(
					chapterId = chapter.chapterId,
					pages = ArchiveReader.listImageEntries(location).map { PageSource.ArchiveEntry(location, it) },
				)

				else -> EMPTY
			}
		}
	}
}
