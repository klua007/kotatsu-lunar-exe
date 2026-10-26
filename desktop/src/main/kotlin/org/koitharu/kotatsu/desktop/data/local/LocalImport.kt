/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Import of user-provided content. The Android app receives CBZ/ZIP files through the Storage
 * Access Framework; the desktop build scans normal Windows paths.
 *
 * Supported layouts (the heuristics are documented in docs/USER_GUIDE.md):
 *
 *  1. `Manga.cbz`                      -> one manga, one chapter
 *  2. `Some folder/` with images       -> one manga, one chapter
 *  3. `Some folder/` with sub-folders  -> one manga per sub-folder (a "library folder"),
 *                                         unless every sub-folder is named like a chapter,
 *                                         in which case the folder itself is the manga
 *  4. `Some folder/` with only archives-> one manga per archive
 *  5. `Manga/` with chapter sub-folders and/or CBZ files -> one manga with many chapters
 */
package org.koitharu.kotatsu.desktop.data.local

import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.core.util.NaturalOrder
import org.koitharu.kotatsu.desktop.core.util.parseChapterNumber
import org.koitharu.kotatsu.desktop.data.model.Chapter
import org.koitharu.kotatsu.desktop.data.model.Manga
import org.koitharu.kotatsu.desktop.data.model.SOURCE_LOCAL
import org.koitharu.kotatsu.desktop.data.model.Tag
import org.koitharu.kotatsu.desktop.data.model.stableId
import org.koitharu.kotatsu.desktop.data.store.LibraryStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipFile

data class ImportResult(
	val importedManga: Int = 0,
	val updatedManga: Int = 0,
	val importedChapters: Int = 0,
	val skipped: Int = 0,
	val messages: List<String> = emptyList(),
) {
	operator fun plus(other: ImportResult) = ImportResult(
		importedManga = importedManga + other.importedManga,
		updatedManga = updatedManga + other.updatedManga,
		importedChapters = importedChapters + other.importedChapters,
		skipped = skipped + other.skipped,
		messages = messages + other.messages,
	)
}

data class ScannedChapter(
	val title: String,
	val number: Float,
	val location: Path,
	val pageCount: Int,
	val uploadDate: Long,
)

data class ScannedManga(
	val title: String,
	val root: Path,
	val chapters: List<ScannedChapter>,
	val authors: String? = null,
	val tags: List<String> = emptyList(),
)

class LocalLibraryImporter(private val library: LibraryStore) {

	private companion object {
		const val TAG = "Import"
	}

	/**
	 * Imports the given paths. Nothing is deleted: paths that were imported before are re-scanned
	 * and refreshed (a chapter list change keeps reading progress, because chapter ids are stable).
	 */
	fun importPaths(paths: List<Path>): ImportResult {
		var result = ImportResult()
		for (path in paths) {
			result += try {
				importSinglePath(path)
			} catch (e: Exception) {
				Log.e(TAG, "Cannot import $path", e)
				ImportResult(skipped = 1, messages = listOf("$path: ${e.message ?: e.javaClass.simpleName}"))
			}
		}
		return result
	}

	private fun importSinglePath(path: Path): ImportResult {
		if (!Files.exists(path)) {
			return ImportResult(skipped = 1, messages = listOf("$path: not found"))
		}
		val scanned = scan(path)
		if (scanned.isEmpty()) {
			return ImportResult(skipped = 1, messages = listOf("$path: no images or archives found"))
		}
		var imported = 0
		var updated = 0
		var chapters = 0
		val messages = mutableListOf<String>()
		for (manga in scanned) {
			if (manga.chapters.isEmpty()) {
				messages.add("${manga.title}: no readable chapters")
				continue
			}
			val existsBefore = library.findMangaByUrl(manga.root.toString()) != null
			val entity = toMangaEntity(manga)
			val chapterEntities = toChapterEntities(manga, entity.id)
			library.upsert(entity, chapterEntities)
			chapters += chapterEntities.size
			if (existsBefore) updated++ else imported++
		}
		return ImportResult(
			importedManga = imported,
			updatedManga = updated,
			importedChapters = chapters,
			messages = messages,
		)
	}

	fun scan(path: Path): List<ScannedManga> = when {
		Files.isRegularFile(path) && ArchiveReader.isArchive(path) -> listOf(scanArchiveAsManga(path))
		Files.isRegularFile(path) -> emptyList()
		Files.isDirectory(path) -> scanDirectory(path)
		else -> emptyList()
	}

	private fun scanArchiveAsManga(archive: Path): ScannedManga {
		val title = cleanTitle(archive.fileName.toString().substringBeforeLast('.'))
		val pages = ArchiveReader.tryListImageEntries(archive)?.size ?: 0
		val metadata = ComicInfoMetadata.read(archive)
		return ScannedManga(
			title = metadata?.series ?: title,
			root = archive.toAbsolutePath().normalize(),
			chapters = listOf(
				ScannedChapter(
					title = metadata?.title ?: title,
					number = 1f,
					location = archive.toAbsolutePath().normalize(),
					pageCount = pages,
					uploadDate = lastModified(archive),
				),
			),
			authors = metadata?.writer,
			tags = metadata?.genres.orEmpty(),
		)
	}

	private fun scanDirectory(directory: Path): List<ScannedManga> {
		val directImages = ArchiveReader.listImageFiles(directory)
		if (directImages.isNotEmpty()) {
			return listOf(
				ScannedManga(
					title = cleanTitle(directory.fileName?.toString() ?: "Local"),
					root = directory.toAbsolutePath().normalize(),
					chapters = listOf(
						ScannedChapter(
							title = cleanTitle(directory.fileName?.toString() ?: "Chapter 1"),
							number = 1f,
							location = directory.toAbsolutePath().normalize(),
							pageCount = directImages.size,
							uploadDate = lastModified(directory),
						),
					),
				),
			)
		}
		val children = listChildren(directory)
		val subdirectories = children.filter { Files.isDirectory(it) }
		val archives = children.filter { Files.isRegularFile(it) && ArchiveReader.isArchive(it) }

		// A folder whose sub-folders are all named like chapters is a single manga.
		val chapterFolders = subdirectories.isNotEmpty() && subdirectories.all { isChapterLikeName(it.fileName.toString()) }
		val mixedSeriesFolder = subdirectories.isNotEmpty() && archives.isNotEmpty() && !chapterFolders

		if (chapterFolders || mixedSeriesFolder) {
			val chapters = mutableListOf<ScannedChapter>()
			subdirectories.forEach { dir -> chapters += chaptersOfDirectory(dir) }
			archives.forEach { archive -> chapters += chapterOfArchive(archive) }
			if (chapters.isNotEmpty()) {
				val metadata = archives.firstOrNull()?.let(ComicInfoMetadata::read)
				return listOf(
					ScannedManga(
						title = metadata?.series ?: cleanTitle(directory.fileName?.toString() ?: "Local"),
						root = directory.toAbsolutePath().normalize(),
						chapters = chapters.sortedWith { a, b -> NaturalOrder.compare(a.title, b.title) },
						authors = metadata?.writer,
						tags = metadata?.genres.orEmpty(),
					),
				)
			}
		}

		if (archives.isNotEmpty() && subdirectories.isEmpty()) {
			// A flat folder of archives: each archive is its own manga.
			return archives.map(::scanArchiveAsManga)
		}

		// Otherwise each sub-folder is its own manga.
		val result = mutableListOf<ScannedManga>()
		for (dir in subdirectories) {
			val images = ArchiveReader.listImageFiles(dir)
			if (images.isNotEmpty()) {
				result += ScannedManga(
					title = cleanTitle(dir.fileName?.toString() ?: "Local"),
					root = dir.toAbsolutePath().normalize(),
					chapters = listOf(
						ScannedChapter(
							title = cleanTitle(dir.fileName?.toString() ?: "Chapter 1"),
							number = 1f,
							location = dir.toAbsolutePath().normalize(),
							pageCount = images.size,
							uploadDate = lastModified(dir),
						),
					),
				)
			} else {
				val chapters = chaptersOfDirectory(dir)
				if (chapters.isNotEmpty()) {
					result += ScannedManga(
						title = cleanTitle(dir.fileName?.toString() ?: "Local"),
						root = dir.toAbsolutePath().normalize(),
						chapters = chapters,
					)
				}
			}
		}
		return result
	}

	/** Chapters inside [directory]: either its archives, or one level of sub-folders. */
	private fun chaptersOfDirectory(directory: Path): List<ScannedChapter> {
		val images = ArchiveReader.listImageFiles(directory)
		if (images.isNotEmpty()) {
			return listOf(chapterOfFolder(directory, images.size))
		}
		val children = listChildren(directory)
		val chapters = mutableListOf<ScannedChapter>()
		children.filter { Files.isRegularFile(it) && ArchiveReader.isArchive(it) }
			.forEach { chapters += chapterOfArchive(it) }
		children.filter { Files.isDirectory(it) }.forEach { sub ->
			val subImages = ArchiveReader.listImageFiles(sub)
			if (subImages.isNotEmpty()) {
				chapters += chapterOfFolder(sub, subImages.size)
			} else {
				listChildren(sub).filter { Files.isRegularFile(it) && ArchiveReader.isArchive(it) }
					.forEach { chapters += chapterOfArchive(it) }
			}
		}
		return chapters.sortedWith { a, b -> NaturalOrder.compare(a.title, b.title) }
	}

	private fun chapterOfFolder(folder: Path, pageCount: Int): ScannedChapter {
		val name = cleanTitle(folder.fileName?.toString() ?: "Chapter")
		return ScannedChapter(
			title = name,
			number = parseChapterNumber(name).takeIf { it > 0f } ?: 0f,
			location = folder.toAbsolutePath().normalize(),
			pageCount = pageCount,
			uploadDate = lastModified(folder),
		)
	}

	private fun chapterOfArchive(archive: Path): ScannedChapter {
		val name = cleanTitle(archive.fileName.toString().substringBeforeLast('.'))
		val pages = ArchiveReader.tryListImageEntries(archive)?.size ?: 0
		return ScannedChapter(
			title = name,
			number = parseChapterNumber(name),
			location = archive.toAbsolutePath().normalize(),
			pageCount = pages,
			uploadDate = lastModified(archive),
		)
	}

	private fun listChildren(directory: Path): List<Path> = runCatching {
		Files.list(directory).use { stream ->
			stream.filter { child ->
				val name = child.fileName.toString()
				!name.startsWith(".") && name.lowercase(Locale.ROOT) != "thumbs.db"
			}.sorted { a, b -> NaturalOrder.compare(a.fileName.toString(), b.fileName.toString()) }
				.toList()
		}
	}.getOrElse {
		Log.w(TAG, "Cannot list $directory", it)
		emptyList()
	}

	private fun toMangaEntity(scanned: ScannedManga): Manga {
		val root = scanned.root
		val existing = library.findMangaByUrl(root.toString())
		val now = System.currentTimeMillis()
		val id = existing?.id ?: stableId(SOURCE_LOCAL, root.toString().lowercase(Locale.ROOT))
		return Manga(
			id = id,
			title = scanned.title,
			url = root.toString(),
			publicUrl = root.toUri().toString(),
			authors = scanned.authors ?: existing?.authors,
			tags = scanned.tags.map { tag ->
				Tag(
					id = stableId(SOURCE_LOCAL, tag.lowercase(Locale.ROOT)),
					title = tag,
					key = tag.lowercase(Locale.ROOT),
				)
			},
			source = SOURCE_LOCAL,
			createdAt = existing?.createdAt ?: now,
			updatedAt = now,
		)
	}

	private fun toChapterEntities(scanned: ScannedManga, mangaId: Long): List<Chapter> =
		scanned.chapters.mapIndexed { index, chapter ->
			Chapter(
				chapterId = stableId(mangaId.toString(), chapter.location.toString().lowercase(Locale.ROOT)),
				mangaId = mangaId,
				title = chapter.title,
				number = chapter.number,
				url = chapter.location.toString(),
				uploadDate = chapter.uploadDate,
				source = SOURCE_LOCAL,
				index = index,
				pagesCount = chapter.pageCount,
			)
		}

	private fun lastModified(path: Path): Long = runCatching { Files.getLastModifiedTime(path).toMillis() }
		.getOrDefault(0L)
}

/** Cleans file-system names into something readable: `My_Manga [Group] v2` -> `My Manga`. */
internal fun cleanTitle(raw: String): String = raw
	.replace('_', ' ')
	.replace(Regex("""\s*\[[^\]]*]"""), "")
	.replace(Regex("""\s{2,}"""), " ")
	.trim()
	.ifEmpty { raw.trim() }

internal fun isChapterLikeName(name: String): Boolean {
	val normalized = name.trim().lowercase(Locale.ROOT)
	if (normalized.isEmpty()) return false
	if (Regex("""^(\d{1,4})(?:[.,]\d{1,2})?$""").matches(normalized)) return true
	return Regex("""^(ch|chap|chapter|cap|capitulo|capítulo|vol|volume|v|ep|episode|aula)\b""")
		.containsMatchIn(normalized)
}

/**
 * Minimal ComicInfo.xml reader (the de-facto metadata file inside CBZ archives). A tolerant regex
 * parse is enough here and avoids adding an XML dependency.
 */
internal class ComicInfoMetadata(
	val series: String?,
	val title: String?,
	val writer: String?,
	val genres: List<String>,
) {

	companion object {

		fun read(archive: Path): ComicInfoMetadata? = runCatching {
			ZipFile(archive.toFile()).use { zip ->
				val entry = zip.getEntry("ComicInfo.xml") ?: zip.entries().asSequence()
					.firstOrNull { it.name.equals("ComicInfo.xml", ignoreCase = true) }
					?: return null
				val text = zip.getInputStream(entry).use { it.readBytes().decodeToString() }
				ComicInfoMetadata(
					series = tag(text, "Series"),
					title = tag(text, "Title"),
					writer = tag(text, "Writer"),
					genres = tag(text, "Genre")?.split(',', '/')
						?.map(::cleanTitle)
						?.filter { it.isNotBlank() }
						.orEmpty(),
				)
			}
		}.getOrNull()

		private fun tag(xml: String, name: String): String? =
			Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL)
				.find(xml)
				?.groupValues
				?.get(1)
				?.replace("&amp;", "&")
				?.replace("&lt;", "<")
				?.replace("&gt;", ">")
				?.trim()
				?.takeIf { it.isNotEmpty() }
	}
}
