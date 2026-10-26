/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: an end-to-end self test that runs without a display, so that the packaged Windows
 * binary can be verified in CI (`kotatsu-lunar.exe --smoke-test`).
 * Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop

import org.koitharu.kotatsu.desktop.data.AppRepository
import org.koitharu.kotatsu.desktop.data.local.ArchiveReader
import org.koitharu.kotatsu.desktop.data.model.ReaderMode
import org.koitharu.kotatsu.desktop.data.store.ActivityStore
import org.koitharu.kotatsu.desktop.data.store.LibraryStore
import org.koitharu.kotatsu.desktop.data.store.SettingsStore
import org.koitharu.kotatsu.desktop.reader.ReadingProgress
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/**
 * Exercises the complete desktop flow with temporary data: create a CBZ, import it, open a chapter,
 * read pages, store progress, export a backup and restore it into a second profile.
 */
object SmokeTest {

	private var failures = 0
	private var checks = 0

	fun run(): Boolean {
		System.setProperty("java.awt.headless", "true")
		val root = Files.createTempDirectory("kotatsu-smoke")
		System.setProperty("kotatsu.home", root.resolve("data").toString())
		println("Smoke test workspace: $root")
		try {
			val archive = createTestArchive(root.resolve("Sample Manga.cbz"), pages = 3)
			check("archive created", Files.isRegularFile(archive))
			check("archive has 3 pages", ArchiveReader.listImageEntries(archive).size == 3)

			val repo = newRepository()
			val importResult = repo.importPaths(listOf(archive))
			check("import added one title", importResult.importedManga == 1)
			val item = repo.libraryItems().firstOrNull()
			check("library contains the title", item != null)
			val manga = item?.manga ?: return fail("no manga")
			val chapters = repo.library.chaptersOf(manga.id)
			check("chapter list has one chapter", chapters.size == 1)
			val chapter = chapters.first()

			val content = repo.chapterContent(chapter)
			check("chapter exposes 3 pages", content.pageCount == 3)
			val pageBytes = content.readPage(0)
			check("first page is readable", pageBytes != null && pageBytes.isNotEmpty())
			val image = pageBytes?.let { javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(it)) }
			check("first page decodes as an image", image != null)

			repo.saveProgress(manga, chapter, 0, 1, content.pageCount)
			val history = repo.activity.historyFor(manga.id)
			check("progress stored", history?.page == 1)
			check("percent is a valid fraction", ReadingProgress.isValid(history?.percent ?: -1f))
			check("resume position matches", repo.resumePosition(manga.id) == (0 to 1))

			// Offline copy (CBZ download)
			val offline = repo.downloadChapter(chapter)
			check("offline copy created", offline.isSuccess)
			check("offline copy reads 3 pages", ArchiveReader.listImageEntries(offline.getOrThrow()).size == 3)

			// Favourite + reader mode persistence
			check("favourite toggled on", repo.toggleFavourite(manga.id))
			repo.settings.defaultReaderMode = ReaderMode.WEBTOON
			repo.flush()

			// Backup round trip
			val backup = repo.exportBackup(root.resolve("backup.zip"))
			check("backup written", Files.isRegularFile(backup))

			System.setProperty("kotatsu.home", root.resolve("data2").toString())
			val second = newRepository()
			val report = second.importBackup(backup)
			check("backup import restored favourites", report.favourites >= 1)
			check("backup import restored history", report.history >= 1)
			second.flush()

			return reportResult()
		} catch (e: Exception) {
			e.printStackTrace()
			failures++
			return false
		} finally {
			runCatching { deleteRecursively(root) }
		}
	}

	private fun newRepository(): AppRepository {
		val home = Paths.get(System.getProperty("kotatsu.home"))
		Files.createDirectories(home)
		val repo = AppRepository(
			settings = SettingsStore(home.resolve("settings.json")),
			library = LibraryStore(home.resolve("library.json")),
			activity = ActivityStore(home.resolve("activity.json")),
		)
		repo.init()
		return repo
	}

	private fun createTestArchive(target: Path, pages: Int): Path {
		Files.createDirectories(target.parent)
		Files.newOutputStream(target).use { output ->
			ZipOutputStream(output).use { zip ->
				for (index in 1..pages) {
					val image = BufferedImage(600, 900, BufferedImage.TYPE_INT_RGB)
					val graphics = image.createGraphics()
					graphics.color = java.awt.Color(index * 40, 60, 90)
					graphics.fillRect(0, 0, image.width, image.height)
					graphics.color = java.awt.Color.WHITE
					graphics.drawString("Page $index", 40, 60)
					graphics.dispose()
					val buffer = java.io.ByteArrayOutputStream()
					ImageIO.write(image, "png", buffer)
					zip.putNextEntry(ZipEntry(String.format("%03d.png", index)))
					zip.write(buffer.toByteArray())
					zip.closeEntry()
				}
			}
		}
		return target
	}

	private fun deleteRecursively(path: Path) {
		if (!Files.exists(path)) return
		Files.walk(path).use { stream ->
			stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
		}
	}

	private fun check(name: String, condition: Boolean) {
		checks++
		if (condition) {
			println("  PASS  $name")
		} else {
			failures++
			println("  FAIL  $name")
		}
	}

	private fun reportResult(): Boolean {
		println("Smoke test: $checks checks, $failures failure(s)")
		return failures == 0
	}

	private fun fail(message: String): Boolean {
		println("  FAIL  $message")
		failures++
		return false
	}
}
