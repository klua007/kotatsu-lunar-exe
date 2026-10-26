/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * The Android-shaped fixture in `androidStyleBackup` reproduces exactly what
 * `BackupRepository.createBackup()` writes (ZIP entries whose payload is a JSON array), so these
 * tests fail if the desktop importer drifts away from the upstream format.
 */
package org.koitharu.kotatsu.desktop.test

import org.koitharu.kotatsu.desktop.data.backup.BackupSection
import org.koitharu.kotatsu.desktop.data.backup.BackupService
import org.koitharu.kotatsu.desktop.data.model.Chapter
import org.koitharu.kotatsu.desktop.data.model.HistoryEntry
import org.koitharu.kotatsu.desktop.data.model.Manga
import org.koitharu.kotatsu.desktop.data.model.ReaderMode
import org.koitharu.kotatsu.desktop.data.model.SOURCE_LOCAL
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
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object BackupTests {

	fun run() {
		roundTripTests()
		androidImportTests()
		mergeSafetyTests()
	}

	private class Stores(val settings: SettingsStore, val library: LibraryStore, val activity: ActivityStore) {
		val service = BackupService(library, activity, settings)

		fun flush() {
			settings.flush()
			library.flush()
			activity.flush()
		}
	}

	private fun newStores(dir: Path): Stores {
		val settings = SettingsStore(dir.resolve("settings.json"))
		val library = LibraryStore(dir.resolve("library.json"))
		val activity = ActivityStore(dir.resolve("activity.json"))
		settings.load()
		library.load()
		activity.load()
		return Stores(settings, library, activity)
	}

	private fun writeZip(target: Path, entries: Map<String, String>) {
		Files.createDirectories(target.parent)
		Files.newOutputStream(target).use { output ->
			ZipOutputStream(output).use { zip ->
				entries.forEach { (name, payload) ->
					zip.putNextEntry(ZipEntry(name))
					zip.write(payload.toByteArray())
					zip.closeEntry()
				}
			}
		}
	}

	private fun roundTripTests() = TestRunner.suite("Backup round trip") {
		TestRunner.test("exports every section the Android app expects") {
			val stores = newStores(Fixtures.tempDir())
			val manga = Manga(id = 1L, title = "Local Title", url = "C:/m/one.cbz", source = SOURCE_LOCAL, createdAt = 10L)
			stores.library.upsert(
				manga,
				listOf(Chapter(chapterId = 2L, mangaId = 1L, title = "Chapter 1", url = "C:/m/one.cbz")),
			)
			stores.activity.toggleFavourite(1L)
			stores.activity.putHistory(HistoryEntry(mangaId = 1L, chapterId = 2L, page = 4, percent = 0.5f, updatedAt = 20L))
			val target = Fixtures.tempDir().resolve("backup.zip")
			stores.service.export(target)

			ZipFile(target.toFile()).use { zip ->
				listOf("index", "history", "categories", "favourites", "bookmarks", "settings", "sources")
					.forEach { name ->
						assertNotNull("entry $name", zip.getEntry(name))
					}
			}
		}

		TestRunner.test("exported arrays parse as JSON arrays") {
			val stores = newStores(Fixtures.tempDir())
			stores.library.upsert(Manga(id = 1L, title = "T", url = "u", source = SOURCE_LOCAL), emptyList())
			stores.activity.toggleFavourite(1L)
			val target = Fixtures.tempDir().resolve("backup.zip")
			stores.service.export(target)
			ZipFile(target.toFile()).use { zip ->
				val payload = zip.getInputStream(zip.getEntry("favourites")).use { it.readBytes().decodeToString() }
				val parsed = org.koitharu.kotatsu.desktop.core.json.Json.parse(payload)
				assertTrue("array wrapped", parsed is org.koitharu.kotatsu.desktop.core.json.JsonValue.Arr)
				assertEquals("one favourite", 1, (parsed as org.koitharu.kotatsu.desktop.core.json.JsonValue.Arr).size)
			}
		}

		TestRunner.test("restores favourites, history and settings into a fresh profile") {
			val source = newStores(Fixtures.tempDir())
			source.library.upsert(
				Manga(id = 1L, title = "T", url = "C:/t.cbz", source = SOURCE_LOCAL, createdAt = 1L),
				listOf(Chapter(chapterId = 2L, mangaId = 1L, title = "C1", url = "C:/t.cbz")),
			)
			source.activity.toggleFavourite(1L)
			source.activity.putHistory(HistoryEntry(mangaId = 1L, chapterId = 2L, page = 3, percent = 0.7f, updatedAt = 5L))
			source.settings.defaultReaderMode = ReaderMode.VERTICAL
			val target = Fixtures.tempDir().resolve("backup.zip")
			source.service.export(target)
			source.flush()

			val destination = newStores(Fixtures.tempDir())
			val report = destination.service.import(target)
			assertEquals("favourites", 1, report.favourites)
			assertEquals("history", 1, report.history)
			assertEquals("manga added", 1, report.mangaAdded)
			assertEquals("settings applied at least 1", true, report.settingsApplied >= 1)
			assertEquals("reader mode restored", ReaderMode.VERTICAL, destination.settings.defaultReaderMode)
			assertTrue("favourite present", destination.activity.isFavourite(1L))
			assertEquals("page restored", 3, destination.activity.historyFor(1L)?.page)
			assertNotNull("manga restored", destination.library.getManga(1L))
		}

		TestRunner.test("importing a non-zip file fails cleanly") {
			val stores = newStores(Fixtures.tempDir())
			val file = Fixtures.tempDir().resolve("not-a-backup.zip")
			Files.write(file, "hello".toByteArray())
			TestRunner.assertThrows("io error", IOException::class.java) { stores.service.import(file) }
		}
	}

	private fun androidMangaJson(mangaId: Long, source: String, title: String): String = """
		{"id":$mangaId,"title":"$title","alt_title":null,"url":"https://example.org/manga/$mangaId",
		 "public_url":"https://example.org/manga/$mangaId","rating":0.75,"nsfw":false,
		 "content_rating":null,"cover_url":"https://example.org/c.jpg","large_cover_url":null,
		 "state":null,"author":"Some Author","source":"$source",
		 "tags":[{"id":9,"title":"Action","key":"action","source":"$source","pinned":false}]}
	""".trimIndent()

	/** Mirrors what the Android app writes, including sections the desktop build cannot use. */
	private fun androidStyleBackup(target: Path) {
		val manga = androidMangaJson(111L, "MANGAUPDATES", "Remote Title")
		writeZip(
			target,
			linkedMapOf(
				"index" to """[{"app_id":"io.github.kotatsuredo.kotatsu","app_version":2047,"created_at":1700000000000}]""",
				"categories" to """[
					{"category_id":0,"created_at":1,"sort_key":0,"title":"Default","order":"NEWEST","track":true,"show_in_lib":true},
					{"category_id":1,"created_at":2,"sort_key":1,"title":"Reading","order":"ALPHABETIC","track":false,"show_in_lib":true}
				]""",
				"favourites" to """[
					{"manga_id":111,"category_id":1,"sort_key":0,"pinned":true,"created_at":3,"manga":$manga}
				]""",
				"history" to """[
					{"manga_id":111,"created_at":4,"updated_at":9,"chapter_id":222,"page":3,"scroll":0,
					 "percent":0.42,"chapters":10,"manga":$manga}
				]""",
				"bookmarks" to """[
					{"manga":$manga,"tags":[{"id":9,"title":"Action","key":"action","source":"MANGAUPDATES","pinned":false}],
					 "bookmarks":[{"manga_id":111,"page_id":333,"chapter_id":222,"page":3,"scroll":0,
					   "image_url":"https://example.org/p.jpg","created_at":7,"percent":0.3}]}
				]""",
				"settings" to """[{"reader_mode":"WEBTOON","app_theme":"DARK","app_password":"secret",
					"android_only_pref":"kept","zoom":1.25}]""",
				"sources" to """[{"source":"MANGAUPDATES","sort_key":0,"used_at":1,"added_in":1,"pinned":false,"enabled":true}]""",
				"reader_grid" to """[{"grid_size":9}]""",
				"statistics" to """[{"manga_id":111,"chapters":10,"read_chapters":2}]""",
				"scrobbling" to """[]""",
			),
		)
	}

	private fun androidImportTests() = TestRunner.suite("Android backup import") {
		TestRunner.test("reads app id, counts and section payloads") {
			val backup = Fixtures.tempDir().resolve("android.zip")
			androidStyleBackup(backup)
			val stores = newStores(Fixtures.tempDir())
			val report = stores.service.import(backup)
			assertEquals("app id", "io.github.kotatsuredo.kotatsu", report.backupAppId)
			assertEquals("app version", 2047, report.backupAppVersion)
			assertEquals("favourites", 1, report.favourites)
			assertEquals("history", 1, report.history)
			assertEquals("categories", 2, report.categories)
			assertEquals("bookmarks", 1, report.bookmarks)
			assertEquals("sources", 1, report.sources)
			assertTrue("settings applied", report.settingsApplied >= 2)
			assertEquals("manga added", 1, report.mangaAdded)
			assertEquals("unsupported sources", 1, report.mangaFromUnsupportedSources)
			assertTrue("statistics preserved", report.preservedSections.contains("statistics"))
			assertTrue("reader grid preserved", report.preservedSections.contains("reader_grid"))
		}

		TestRunner.test("maps values onto desktop preferences") {
			val backup = Fixtures.tempDir().resolve("android.zip")
			androidStyleBackup(backup)
			val stores = newStores(Fixtures.tempDir())
			stores.service.import(backup)
			assertEquals("reader mode", ReaderMode.WEBTOON, stores.settings.defaultReaderMode)
			assertEquals("theme", "DARK", stores.settings.snapshot()["app_theme"]?.let { (it as? org.koitharu.kotatsu.desktop.core.json.JsonValue.Str)?.value })
			assertEquals("unknown key kept", "kept", (stores.settings.snapshot()["android_only_pref"] as? org.koitharu.kotatsu.desktop.core.json.JsonValue.Str)?.value)
			assertEquals("password not imported", null, stores.settings.snapshot()["app_password"])
			assertEquals("imported sources kept", true, stores.settings.snapshot().containsKey("imported_sources"))
		}

		TestRunner.test("keeps remote manga readable only as metadata") {
			val backup = Fixtures.tempDir().resolve("android.zip")
			androidStyleBackup(backup)
			val stores = newStores(Fixtures.tempDir())
			stores.service.import(backup)
			val manga = assertNotNull("manga", stores.library.getManga(111L))
			assertEquals("source kept", "MANGAUPDATES", manga.source)
			assertEquals("title", "Remote Title", manga.title)
			assertEquals("no chapters", 0, stores.library.chaptersOf(111L).size)
			assertEquals("tags kept", 1, manga.tags.size)
		}

		TestRunner.test("import is idempotent") {
			val backup = Fixtures.tempDir().resolve("android.zip")
			androidStyleBackup(backup)
			val stores = newStores(Fixtures.tempDir())
			stores.service.import(backup)
			val second = stores.service.import(backup)
			assertEquals("no duplicate favourites", 0, second.favourites)
			assertEquals("no duplicate bookmarks counted as new", 1, second.bookmarks)
			assertEquals("bookmarks not duplicated", 1, stores.activity.bookmarks().size)
			assertEquals("single manga", 1, stores.library.mangaCount())
		}

		TestRunner.test("tolerates an unknown section without failing") {
			val backup = Fixtures.tempDir().resolve("odd.zip")
			writeZip(
				backup,
				mapOf(
					"index" to """[{"app_id":"x","app_version":1,"created_at":0}]""",
					"future_section" to """[{"a":1}]""",
				),
			)
			val stores = newStores(Fixtures.tempDir())
			val report = stores.service.import(backup)
			assertEquals("skipped unknown", 1, report.skipped)
		}
	}

	private fun mergeSafetyTests() = TestRunner.suite("Backup merge safety") {
		TestRunner.test("existing local data is never deleted") {
			val backup = Fixtures.tempDir().resolve("android.zip")
			androidStyleBackup(backup)
			val stores = newStores(Fixtures.tempDir())
			stores.library.upsert(
				Manga(id = 500L, title = "Local", url = "C:/local.cbz", source = SOURCE_LOCAL),
				listOf(Chapter(chapterId = 501L, mangaId = 500L, title = "C1", url = "C:/local.cbz")),
			)
			stores.activity.toggleFavourite(500L)
			stores.service.import(backup)
			assertNotNull("local manga kept", stores.library.getManga(500L))
			assertTrue("local favourite kept", stores.activity.isFavourite(500L))
			assertEquals("local chapters kept", 1, stores.library.chaptersOf(500L).size)
		}

		TestRunner.test("a newer local reading position wins over the backup") {
			val backup = Fixtures.tempDir().resolve("android.zip")
			androidStyleBackup(backup)
			val stores = newStores(Fixtures.tempDir())
			stores.library.insertIfAbsent(
				Manga(id = 111L, title = "Remote Title", url = "https://example.org/manga/111", source = "MANGAUPDATES"),
				emptyList(),
			)
			stores.activity.putHistory(
				HistoryEntry(mangaId = 111L, chapterId = 999L, page = 40, percent = 0.9f, updatedAt = 9_999_999_999L),
			)
			stores.service.import(backup)
			assertEquals("local page kept", 40, stores.activity.historyFor(111L)?.page)
			assertEquals("local chapter kept", 999L, stores.activity.historyFor(111L)?.chapterId)
		}

		TestRunner.test("section names match the Android implementation") {
			assertEquals("index", "index", BackupSection.INDEX.entryName)
			assertEquals("reader grid", "reader_grid", BackupSection.SETTINGS_READER_GRID.entryName)
			assertEquals("statistics", "statistics", BackupSection.STATS.entryName)
			assertEquals("lookup", BackupSection.SAVED_FILTERS, BackupSection.of("saved_filters"))
			assertEquals("unknown", null, BackupSection.of("nope"))
		}
	}
}
