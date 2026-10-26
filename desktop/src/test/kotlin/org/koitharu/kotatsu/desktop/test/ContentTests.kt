/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.test

import org.koitharu.kotatsu.desktop.core.AppDirs
import org.koitharu.kotatsu.desktop.data.AppRepository
import org.koitharu.kotatsu.desktop.data.local.ArchiveReader
import org.koitharu.kotatsu.desktop.data.local.ChapterContent
import org.koitharu.kotatsu.desktop.data.local.cleanTitle
import org.koitharu.kotatsu.desktop.data.local.isChapterLikeName
import org.koitharu.kotatsu.desktop.data.store.ActivityStore
import org.koitharu.kotatsu.desktop.data.store.LibraryStore
import org.koitharu.kotatsu.desktop.data.store.SettingsStore
import org.koitharu.kotatsu.desktop.test.TestRunner.assertEquals
import org.koitharu.kotatsu.desktop.test.TestRunner.assertNotNull
import org.koitharu.kotatsu.desktop.test.TestRunner.assertTrue
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ContentTests {

	fun run() {
		archiveTests()
		importTests()
		downloadTests()
	}

	private fun createArchive(target: Path, entries: Map<String, ByteArray>) {
		Files.createDirectories(target.parent)
		Files.newOutputStream(target).use { output ->
			ZipOutputStream(output).use { zip ->
				entries.forEach { (name, bytes) ->
					zip.putNextEntry(ZipEntry(name))
					zip.write(bytes)
					zip.closeEntry()
				}
			}
		}
	}

	private fun archiveTests() = TestRunner.suite("CBZ / ZIP reading") {
		TestRunner.test("lists only image entries in natural order") {
			val dir = Fixtures.tempDir()
			val archive = dir.resolve("book.cbz")
			createArchive(
				archive,
				linkedMapOf(
					"page10.png" to Fixtures.pngBytes(),
					"__MACOSX/._page1.png" to Fixtures.pngBytes(),
					"page2.png" to Fixtures.pngBytes(),
					"readme.txt" to "hello".toByteArray(),
					"page1.png" to Fixtures.pngBytes(),
					"Thumbs.db" to byteArrayOf(1, 2, 3),
				),
			)
			val entries = ArchiveReader.listImageEntries(archive)
			assertEquals("names", listOf("page1.png", "page2.png", "page10.png"), entries)
		}

		TestRunner.test("reads a single entry") {
			val dir = Fixtures.tempDir()
			val archive = dir.resolve("book.cbz")
			val bytes = Fixtures.pngBytes()
			createArchive(archive, mapOf("001.png" to bytes))
			val read = ArchiveReader.readEntry(archive, "001.png")
			assertEquals("same size", bytes.size, read.size)
		}

		TestRunner.test("corrupt archive raises an IOException") {
			val dir = Fixtures.tempDir()
			val archive = dir.resolve("broken.cbz")
			Files.write(archive, "not a zip".toByteArray())
			TestRunner.assertThrows("io error", IOException::class.java) { ArchiveReader.listImageEntries(archive) }
			assertEquals("safe variant returns null", null, ArchiveReader.tryListImageEntries(archive))
		}

		TestRunner.test("detects archives by extension") {
			assertTrue("cbz", ArchiveReader.isArchive(java.nio.file.Paths.get("a.cbz")))
			assertTrue("zip", ArchiveReader.isArchive(java.nio.file.Paths.get("a.ZIP")))
			assertTrue("not an archive", !ArchiveReader.isArchive(java.nio.file.Paths.get("a.pdf")))
		}

		TestRunner.test("chapter content exposes ordered pages") {
			val dir = Fixtures.tempDir()
			val archive = dir.resolve("book.cbz")
			createArchive(
				archive,
				mapOf(
					"2.png" to Fixtures.pngBytes(),
					"1.png" to Fixtures.pngBytes(),
					"10.png" to Fixtures.pngBytes(),
					"cover.jpg" to Fixtures.pngBytes(),
				),
			)
			val content = ChapterContent.load(
				org.koitharu.kotatsu.desktop.data.model.Chapter(
					chapterId = 1L,
					mangaId = 1L,
					title = "Chapter 1",
					url = archive.toString(),
				),
			)
			assertEquals("page count", 4, content.pageCount)
			assertEquals("first page", "1.png", content.pageName(0))
			assertEquals("third page", "2.png", content.pageName(1))
			assertNotNull("page bytes", content.readPage(0))
		}

		TestRunner.test("title/name heuristics") {
			assertEquals("cleans names", "My Manga", cleanTitle("My_Manga [Group]"))
			assertTrue("chapter like", isChapterLikeName("Chapter 12"))
			assertTrue("number like", isChapterLikeName("007"))
			assertTrue("volume like", isChapterLikeName("Vol. 2"))
			assertTrue("not chapter like", !isChapterLikeName("One Piece"))
		}
	}

	private fun newRepository(home: Path): AppRepository {
		System.setProperty("kotatsu.home", home.toString())
		val repo = AppRepository(
			SettingsStore(home.resolve("settings.json")),
			LibraryStore(home.resolve("library.json")),
			ActivityStore(home.resolve("activity.json")),
		)
		repo.init()
		return repo
	}

	private fun importTests() = TestRunner.suite("Local import") {
		TestRunner.test("imports a single CBZ as one title with one chapter") {
			Fixtures.useFreshHome()
			val dir = Fixtures.tempDir()
			val archive = dir.resolve("Solo Manga.cbz")
			createArchive(archive, mapOf("01.png" to Fixtures.pngBytes(), "02.png" to Fixtures.pngBytes()))
			val repo = newRepository(Fixtures.tempDir())
			val result = repo.importPaths(listOf(archive))
			assertEquals("imported", 1, result.importedManga)
			val item = assertNotNull("library item", repo.libraryItems().firstOrNull())
			assertEquals("title", "Solo Manga", item.manga.title)
			assertEquals("chapters", 1, repo.library.chaptersOf(item.manga.id).size)
			assertEquals("pages", 2, repo.library.chaptersOf(item.manga.id).first().pagesCount)
		}

		TestRunner.test("imports a folder of images as one chapter") {
			Fixtures.useFreshHome()
			val dir = Fixtures.tempDir().resolve("Chapter Images")
			Fixtures.writeImage(dir.resolve("1.png"))
			Fixtures.writeImage(dir.resolve("2.png"))
			val repo = newRepository(Fixtures.tempDir())
			repo.importPaths(listOf(dir))
			val item = assertNotNull("library item", repo.libraryItems().firstOrNull())
			assertEquals("title", "Chapter Images", item.manga.title)
			assertEquals("chapter count", 1, repo.library.chaptersOf(item.manga.id).size)
		}

		TestRunner.test("imports a library folder with one manga per sub-folder") {
			Fixtures.useFreshHome()
			val root = Fixtures.tempDir().resolve("Library")
			Fixtures.writeImage(root.resolve("Alpha").resolve("1.png"))
			Fixtures.writeImage(root.resolve("Beta").resolve("1.png"))
			val repo = newRepository(Fixtures.tempDir())
			val result = repo.importPaths(listOf(root))
			assertEquals("two titles", 2, result.importedManga)
			assertEquals("library size", 2, repo.libraryItems().size)
		}

		TestRunner.test("imports a series folder with chapter sub-folders") {
			Fixtures.useFreshHome()
			val root = Fixtures.tempDir().resolve("Great Series")
			Fixtures.writeImage(root.resolve("Chapter 1").resolve("1.png"))
			Fixtures.writeImage(root.resolve("Chapter 2").resolve("1.png"))
			Fixtures.writeImage(root.resolve("Chapter 2").resolve("2.png"))
			val repo = newRepository(Fixtures.tempDir())
			repo.importPaths(listOf(root))
			val item = assertNotNull("library item", repo.libraryItems().firstOrNull())
			val chapters = repo.library.chaptersOf(item.manga.id)
			assertEquals("one title", 1, repo.libraryItems().size)
			assertEquals("two chapters", 2, chapters.size)
			assertEquals("chapter order", "Chapter 1", chapters.first().title)
			assertEquals("second chapter pages", 2, chapters[1].pagesCount)
		}

		TestRunner.test("imports a flat folder of archives as separate titles") {
			Fixtures.useFreshHome()
			val root = Fixtures.tempDir().resolve("Archives")
			createArchive(root.resolve("One.cbz"), mapOf("1.png" to Fixtures.pngBytes()))
			createArchive(root.resolve("Two.cbz"), mapOf("1.png" to Fixtures.pngBytes()))
			val repo = newRepository(Fixtures.tempDir())
			repo.importPaths(listOf(root))
			assertEquals("two titles", 2, repo.libraryItems().size)
		}

		TestRunner.test("re-import keeps ids and reading progress") {
			Fixtures.useFreshHome()
			val dir = Fixtures.tempDir()
			val archive = dir.resolve("Repeat.cbz")
			createArchive(archive, mapOf("1.png" to Fixtures.pngBytes(), "2.png" to Fixtures.pngBytes()))
			val repo = newRepository(Fixtures.tempDir())
			repo.importPaths(listOf(archive))
			val manga = assertNotNull("manga", repo.libraryItems().firstOrNull()?.manga)
			val chapter = repo.library.chaptersOf(manga.id).first()
			repo.saveProgress(manga, chapter, 0, 1, 2)

			val second = repo.importPaths(listOf(archive))
			assertEquals("updated instead of added", 1, second.updatedManga)
			assertEquals("still one title", 1, repo.libraryItems().size)
			assertEquals("progress kept", 1, repo.activity.historyFor(manga.id)?.page)
			assertEquals("chapter id stable", chapter.chapterId, repo.library.chaptersOf(manga.id).first().chapterId)
		}

		TestRunner.test("missing path is reported, not thrown") {
			Fixtures.useFreshHome()
			val repo = newRepository(Fixtures.tempDir())
			val result = repo.importPaths(listOf(Fixtures.tempDir().resolve("nope.cbz")))
			assertEquals("skipped", 1, result.skipped)
			assertTrue("has message", result.messages.isNotEmpty())
		}

		TestRunner.test("empty folder is skipped") {
			Fixtures.useFreshHome()
			val repo = newRepository(Fixtures.tempDir())
			val result = repo.importPaths(listOf(Fixtures.tempDir()))
			assertEquals("skipped", 1, result.skipped)
		}
	}

	private fun downloadTests() = TestRunner.suite("Offline downloads") {
		TestRunner.test("downloads an archive chapter as an offline CBZ") {
			Fixtures.useFreshHome()
			val dir = Fixtures.tempDir()
			val archive = dir.resolve("Offline.cbz")
			createArchive(archive, mapOf("1.png" to Fixtures.pngBytes(), "2.png" to Fixtures.pngBytes()))
			val repo = newRepository(Fixtures.tempDir())
			repo.importPaths(listOf(archive))
			val manga = assertNotNull("manga", repo.libraryItems().firstOrNull()?.manga)
			val chapter = repo.library.chaptersOf(manga.id).first()
			val offline = repo.downloadChapter(chapter)
			assertTrue("download ok", offline.isSuccess)
			assertTrue("file exists", Files.isRegularFile(offline.getOrThrow()))
			assertEquals("flag set", true, repo.library.chaptersOf(manga.id).first().isDownloaded)
			assertEquals("pages", 2, ArchiveReader.listImageEntries(offline.getOrThrow()).size)
		}

		TestRunner.test("downloads a folder chapter by packing it into a CBZ") {
			Fixtures.useFreshHome()
			val dir = Fixtures.tempDir().resolve("Packed")
			Fixtures.writeImage(dir.resolve("1.png"))
			Fixtures.writeImage(dir.resolve("2.png"))
			Fixtures.writeImage(dir.resolve("3.png"))
			val repo = newRepository(Fixtures.tempDir())
			repo.importPaths(listOf(dir))
			val manga = assertNotNull("manga", repo.libraryItems().firstOrNull()?.manga)
			val chapter = repo.library.chaptersOf(manga.id).first()
			val offline = repo.downloadChapter(chapter).getOrThrow()
			assertEquals("packed pages", 3, ArchiveReader.listImageEntries(offline).size)
			assertTrue("entry names normalised", ArchiveReader.listImageEntries(offline).first() == "0001.png")
		}

		TestRunner.test("offline copy is used after the source disappears") {
			Fixtures.useFreshHome()
			val dir = Fixtures.tempDir()
			val archive = dir.resolve("Gone.cbz")
			createArchive(archive, mapOf("1.png" to Fixtures.pngBytes()))
			val repo = newRepository(Fixtures.tempDir())
			repo.importPaths(listOf(archive))
			val manga = assertNotNull("manga", repo.libraryItems().firstOrNull()?.manga)
			val chapter = repo.library.chaptersOf(manga.id).first()
			repo.downloadChapter(chapter)
			Files.delete(archive)
			val content = repo.chapterContent(repo.library.chaptersOf(manga.id).first())
			assertEquals("offline pages", 1, content.pageCount)
			assertNotNull("page readable", content.readPage(0))
		}

		TestRunner.test("deleting a download clears the flag") {
			Fixtures.useFreshHome()
			val dir = Fixtures.tempDir()
			val archive = dir.resolve("Delete.cbz")
			createArchive(archive, mapOf("1.png" to Fixtures.pngBytes()))
			val repo = newRepository(Fixtures.tempDir())
			repo.importPaths(listOf(archive))
			val manga = assertNotNull("manga", repo.libraryItems().firstOrNull()?.manga)
			val chapter = repo.library.chaptersOf(manga.id).first()
			repo.downloadChapter(chapter)
			repo.deleteDownload(repo.library.chaptersOf(manga.id).first())
			assertEquals("flag cleared", false, repo.library.chaptersOf(manga.id).first().isDownloaded)
		}

		TestRunner.test("reconcile rebuilds flags from disk") {
			Fixtures.useFreshHome()
			val dir = Fixtures.tempDir()
			val archive = dir.resolve("Reconcile.cbz")
			createArchive(archive, mapOf("1.png" to Fixtures.pngBytes()))
			val home = Fixtures.tempDir()
			val repo = newRepository(home)
			repo.importPaths(listOf(archive))
			val manga = assertNotNull("manga", repo.libraryItems().firstOrNull()?.manga)
			val chapter = repo.library.chaptersOf(manga.id).first()
			val offline = repo.downloadChapter(chapter).getOrThrow()
			repo.library.setChapterDownloaded(chapter.chapterId, false)
			repo.library.flush()
			assertTrue("copy exists", Files.isRegularFile(offline))
			assertEquals("reconciled", 1, repo.downloads.reconcile())
		}
	}

	@Suppress("unused")
	private fun ensureDirectories(): Path = AppDirs.dataDir
}
