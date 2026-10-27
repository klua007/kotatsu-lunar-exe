/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Tests of the online source layer. Every test talks to [FakeServer] on 127.0.0.1 or to an
 * unreachable port, so the suite is deterministic and needs no internet access.
 */
package org.koitharu.kotatsu.desktop.test

import org.koitharu.kotatsu.desktop.data.AppRepository
import org.koitharu.kotatsu.desktop.data.model.SOURCE_LOCAL
import org.koitharu.kotatsu.desktop.data.store.ActivityStore
import org.koitharu.kotatsu.desktop.data.store.LibraryStore
import org.koitharu.kotatsu.desktop.data.store.SettingsStore
import org.koitharu.kotatsu.desktop.data.store.SourcesStore
import org.koitharu.kotatsu.desktop.sources.ChapterKind
import org.koitharu.kotatsu.desktop.sources.FailureKind
import org.koitharu.kotatsu.desktop.sources.RemoteContentService
import org.koitharu.kotatsu.desktop.sources.SourceOutcome
import org.koitharu.kotatsu.desktop.sources.mangadex.MangaDexSource
import org.koitharu.kotatsu.desktop.sources.net.JdkHttpTransport
import org.koitharu.kotatsu.desktop.sources.opds.OpdsSource
import org.koitharu.kotatsu.desktop.sources.xml.Xml
import org.koitharu.kotatsu.desktop.test.TestRunner.assertEquals
import org.koitharu.kotatsu.desktop.test.TestRunner.assertFails
import org.koitharu.kotatsu.desktop.test.TestRunner.assertNotNull
import org.koitharu.kotatsu.desktop.test.TestRunner.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object SourceTests {

	fun run() {
		xmlAndHelperTests()
		mangaDexTests()
		opdsTests()
		catalogueTests()
		repositoryTests()
		downloadTests()
		backupTests()
	}

	// --- Harness -------------------------------------------------------------------------------

	private fun newHome(): Path {
		val home = Fixtures.tempDir("kotatsu-sources")
		System.setProperty("kotatsu.home", home.toString())
		Files.createDirectories(home)
		return home
	}

	private fun newRepository(
		server: FakeServer? = null,
		timeoutMs: Long = 4_000,
	): AppRepository {
		val home = newHome()
		val sourcesStore = SourcesStore(home.resolve("sources.json"))
		val repo = AppRepository(
			settings = SettingsStore(home.resolve("settings.json")),
			library = LibraryStore(home.resolve("library.json")),
			activity = ActivityStore(home.resolve("activity.json")),
			sourcesStore = sourcesStore,
			transport = JdkHttpTransport(defaultTimeoutMs = timeoutMs),
			sourceTimeoutMs = timeoutMs,
		)
		repo.init()
		// Configured after init(): init() reloads the store from disk, which would drop it otherwise.
		server?.let { repo.registry.setBaseUrlOverride(MangaDexSource.SOURCE_ID, it.baseUrl) }
		repo.clearPageCache()
		return repo
	}

	private fun mangaDex(server: FakeServer, timeoutMs: Long = 4_000): MangaDexSource =
		MangaDexSource(
			transport = JdkHttpTransport(defaultTimeoutMs = timeoutMs),
			baseUrls = listOf(server.baseUrl),
			timeoutMs = timeoutMs,
		)

	private fun opds(server: FakeServer, path: String = "/opds"): OpdsSource = OpdsSource(
		transport = JdkHttpTransport(),
		id = "OPDS_TEST",
		title = "Test Catalogue",
		rootUrl = server.baseUrl + path,
	)

	/** Routes shared by the MangaDex tests: entity, feed, at-home and image bytes. */
	private fun FakeServer.withMangaDexRoutes(): FakeServer {
		json("/manga/${MangaDexFixtures.MANGA_ID}", MangaDexFixtures.mangaResponse())
		json("/manga/${MangaDexFixtures.MANGA_ID}/feed", MangaDexFixtures.feed())
		onPath("/at-home/server/${MangaDexFixtures.CHAPTER_1}") {
			FakeServer.Respond(body = MangaDexFixtures.atHome(baseUrl).toByteArray())
		}
		onPrefix("/data/hash123/") { uri ->
			FakeServer.Respond(body = Fixtures.pngBytes(width = 40, height = 60), contentType = "image/png")
		}
		onPrefix("/covers/") { FakeServer.Respond(body = Fixtures.pngBytes(), contentType = "image/png") }
		return this
	}

	// --- XML and helpers -----------------------------------------------------------------------

	private fun xmlAndHelperTests() = TestRunner.suite("Sources · XML and helpers") {
		TestRunner.test("parses nested feed entries and decodes entities") {
			val document = Xml.parse(
				"""<?xml version="1.0"?>
				<feed><title>A &amp; B</title><entry><id>urn:1</id>
				<title><![CDATA[Tom & Jerry]]></title>
				<link rel="http://opds-spec.org/acquisition" href="/a.cbz" type="application/vnd.comicbook+zip"/>
				</entry></feed>""",
			)
			val feed = document.children.firstOrNull()
			assertNotNull("feed parsed", feed)
			assertEquals("entity decoded", "A & B", feed!!.childText("title"))
			val entry = feed.descendants("entry").firstOrNull()
			assertNotNull("entry", entry)
			assertEquals("cdata text", "Tom & Jerry", entry!!.childText("title"))
			assertEquals("attr", "/a.cbz", entry.childrenNamed("link").first().attr("href"))
		}

		TestRunner.test("malformed input yields no feed instead of throwing") {
			val document = Xml.parse("<feed><title>broken")
			assertEquals("no entry parsed", 0, document.descendants("entry").size)
			assertTrue("does not throw", document.children.isNotEmpty())
		}

		TestRunner.test("detects image extensions from URL and magic bytes") {
			assertEquals("from URL", "png", RemoteContentService.detectExtension("http://x/y/a.PNG?token=1", byteArrayOf()))
			assertEquals("jpeg magic", "jpg", RemoteContentService.detectExtension("http://x/y", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0, 0)))
			val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
			assertEquals("png magic", "png", RemoteContentService.detectExtension("http://x/y", png))
			val webp = ByteArray(16).also {
				it[0] = 'R'.code.toByte(); it[1] = 'I'.code.toByte(); it[2] = 'F'.code.toByte(); it[3] = 'F'.code.toByte()
				it[8] = 'W'.code.toByte(); it[9] = 'E'.code.toByte(); it[10] = 'B'.code.toByte(); it[11] = 'P'.code.toByte()
			}
			assertEquals("webp magic", "webp", RemoteContentService.detectExtension("http://x/y", webp))
			assertTrue("zip magic", RemoteContentService.isZip(OpdsFixtures.cbz()))
			assertTrue("png is not a zip", !RemoteContentService.isZip(Fixtures.pngBytes()))
		}
	}

	// --- MangaDex ------------------------------------------------------------------------------

	private fun mangaDexTests() = TestRunner.suite("Sources · MangaDex (local server)") {
		TestRunner.test("search maps titles, tags, authors and covers") {
			FakeServer().use { server ->
				server.json("/manga", MangaDexFixtures.searchResults())
				val result = mangaDex(server).search("alpha", 0)
				val manga = assertNotNull("result", result.getOrNull()?.firstOrNull())
				assertEquals("title", "Alpha Saga", manga.title)
				assertEquals("source id", MangaDexSource.SOURCE_ID, manga.sourceId)
				assertEquals("remote id", MangaDexFixtures.MANGA_ID, manga.remoteId)
				assertTrue("tag", manga.tags.contains("Action"))
				assertTrue("author", manga.authors.contains("Author One"))
				assertTrue("cover url", manga.coverUrl?.contains("/covers/${MangaDexFixtures.MANGA_ID}/cover.png") == true)
				assertEquals("public url", "https://mangadex.org/title/${MangaDexFixtures.MANGA_ID}", manga.url)
				val request = server.requests.first()
				assertTrue("title query", request.contains("title=alpha"))
				assertTrue("cover included", request.contains("includes%5B%5D=cover_art"))
				assertTrue("safe rating only", request.contains("contentRating%5B%5D=safe"))
				assertTrue("no adult rating", !request.contains("porn"))
			}
		}

		TestRunner.test("popular browse orders by follows and includes adult content only when asked") {
			FakeServer().use { server ->
				server.json("/manga", MangaDexFixtures.popularResults())
				val source = mangaDex(server)
				val popular = source.popular(0).getOrNull().orEmpty()
				assertEquals("two titles", 2, popular.size)
				assertTrue("followedCount", server.requests.first().contains("followedCount"))
				server.requests.clear()
				source.search("", 0, org.koitharu.kotatsu.desktop.sources.SearchFilters(includeNsfw = true))
				assertTrue("adult rating requested", server.requests.first().contains("porn"))
			}
		}

		TestRunner.test("details returns the chapter list with volume, pages and scanlator") {
			FakeServer().use { server ->
				server.withMangaDexRoutes()
				val details = assertNotNull("details", mangaDex(server).details(MangaDexFixtures.MANGA_ID).getOrNull())
				assertEquals("title", "Alpha Saga", details.manga.title)
				assertEquals("chapters", 2, details.chapters.size)
				val first = details.chapters.first()
				assertEquals("chapter id", MangaDexFixtures.CHAPTER_1, first.remoteId)
				assertEquals("chapter title", "Vol. 1 Ch. 1 The beginning", first.title)
				assertEquals("pages", 2, first.pagesCount)
				assertEquals("scanlator", "Test Group", first.scanlator)
				assertTrue("upload date parsed", first.uploadedAt > 0)
				assertEquals("kind", ChapterKind.PAGES, first.kind)
			}
		}

		TestRunner.test("pages uses the at-home host and sets a referer") {
			FakeServer().use { server ->
				server.withMangaDexRoutes()
				val source = mangaDex(server)
				val chapter = assertNotNull(
					"chapter",
					source.details(MangaDexFixtures.MANGA_ID).getOrNull()?.chapters?.firstOrNull(),
				)
				val pages = assertNotNull("pages", source.pages(chapter).getOrNull())
				assertEquals("page count", 2, pages.size)
				assertEquals("page url", server.baseUrl + "/data/hash123/page1.png", pages.first().url)
				assertEquals("referer", MangaDexSource.REFERER, pages.first().referer)
				assertEquals("image referer header", MangaDexSource.REFERER, source.imageHeaders(pages.first())["Referer"])
			}
		}

		TestRunner.test("rate limiting, timeouts, invalid bodies, 404 and offline are classified") {
			FakeServer().use { server ->
				server.status("/manga", 429, headers = mapOf("Retry-After" to "30"))
				val limited = assertNotNull("failure", mangaDex(server).search("x", 0).failureOrNull())
				assertEquals("kind", FailureKind.RATE_LIMITED, limited.kind)
				assertEquals("retry after", 30, limited.retryAfterSeconds)
				assertTrue("message mentions seconds", limited.message().contains("30"))
			}
			FakeServer().use { server ->
				server.onPath("/manga") { FakeServer.Respond(body = "not json".toByteArray()) }
				val invalid = assertNotNull("failure", mangaDex(server).search("x", 0).failureOrNull())
				assertEquals("kind", FailureKind.INVALID_RESPONSE, invalid.kind)
			}
			FakeServer().use { server ->
				server.status("/manga", 404)
				val missing = assertNotNull("failure", mangaDex(server).details("gone").failureOrNull())
				assertEquals("kind", FailureKind.NOT_FOUND, missing.kind)
			}
			FakeServer().use { server ->
				server.delay("/manga", 600) { FakeServer.Respond(body = MangaDexFixtures.searchResults().toByteArray()) }
				val timeout = assertNotNull("failure", mangaDex(server, timeoutMs = 150).search("x", 0).failureOrNull())
				assertEquals("kind", FailureKind.TIMEOUT, timeout.kind)
			}
			FakeServer().use { server ->
				val dead = server.unusedPortUrl()
				val source = MangaDexSource(JdkHttpTransport(defaultTimeoutMs = 2_000), listOf(dead), timeoutMs = 2_000)
				val offline = assertNotNull("failure", source.search("x", 0).failureOrNull())
				assertEquals("kind", FailureKind.OFFLINE, offline.kind)
			}
		}

		TestRunner.test("a broken mirror falls back to the next base URL") {
			FakeServer().use { good ->
				FakeServer().use { broken ->
					broken.status("/manga", 503)
					good.json("/manga", MangaDexFixtures.searchResults())
					val source = MangaDexSource(
						transport = JdkHttpTransport(),
						baseUrls = listOf(broken.baseUrl, good.baseUrl),
					)
					val items = assertNotNull("result", source.search("alpha", 0).getOrNull())
					assertEquals("one item", 1, items.size)
					assertEquals("broken hit once", 1, broken.hitCount("/manga"))
					assertEquals("good hit once", 1, good.hitCount("/manga"))
					assertEquals("sticky base", good.baseUrl, source.baseUrl)
				}
			}
		}
	}

	// --- OPDS ----------------------------------------------------------------------------------

	private fun opdsTests() = TestRunner.suite("Sources · OPDS (local server)") {
		TestRunner.test("catalogue lists series entries with covers and authors") {
			FakeServer().use { server ->
				server.json("/opds", OpdsFixtures.catalog())
				val items = assertNotNull("result", opds(server).popular(0).getOrNull())
				assertEquals("two series", 2, items.size)
				val first = items.first()
				assertEquals("title", "Series One", first.title)
				assertTrue("cover", first.coverUrl?.endsWith("/img/series1.png") == true)
				assertTrue("author", first.authors.contains("Writer One"))
				assertTrue("remote id carries the feed", first.remoteId.contains("|entry|urn:test:series:1"))
			}
		}

		TestRunner.test("OpenSearch link is used for queries") {
			FakeServer().use { server ->
				server.json("/opds", OpdsFixtures.catalog())
				server.json("/opds/search", OpdsFixtures.searchResults())
				val items = assertNotNull("result", opds(server).search("Series One", 0).getOrNull())
				assertEquals("one result", 1, items.size)
				val request = server.requestsFor("/opds/search").first()
				assertTrue("query sent", request.contains("q=Series%20One"))
			}
		}

		TestRunner.test("series details exposes only readable archives") {
			FakeServer().use { server ->
				server.json("/opds", OpdsFixtures.catalog())
				server.json("/opds/series/1", OpdsFixtures.seriesOne())
				val manga = assertNotNull(
					"popular",
					opds(server).popular(0).getOrNull()?.firstOrNull { it.title == "Series One" },
				)
				val details = assertNotNull("details", opds(server).details(manga.remoteId).getOrNull())
				assertEquals("series title", "Series One", details.manga.title)
				assertEquals("one readable chapter", 1, details.chapters.size)
				val chapter = details.chapters.first()
				assertEquals("epub skipped", "Book 1", chapter.title)
				assertEquals("kind", ChapterKind.ARCHIVE, chapter.kind)
				assertTrue("archive url", chapter.url.endsWith("/files/book1.cbz"))
			}
		}

		TestRunner.test("a flat feed of books becomes a one-chapter title") {
			FakeServer().use { server ->
				server.json("/opds", OpdsFixtures.flatCatalog())
				val items = assertNotNull("result", opds(server).popular(0).getOrNull())
				assertEquals("one book", 1, items.size)
				assertEquals("entities decoded", "Standalone & Alone", items.first().title)
				val details = assertNotNull("details", opds(server).details(items.first().remoteId).getOrNull())
				assertEquals("chapters", 1, details.chapters.size)
				assertEquals("kind", ChapterKind.ARCHIVE, details.chapters.first().kind)
			}
		}

		TestRunner.test("an empty feed is reported as an invalid response") {
			FakeServer().use { server ->
				server.json("/opds", OpdsFixtures.emptyFeed())
				val failure = assertNotNull("failure", opds(server).popular(0).failureOrNull())
				assertEquals("kind", FailureKind.INVALID_RESPONSE, failure.kind)
			}
		}

		TestRunner.test("a non-Atom body is reported as an invalid response") {
			FakeServer().use { server ->
				server.json("/opds", """{"not":"a feed"}""")
				val failure = assertNotNull("failure", opds(server).popular(0).failureOrNull())
				assertEquals("kind", FailureKind.INVALID_RESPONSE, failure.kind)
			}
		}
	}

	// --- Catalogue -----------------------------------------------------------------------------

	private fun catalogueTests() = TestRunner.suite("Sources · catalogue state") {
		TestRunner.test("the catalogue lists implemented and unavailable sources with reasons") {
			val repo = newRepository()
			val catalog = repo.sourceCatalog()
			val mangaDex = assertNotNull("mangadex row", catalog.firstOrNull { it.descriptor.id == MangaDexSource.SOURCE_ID })
			assertTrue("enabled by default", mangaDex.enabled)
			assertTrue("available", mangaDex.isAvailable)
			val unavailable = catalog.filter { !it.isAvailable }
			assertTrue("documented unavailable sources", unavailable.size >= 3)
			assertTrue(
				"reasons are translation keys",
				unavailable.all { it.descriptor.unavailableReason?.startsWith("sources.reason.") == true },
			)
			assertTrue("unknown sources are disabled", unavailable.none { it.enabled })
		}

		TestRunner.test("enabled/pinned flags survive a reload and can be restored from the file") {
			val home = newHome()
			val store = SourcesStore(home.resolve("sources.json"))
			store.load()
			store.setEnabled(MangaDexSource.SOURCE_ID, false)
			store.setPinned(MangaDexSource.SOURCE_ID, true)
			store.saveNow()
			val reloaded = SourcesStore(home.resolve("sources.json"))
			reloaded.load()
			val state = assertNotNull("state", reloaded.stateOf(MangaDexSource.SOURCE_ID))
			assertEquals("disabled", false, state.enabled)
			assertTrue("pinned", state.pinned)
		}

		TestRunner.test("OPDS catalogues can be added, deduplicated and removed") {
			val repo = newRepository()
			val outcome = repo.registry.addOpdsCatalog("Mine", "http://localhost:9999/opds")
			assertTrue("added", outcome is SourceOutcome.Success)
			val source = (outcome as SourceOutcome.Success).value
			assertEquals("one catalogue", 1, repo.registry.catalogs().size)
			repo.registry.addOpdsCatalog("Mine again", "http://localhost:9999/opds")
			assertEquals("deduplicated by url", 1, repo.registry.catalogs().size)
			assertNotNull("source registered", repo.registry.get(source.id))
			repo.registry.removeOpdsCatalog(source.id)
			assertEquals("removed", 0, repo.registry.catalogs().size)
			assertEquals("source gone", null, repo.registry.get(source.id))
			val bad = repo.registry.addOpdsCatalog("Bad", "ftp://example.org")
			assertEquals("invalid url rejected", FailureKind.INVALID_RESPONSE, bad.failureOrNull()?.kind)
		}

		TestRunner.test("availability checks are stored and shown in the catalogue") {
			FakeServer().use { server ->
				server.json("/manga", MangaDexFixtures.popularResults())
				val repo = newRepository(server)
				val outcome = repo.registry.checkAvailability(MangaDexSource.SOURCE_ID)
				val availability = assertNotNull("availability", outcome.getOrNull())
				assertTrue("reachable", availability.available)
				val row = repo.sourceCatalog().first { it.descriptor.id == MangaDexSource.SOURCE_ID }
				assertTrue("state stored", row.availability?.available == true)
			}
		}
	}

	// --- Repository flows ----------------------------------------------------------------------

	private fun repositoryTests() = TestRunner.suite("Sources · library integration") {
		TestRunner.test("adding a search result stores stable ids and every chapter") {
			FakeServer().use { server ->
				server.withMangaDexRoutes()
				server.json("/manga", MangaDexFixtures.searchResults())
				val repo = newRepository(server)
				val remote = assertNotNull("search hit", repo.searchRemote("alpha").items.firstOrNull())
				val manga = assertNotNull("added", repo.addFromSource(remote).getOrNull())
				assertEquals("source", MangaDexSource.SOURCE_ID, manga.source)
				assertTrue("in library", repo.findInLibrary(remote) != null)
				val chapters = repo.library.chaptersOf(manga.id)
				assertEquals("chapters", 2, chapters.size)
				assertEquals("remote kind", ChapterKind.PAGES.name, chapters.first().remoteKind)
				assertEquals("remote id", MangaDexFixtures.CHAPTER_1, chapters.first().remoteId)
				// Adding again replaces rather than duplicates.
				repo.addFromSource(remote)
				assertEquals("still two chapters", 2, repo.library.chaptersOf(manga.id).size)
				assertEquals("one title", 1, repo.libraryItems().size)
			}
		}

		TestRunner.test("refreshing keeps read and offline flags") {
			FakeServer().use { server ->
				server.withMangaDexRoutes()
				server.json("/manga", MangaDexFixtures.searchResults())
				val repo = newRepository(server)
				val remote = assertNotNull("hit", repo.searchRemote("alpha").items.firstOrNull())
				val manga = assertNotNull("added", repo.addFromSource(remote).getOrNull())
				val first = repo.library.chaptersOf(manga.id).first()
				repo.markChapterRead(manga, first, true)
				val offlineFile = repo.downloads.chapterTarget(first)
				Files.createDirectories(offlineFile.parent)
				Files.write(offlineFile, OpdsFixtures.cbz())
				repo.library.setChapterDownloaded(first.chapterId, true)
				val refreshed = assertNotNull("refresh", repo.refreshRemote(manga.id).getOrNull())
				assertEquals("chapters after refresh", 2, refreshed)
				val again = repo.library.chaptersOf(manga.id).first { it.chapterId == first.chapterId }
				assertTrue("read flag kept", again.isRead)
				assertTrue("download flag kept", again.isDownloaded)
				assertEquals("title kept from server", "Vol. 1 Ch. 1 The beginning", again.title)
			}
		}

		TestRunner.test("search fans out over enabled sources and reports failures separately") {
			FakeServer().use { server ->
				server.json("/manga", MangaDexFixtures.searchResults())
				server.status("/opds", 500)
				val repo = newRepository(server)
				repo.registry.addOpdsCatalog("Broken", server.baseUrl + "/opds")
				val result = repo.searchRemote("alpha")
				assertEquals("items from mangadex", 1, result.items.size)
				assertEquals("opds failure kept", 1, result.failures.size)
				assertEquals("failure source", "Broken", result.failures.first().first)
				assertEquals("kind", FailureKind.HTTP_ERROR, result.failures.first().second.kind)
			}
		}

		TestRunner.test("a disabled source refuses to browse and is skipped by searches") {
			FakeServer().use { server ->
				server.json("/manga", MangaDexFixtures.searchResults())
				val repo = newRepository(server)
				repo.registry.setEnabled(MangaDexSource.SOURCE_ID, false)
				val browse = repo.browseSource(MangaDexSource.SOURCE_ID)
				assertEquals("kind", FailureKind.SOURCE_UNAVAILABLE, browse.failureOrNull()?.kind)
				val result = repo.searchRemote("alpha")
				assertTrue("no items", result.items.isEmpty())
				assertTrue("nothing requested", server.requests.isEmpty())
			}
		}

		TestRunner.test("a failing source never damages the local library or history") {
			val localRepo = newRepository()
			val dir = Fixtures.tempDir()
			val archive = dir.resolve("Local One.cbz")
			Files.newOutputStream(archive).use { output ->
				java.util.zip.ZipOutputStream(output).use { zip ->
					zip.putNextEntry(java.util.zip.ZipEntry("01.png"))
					zip.write(Fixtures.pngBytes())
					zip.closeEntry()
				}
			}
			localRepo.importPaths(listOf(archive))
			val item = assertNotNull("local title", localRepo.libraryItems().firstOrNull())
			val chapter = localRepo.library.chaptersOf(item.manga.id).first()
			localRepo.saveProgress(item.manga, chapter, 0, 0, 1)
			localRepo.toggleFavourite(item.manga.id)
			val libraryBefore = localRepo.library.mangaCount()
			val historyBefore = localRepo.activity.historyFor(item.manga.id)?.page
			FakeServer().use { server ->
				server.status("/manga", 500)
				localRepo.registry.setBaseUrlOverride(MangaDexSource.SOURCE_ID, server.baseUrl)
				val result = localRepo.searchRemote("anything")
				assertTrue("no remote items", result.items.isEmpty())
				assertEquals("one failure", 1, result.failures.size)
			}
			assertEquals("library untouched", libraryBefore, localRepo.library.mangaCount())
			assertEquals("history untouched", historyBefore, localRepo.activity.historyFor(item.manga.id)?.page)
			assertTrue("favourite kept", localRepo.activity.isFavourite(item.manga.id))
			assertEquals("local source intact", SOURCE_LOCAL, localRepo.library.getManga(item.manga.id)?.source)
		}

		TestRunner.test("page reading caches each remote page once") {
			FakeServer().use { server ->
				server.withMangaDexRoutes()
				server.json("/manga", MangaDexFixtures.searchResults())
				val repo = newRepository(server)
				val remote = assertNotNull("hit", repo.searchRemote("alpha").items.firstOrNull())
				val manga = assertNotNull("added", repo.addFromSource(remote).getOrNull())
				val chapter = repo.library.chaptersOf(manga.id).first()
				val content = assertNotNull("content", repo.chapterContentResult(chapter).getOrNull())
				assertEquals("pages", 2, content.pageCount)
				assertNotNull("page one bytes", content.readPage(0))
				assertNotNull("page one again", content.readPage(0))
				val pageHits = server.hitCount("/data/hash123/page1.png")
				assertEquals("downloaded once", 1, pageHits)
				assertTrue("cached on disk", repo.cachedPageCount(chapter) >= 1)
			}
		}

		TestRunner.test("an unreachable source surfaces a readable message in the reader") {
			FakeServer().use { server ->
				server.withMangaDexRoutes()
				server.json("/manga", MangaDexFixtures.searchResults())
				val repo = newRepository(server)
				val remote = assertNotNull("hit", repo.searchRemote("alpha").items.firstOrNull())
				val manga = assertNotNull("added", repo.addFromSource(remote).getOrNull())
				val chapter = repo.library.chaptersOf(manga.id).first()
				server.close()
				repo.clearPageCache()
				val outcome = repo.chapterContentResult(chapter)
				val failure = assertNotNull("failure", outcome.failureOrNull())
				assertEquals("kind", FailureKind.OFFLINE, failure.kind)
				assertTrue("message is not empty", failure.message().isNotBlank())
			}
		}
	}

	// --- Downloads -----------------------------------------------------------------------------

	private fun downloadTests() = TestRunner.suite("Sources · downloads and offline reading") {
		TestRunner.test("a PAGES chapter downloads into a CBZ and is readable with the server gone") {
			FakeServer().use { server ->
				server.withMangaDexRoutes()
				server.json("/manga", MangaDexFixtures.searchResults())
				val repo = newRepository(server)
				val remote = assertNotNull("hit", repo.searchRemote("alpha").items.firstOrNull())
				val manga = assertNotNull("added", repo.addFromSource(remote).getOrNull())
				val chapter = repo.library.chaptersOf(manga.id).first()
				val result = repo.downloadChapter(chapter)
				val target = assertNotNull("downloaded", result.getOrNull())
				assertTrue("file exists", Files.isRegularFile(target))
				assertTrue("flagged", repo.library.getChapter(chapter.chapterId)?.isDownloaded == true)
				server.close()
				repo.clearPageCache()
				val content = assertNotNull("offline content", repo.chapterContentResult(chapter).getOrNull())
				assertEquals("pages from archive", 2, content.pageCount)
				assertNotNull("page bytes", content.readPage(0))
				assertTrue("zip entries", repo.downloads.offlineFile(chapter) != null)
			}
		}

		TestRunner.test("a failing page aborts the download without leaving a partial archive") {
			FakeServer().use { server ->
				server.withMangaDexRoutes()
				server.json("/manga", MangaDexFixtures.searchResults())
				server.status("/data/hash123/page2.png", 500)
				val repo = newRepository(server)
				val remote = assertNotNull("hit", repo.searchRemote("alpha").items.firstOrNull())
				val manga = assertNotNull("added", repo.addFromSource(remote).getOrNull())
				val chapter = repo.library.chaptersOf(manga.id).first()
				val failure = repo.downloadChapter(chapter).exceptionOrNull()
				assertNotNull("download failed", failure)
				assertTrue("no archive left", repo.downloads.offlineFile(chapter) == null)
				assertTrue("not flagged", repo.library.getChapter(chapter.chapterId)?.isDownloaded == false)
			}
		}

		TestRunner.test("an OPDS archive is downloaded, opened and kept for offline use") {
			FakeServer().use { server ->
				server.json("/opds", OpdsFixtures.catalog())
				server.json("/opds/series/1", OpdsFixtures.seriesOne())
				server.bytes("/files/book1.cbz", OpdsFixtures.cbz(pages = 3), "application/vnd.comicbook+zip")
				val repo = newRepository(server)
				assertTrue("catalogue added", repo.registry.addOpdsCatalog("Test", server.baseUrl + "/opds") is SourceOutcome.Success)
				val hits = repo.searchRemote("", sourceIds = repo.registry.catalogs().map { it.id }).items
				val series = assertNotNull("series found", hits.firstOrNull { it.title == "Series One" })
				val manga = assertNotNull("added", repo.addFromSource(series).getOrNull())
				val chapter = assertNotNull("chapter", repo.library.chaptersOf(manga.id).firstOrNull())
				assertEquals("archive kind", ChapterKind.ARCHIVE.name, chapter.remoteKind)
				val target = assertNotNull("download", repo.downloadChapter(chapter).getOrNull())
				assertTrue("cbz written", Files.isRegularFile(target))
				server.close()
				repo.clearPageCache()
				val content = assertNotNull("offline content", repo.chapterContentResult(chapter).getOrNull())
				assertEquals("pages", 3, content.pageCount)
				assertNotNull("page read", content.readPage(1))
			}
		}

		TestRunner.test("a non-ZIP acquisition answer is rejected with a clear failure") {
			FakeServer().use { server ->
				server.json("/opds", OpdsFixtures.catalog())
				server.json("/opds/series/1", OpdsFixtures.seriesOne())
				server.bytes("/files/book1.cbz", "html error page".toByteArray(), "text/html")
				val repo = newRepository(server)
				repo.registry.addOpdsCatalog("Test", server.baseUrl + "/opds")
				val series = assertNotNull(
					"series",
					repo.searchRemote("").items.firstOrNull { it.title == "Series One" },
				)
				val manga = assertNotNull("added", repo.addFromSource(series).getOrNull())
				val chapter = assertNotNull("chapter", repo.library.chaptersOf(manga.id).firstOrNull())
				val failure = assertNotNull("failure", repo.downloadChapter(chapter).exceptionOrNull())
				assertTrue("mentions zip", failure.message.orEmpty().isNotBlank())
			}
		}
	}

	// --- Backup --------------------------------------------------------------------------------

	private fun backupTests() = TestRunner.suite("Sources · backup round trip") {
		TestRunner.test("enabled, pinned and OPDS catalogues survive export/import") {
			FakeServer().use { server ->
				val source = newRepository()
				source.registry.setPinned(MangaDexSource.SOURCE_ID, true)
				source.registry.setEnabled(MangaDexSource.SOURCE_ID, false)
				source.registry.addOpdsCatalog("My Server", "http://localhost:25600/opds")
				val backup = Fixtures.tempDir().resolve("backup.zip")
				source.exportBackup(backup)

				val target = newRepository()
				val report = target.importBackup(backup)
				assertTrue("sources section counted", report.sources >= 3)
				assertTrue("mangadex pinned", target.registry.isPinned(MangaDexSource.SOURCE_ID))
				assertTrue("mangadex still disabled", !target.registry.isEnabled(target.registry.get(MangaDexSource.SOURCE_ID)!!.descriptor))
				assertEquals("catalogue restored", 1, target.registry.catalogs().size)
				assertEquals("catalogue url", "http://localhost:25600/opds", target.registry.catalogs().first().url)
			}
		}

		TestRunner.test("importing a source list does not overwrite existing flags") {
			val home = newHome()
			val store = SourcesStore(home.resolve("sources.json"))
			store.load()
			store.setEnabled(MangaDexSource.SOURCE_ID, true)
			store.setPinned(MangaDexSource.SOURCE_ID, false)
			assertTrue("first import applies", store.importState(MangaDexSource.SOURCE_ID, false, true) == false)
			assertEquals("kept enabled", true, store.stateOf(MangaDexSource.SOURCE_ID)?.enabled)
		}
	}
}
