/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * CBZ/ZIP support (the Android app uses its own `core/zip` package plus the Storage Access
 * Framework; here we read archives straight from the file system with java.util.zip).
 */
package org.koitharu.kotatsu.desktop.data.local

import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.core.util.NaturalOrder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object ArchiveReader {

	private const val TAG = "Archive"

	/** Image extensions that may appear in a CBZ. */
	val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "avif", "jxl")

	/**
	 * Extensions the JVM's ImageIO can always decode. WebP/AVIF/JXL need native decoders (the
	 * Android app bundles Coil plugins for them) and are listed as a known limitation.
	 */
	val DECODABLE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp")

	val ARCHIVE_EXTENSIONS = setOf("cbz", "zip")

	fun isArchive(path: Path): Boolean = extensionOf(path) in ARCHIVE_EXTENSIONS

	fun isImage(path: Path): Boolean = extensionOf(path) in IMAGE_EXTENSIONS

	fun isDecodable(path: Path): Boolean = extensionOf(path) in DECODABLE_EXTENSIONS

	fun isImageEntry(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in IMAGE_EXTENSIONS

	private fun extensionOf(path: Path): String =
		path.fileName?.toString()?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()

	/** Image entry names of an archive, in natural (human) order. */
	@Throws(IOException::class)
	fun listImageEntries(archive: Path): List<String> {
		ZipFile(archive.toFile()).use { zip ->
			val names = zip.entries().asSequence()
				.filter { !it.isDirectory }
				.map(ZipEntry::getName)
				.filter(::isInterestingEntry)
				.toList()
			return names.sortedWith { a, b -> NaturalOrder.compare(a, b) }
		}
	}

	/** Pages stored in a plain directory chapter, in natural order. */
	fun listImageFiles(directory: Path): List<Path> {
		if (!Files.isDirectory(directory)) return emptyList()
		Files.list(directory).use { stream ->
			return stream.filter { Files.isRegularFile(it) && isImage(it) }
				.sorted { a, b -> NaturalOrder.compare(a.fileName.toString(), b.fileName.toString()) }
				.toList()
		}
	}

	@Throws(IOException::class)
	fun readEntry(archive: Path, entryName: String): ByteArray {
		ZipFile(archive.toFile()).use { zip ->
			val entry = zip.getEntry(entryName) ?: throw IOException("Entry not found: $entryName")
			zip.getInputStream(entry).use { input -> return input.readBytes() }
		}
	}

	/** Extracts a set of entries into [target] (used when creating offline copies). */
	@Throws(IOException::class)
	fun extract(archive: Path, entries: List<String>, targetDirectory: Path) {
		Files.createDirectories(targetDirectory)
		ZipFile(archive.toFile()).use { zip ->
			for (name in entries) {
				val entry = zip.getEntry(name) ?: continue
				val target = targetDirectory.resolve(name.substringAfterLast('/'))
				zip.getInputStream(entry).use { input ->
					Files.newOutputStream(target).use { output -> input.copyTo(output) }
				}
			}
		}
	}

	fun zipSize(archive: Path): Long = runCatching { Files.size(archive) }.getOrDefault(0L)

	private fun isInterestingEntry(name: String): Boolean {
		if (!isImageEntry(name)) return false
		val normalized = name.replace('\\', '/')
		if (normalized.startsWith("__MACOSX/")) return false
		val fileName = normalized.substringAfterLast('/')
		if (fileName.startsWith(".")) return false
		// Thumbnail caches produced by Windows/macOS inside a folder-shaped "archive"
		if (normalized.contains("/.thumbnails/")) return false
		return true
	}

	/** Logs and swallows archive problems so the UI can show a friendly message. */
	fun tryListImageEntries(archive: Path): List<String>? = try {
		listImageEntries(archive)
	} catch (e: Exception) {
		Log.e(TAG, "Cannot list entries of $archive", e)
		null
	}
}
