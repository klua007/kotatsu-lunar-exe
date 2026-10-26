/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.test

import org.koitharu.kotatsu.desktop.data.model.Bookmark
import org.koitharu.kotatsu.desktop.data.model.Chapter
import org.koitharu.kotatsu.desktop.data.model.Favourite
import org.koitharu.kotatsu.desktop.data.model.FavouriteCategory
import org.koitharu.kotatsu.desktop.data.model.HistoryEntry
import org.koitharu.kotatsu.desktop.data.model.Manga
import org.koitharu.kotatsu.desktop.data.model.ReaderMode
import org.koitharu.kotatsu.desktop.data.model.SOURCE_LOCAL
import org.koitharu.kotatsu.desktop.data.store.ActivityStore
import org.koitharu.kotatsu.desktop.data.store.LibraryStore
import org.koitharu.kotatsu.desktop.data.store.ReaderBackground
import org.koitharu.kotatsu.desktop.data.store.SettingsStore
import org.koitharu.kotatsu.desktop.data.store.ThemeMode
import org.koitharu.kotatsu.desktop.test.TestRunner.assertEquals
import org.koitharu.kotatsu.desktop.test.TestRunner.assertNotNull
import org.koitharu.kotatsu.desktop.test.TestRunner.assertTrue
import java.nio.file.Files
import java.nio.file.Path

object StorageTests {

	fun run() {
		settingsTests()
		libraryTests()
		activityTests()
	}

	private fun settingsTests() = TestRunner.suite("Settings store") {
		TestRunner.test("persists values across instances") {
			val file = Fixtures.tempDir().resolve("settings.json")
			val first = SettingsStore(file)
			first.load()
			first.theme = ThemeMode.LIGHT
			first.defaultReaderMode = ReaderMode.WEBTOON
			first.readerPageGap = 24
			first.readerBackground = ReaderBackground.GRAY
			first.flush()

			val second = SettingsStore(file)
			second.load()
			assertEquals("theme", ThemeMode.LIGHT, second.theme)
			assertEquals("mode", ReaderMode.WEBTOON, second.defaultReaderMode)
			assertEquals("gap", 24, second.readerPageGap)
			assertEquals("background", ReaderBackground.GRAY, second.readerBackground)
		}

		TestRunner.test("falls back to defaults for a missing or broken file") {
			val dir = Fixtures.tempDir()
			val missing = SettingsStore(dir.resolve("nothing.json"))
			missing.load()
			assertEquals("default theme", ThemeMode.DARK, missing.theme)
			assertEquals("default mode", ReaderMode.STANDARD, missing.defaultReaderMode)

			val brokenFile = dir.resolve("broken.json")
			Files.write(brokenFile, "{ not json".toByteArray())
			val broken = SettingsStore(brokenFile)
			broken.load()
			assertEquals("default theme after corruption", ThemeMode.DARK, broken.theme)
			val backups = Files.list(dir).use { stream ->
				stream.filter { it.fileName.toString().contains(".corrupt-") }.count()
			}
			assertEquals("corrupted file preserved", 1L, backups)
		}

		TestRunner.test("import keeps unknown keys and drops secrets") {
			val file = Fixtures.tempDir().resolve("settings.json")
			val store = SettingsStore(file)
			store.load()
			val imported = mapOf(
				"reader_mode" to org.koitharu.kotatsu.desktop.core.json.JsonValue.Str("VERTICAL"),
				"app_password" to org.koitharu.kotatsu.desktop.core.json.JsonValue.Str("1234"),
				"some_android_only_pref" to org.koitharu.kotatsu.desktop.core.json.JsonValue.Str("keep-me"),
			)
			val applied = store.applyImported(imported, SettingsStore.KNOWN_KEYS)
			assertEquals("known keys applied", 1, applied)
			assertEquals("known value", ReaderMode.VERTICAL, store.defaultReaderMode)
			assertEquals("android key preserved", "keep-me", store.snapshot()["some_android_only_pref"]?.asString())
			assertEquals("password dropped", null, store.snapshot()["app_password"])
		}
	}

	private fun sampleManga(id: Long = 1L, url: String = "C:/manga/a.cbz") = Manga(
		id = id,
		title = "Sample",
		url = url,
		source = SOURCE_LOCAL,
		createdAt = 100L,
	)

	private fun sampleChapters(mangaId: Long = 1L) = listOf(
		Chapter(chapterId = 10L, mangaId = mangaId, title = "Chapter 1", url = "C:/manga/a.cbz", index = 0),
		Chapter(chapterId = 11L, mangaId = mangaId, title = "Chapter 2", url = "C:/manga/a.cbz", index = 1),
	)

	private fun libraryTests() = TestRunner.suite("Library store") {
		TestRunner.test("persists manga and chapters") {
			val file = Fixtures.tempDir().resolve("library.json")
			val first = LibraryStore(file)
			first.load()
			first.upsert(sampleManga(), sampleChapters())
			first.flush()

			val second = LibraryStore(file)
			second.load()
			assertEquals("manga count", 1, second.mangaCount())
			assertEquals("chapters", 2, second.chaptersOf(1L).size)
			assertEquals("chapter order", "Chapter 2", second.chaptersOf(1L)[1].title)
		}

		TestRunner.test("upsert preserves read and download flags") {
			val store = LibraryStore(Fixtures.tempDir().resolve("library.json"))
			store.load()
			store.upsert(sampleManga(), sampleChapters())
			store.setChapterDownloaded(10L, true)
			store.setChapterRead(1L, 10L, true)
			store.upsert(sampleManga().copy(title = "Renamed"), sampleChapters())
			val chapter = store.chaptersOf(1L).first { it.chapterId == 10L }
			assertEquals("download flag", true, chapter.isDownloaded)
			assertEquals("read flag", true, chapter.isRead)
			assertEquals("title updated", "Renamed", store.getManga(1L)?.title)
		}

		TestRunner.test("insertIfAbsent does not overwrite") {
			val store = LibraryStore(Fixtures.tempDir().resolve("library.json"))
			store.load()
			assertTrue("first insert", store.insertIfAbsent(sampleManga(), emptyList()))
			assertTrue("second insert skipped", !store.insertIfAbsent(sampleManga().copy(title = "Other"), emptyList()))
			assertEquals("title untouched", "Sample", store.getManga(1L)?.title)
		}

		TestRunner.test("remove drops chapters too") {
			val store = LibraryStore(Fixtures.tempDir().resolve("library.json"))
			store.load()
			store.upsert(sampleManga(), sampleChapters())
			store.removeManga(1L)
			assertEquals("no manga", 0, store.mangaCount())
			assertEquals("no chapters", 0, store.chaptersOf(1L).size)
		}

		TestRunner.test("corrupted library file is preserved and starts empty") {
			val file: Path = Fixtures.tempDir().resolve("library.json")
			Files.write(file, "not json at all".toByteArray())
			val store = LibraryStore(file)
			store.load()
			assertEquals("empty", 0, store.mangaCount())
			val backups = Files.list(file.parent).use { stream ->
				stream.filter { it.fileName.toString().startsWith("library.json.corrupt-") }.count()
			}
			assertEquals("corrupted copy kept", 1L, backups)
		}
	}

	private fun activityTests() = TestRunner.suite("Activity store") {
		TestRunner.test("favourites toggle") {
			val store = ActivityStore(Fixtures.tempDir().resolve("activity.json"))
			store.load()
			assertTrue("added", store.toggleFavourite(5L))
			assertTrue("is favourite", store.isFavourite(5L))
			assertTrue("removed", !store.toggleFavourite(5L))
			assertTrue("not favourite", !store.isFavourite(5L))
		}

		TestRunner.test("history keeps one row per manga and merges newer positions") {
			val store = ActivityStore(Fixtures.tempDir().resolve("activity.json"))
			store.load()
			store.putHistory(HistoryEntry(mangaId = 1L, chapterId = 10L, page = 2, updatedAt = 100L, percent = 0.2f))
			store.putHistory(HistoryEntry(mangaId = 1L, chapterId = 11L, page = 5, updatedAt = 200L, percent = 0.6f))
			assertEquals("single row", 1, store.history().size)
			assertEquals("latest page", 5, store.historyFor(1L)?.page)

			val older = HistoryEntry(mangaId = 1L, chapterId = 10L, page = 0, updatedAt = 50L, percent = 0.1f)
			assertTrue("older rejected", !store.mergeHistory(older))
			assertEquals("page kept", 5, store.historyFor(1L)?.page)

			val newer = HistoryEntry(mangaId = 1L, chapterId = 11L, page = 9, updatedAt = 300L, percent = 0.9f)
			assertTrue("newer accepted", store.mergeHistory(newer))
			assertEquals("page updated", 9, store.historyFor(1L)?.page)
		}

		TestRunner.test("default category always exists") {
			val store = ActivityStore(Fixtures.tempDir().resolve("activity.json"))
			store.load()
			assertNotNull("default category", store.getCategory(FavouriteCategory.DEFAULT_CATEGORY_ID))
		}

		TestRunner.test("removing a category moves favourites to default") {
			val store = ActivityStore(Fixtures.tempDir().resolve("activity.json"))
			store.load()
			store.putCategory(FavouriteCategory(categoryId = 3, title = "Custom"))
			store.putFavourite(Favourite(mangaId = 1L, categoryId = 3))
			store.removeCategory(3)
			assertEquals("moved", FavouriteCategory.DEFAULT_CATEGORY_ID, store.favouriteOf(1L)?.categoryId)
		}

		TestRunner.test("bookmarks are deduplicated by page id") {
			val store = ActivityStore(Fixtures.tempDir().resolve("activity.json"))
			store.load()
			store.addBookmark(Bookmark(mangaId = 1L, pageId = 99L, chapterId = 10L, page = 3))
			store.addBookmark(Bookmark(mangaId = 1L, pageId = 99L, chapterId = 10L, page = 4))
			assertEquals("one bookmark", 1, store.bookmarks().size)
			assertEquals("updated page", 4, store.bookmarks().first().page)
			store.removeBookmark(1L, 99L)
			assertEquals("removed", 0, store.bookmarks().size)
		}

		TestRunner.test("clearing history keeps favourites") {
			val store = ActivityStore(Fixtures.tempDir().resolve("activity.json"))
			store.load()
			store.putHistory(HistoryEntry(mangaId = 1L, chapterId = 1L, updatedAt = 1L))
			store.toggleFavourite(1L)
			store.clearHistory()
			assertEquals("history cleared", 0, store.history().size)
			assertTrue("favourite kept", store.isFavourite(1L))
		}
	}
}

private fun org.koitharu.kotatsu.desktop.core.json.JsonValue?.asString(): String? =
	(this as? org.koitharu.kotatsu.desktop.core.json.JsonValue.Str)?.value
