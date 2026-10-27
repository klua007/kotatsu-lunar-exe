/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Contract tests of the MangaDex source, run against [FakeServer] on 127.0.0.1.
 *
 * They exist because of a real bug: this build used to send `contentRating[]=porn` (the value the
 * Android parser library maps to) while the public API enumerates `pornographic`, so *every*
 * chapter-list request was rejected with HTTP 400, and `status=…` instead of `status[]=…`, which is
 * rejected as well. Both are pure query-string mistakes, so they are asserted here on the exact URL
 * the parser builds — no internet needed, and a regression cannot slip through again.
 *
 * The fixtures reproduce the shapes documented at <https://api.mangadex.org/docs/>:
 *   GET /manga                  → {"result":"ok","data":[…],"limit":n,"offset":n,"total":n}
 *   GET /manga/{id}/feed        → the same envelope with chapter entities
 *   error                       → {"result":"error","errors":[{"status":400,"title":"…","detail":"…"}]}
 */
package org.koitharu.kotatsu.desktop.test

import org.koitharu.kotatsu.desktop.sources.FailureKind
import org.koitharu.kotatsu.desktop.sources.SearchFilters
import org.koitharu.kotatsu.desktop.sources.SourceOutcome
import org.koitharu.kotatsu.desktop.sources.mangadex.MangaDexSource
import org.koitharu.kotatsu.desktop.sources.net.JdkHttpTransport
import org.koitharu.kotatsu.desktop.sources.net.redactUrl
import org.koitharu.kotatsu.desktop.test.TestRunner.assertEquals
import org.koitharu.kotatsu.desktop.test.TestRunner.assertNotNull
import org.koitharu.kotatsu.desktop.test.TestRunner.assertTrue

object MangaDexContractTests {

	private const val MANGA_ID = MangaDexFixtures.MANGA_ID

	fun run() {
		queryParameterTests()
		paginationTests()
		errorTests()
		flowTests()
	}

	// --- Harness -------------------------------------------------------------------------------

	private fun source(
		server: FakeServer,
		pageSize: Int = 24,
		timeoutMs: Long = 4_000,
	): MangaDexSource = MangaDexSource(
		transport = JdkHttpTransport(defaultTimeoutMs = timeoutMs),
		baseUrls = listOf(server.baseUrl),
		timeoutMs = timeoutMs,
		pageSize = pageSize,
	)

	private fun FakeServer.mangaDexOk(): FakeServer = apply {
		json("/manga", MangaDexFixtures.searchResults())
		json("/manga/$MANGA_ID", MangaDexFixtures.mangaResponse())
		json("/manga/$MANGA_ID/feed", MangaDexFixtures.feed())
		onPath("/at-home/server/${MangaDexFixtures.CHAPTER_1}") {
			FakeServer.Respond(body = MangaDexFixtures.atHome(baseUrl).toByteArray())
		}
		onPrefix("/data/hash123/") {
			FakeServer.Respond(body = Fixtures.pngBytes(), contentType = "image/png")
		}
	}

	private fun queryOf(url: String): Map<String, List<String>> {
		val query = url.substringAfter('?', "")
		if (query.isEmpty()) return emptyMap()
		return query.split('&').mapNotNull { pair ->
			val separator = pair.indexOf('=')
			if (separator <= 0) return@mapNotNull null
			decode(pair.substring(0, separator)) to decode(pair.substring(separator + 1))
		}.groupBy({ it.first }, { it.second })
	}

	private fun decode(value: String): String = java.net.URLDecoder.decode(value, Charsets.UTF_8.name())

	private fun FakeServer.lastQuery(path: String): Map<String, List<String>> {
		val request = requests.lastOrNull { it.substringBefore('?') == path }
			?: TestRunner.fail("no request was made to $path (got ${requests.map { it.substringBefore('?') }})")
		return queryOf(request)
	}

	// --- 1. Query parameters -------------------------------------------------------------------

	private fun queryParameterTests() = TestRunner.suite("MangaDex · request contract") {
		TestRunner.test("content ratings use the API enumeration (pornographic, never porn)") {
			FakeServer().use { server ->
				server.mangaDexOk()
				val source = source(server)
				val nsfw = SearchFilters(includeNsfw = true)
				assertTrue("search ok", source.search("alpha", 0, nsfw).isSuccess)
				val ratings = server.lastQuery("/manga")["contentRating[]"].orEmpty()
				assertEquals(
					"ratings",
					listOf("safe", "suggestive", "erotica", "pornographic"),
					ratings,
				)
			}
		}

		TestRunner.test("the chapter feed never sends the invalid 'porn' value (HTTP 400 regression)") {
			FakeServer().use { server ->
				server.mangaDexOk()
				val source = source(server)
				assertNotNull("details", source.details(MANGA_ID).getOrNull())
				val ratings = server.lastQuery("/manga/$MANGA_ID/feed")["contentRating[]"].orEmpty()
				assertTrue("has pornographic", ratings.contains("pornographic"))
				assertTrue("no bare 'porn' value", !ratings.contains("porn"))
				assertTrue(
					"no 'porn' anywhere in the URL",
					!server.requests.any { it.contains("=porn&") || it.endsWith("=porn") },
				)
			}
		}

		TestRunner.test("a state filter is sent as the status[] array") {
			FakeServer().use { server ->
				server.mangaDexOk()
				val source = source(server)
				source.search("alpha", 0, SearchFilters(states = listOf("ongoing")))
				val query = server.lastQuery("/manga")
				assertEquals("status array", listOf("ongoing"), query["status[]"].orEmpty())
				assertTrue("no scalar status", !query.containsKey("status"))
			}
		}

		TestRunner.test("several states and UI aliases are mapped to the enumeration") {
			FakeServer().use { server ->
				server.mangaDexOk()
				val source = source(server)
				source.search("alpha", 0, SearchFilters(states = listOf("Ongoing", "finished", "paused")))
				assertEquals(
					"mapped states",
					listOf("ongoing", "completed", "hiatus"),
					server.lastQuery("/manga")["status[]"].orEmpty(),
				)
			}
		}

		TestRunner.test("an unknown state is dropped instead of producing a rejected request") {
			FakeServer().use { server ->
				server.mangaDexOk()
				val source = source(server)
				source.search("alpha", 0, SearchFilters(states = listOf("whatever")))
				assertTrue("no status parameter", !server.lastQuery("/manga").containsKey("status[]"))
			}
		}

		TestRunner.test("browse and search use documented orders and the cover/author includes") {
			FakeServer().use { server ->
				server.mangaDexOk()
				val source = source(server)
				assertTrue("popular", source.popular(0).isSuccess)
				val popular = server.lastQuery("/manga")
				assertEquals("includes", listOf("cover_art", "author", "artist"), popular["includes[]"].orEmpty())
				assertEquals("popular order", "desc", popular["order[followedCount]"]?.singleOrNull())
				assertEquals("available chapters", "true", popular["hasAvailableChapters"]?.singleOrNull())

				source.search("alpha")
				val search = server.lastQuery("/manga")
				assertEquals("title", "alpha", search["title"]?.singleOrNull())
				assertEquals("relevance order", "desc", search["order[relevance]"]?.singleOrNull())
			}
		}

		TestRunner.test("translated languages of the feed are array parameters") {
			FakeServer().use { server ->
				server.mangaDexOk()
				source(server).details(MANGA_ID)
				assertEquals(
					"languages",
					MangaDexSource.DEFAULT_LANGUAGES,
					server.lastQuery("/manga/$MANGA_ID/feed")["translatedLanguage[]"].orEmpty(),
				)
			}
		}
	}

	// --- 2. Pagination -------------------------------------------------------------------------

	private fun paginationTests() = TestRunner.suite("MangaDex · pagination limits") {
		TestRunner.test("a page size above the API maximum is clamped") {
			FakeServer().use { server ->
				server.mangaDexOk()
				source(server, pageSize = 5_000).popular(0)
				val limit = server.lastQuery("/manga")["limit"]?.singleOrNull()?.toInt()
				assertEquals("limit clamped to 100", MangaDexSource.MAX_PAGE_SIZE, limit)
			}
		}

		TestRunner.test("offset + limit stays inside the 10 000 row result window") {
			FakeServer().use { server ->
				server.mangaDexOk()
				source(server, pageSize = 100).popular(10_000)
				val query = server.lastQuery("/manga")
				val limit = query["limit"]?.singleOrNull()?.toInt() ?: 0
				val offset = query["offset"]?.singleOrNull()?.toInt() ?: -1
				assertTrue("window respected (offset=$offset, limit=$limit)", offset + limit <= MangaDexSource.MAX_RESULT_WINDOW)
			}
		}

		TestRunner.test("the chapter feed paginates and stops at the window") {
			FakeServer().use { server ->
				server.json("/manga/$MANGA_ID", MangaDexFixtures.mangaResponse())
				server.json("/manga/$MANGA_ID/feed", MangaDexFixtures.feed())
				val details = assertNotNull("details", source(server).details(MANGA_ID).getOrNull())
				assertEquals("chapters", 2, details.chapters.size)
				assertTrue("complete", details.chaptersComplete)
				assertEquals("one feed request", 1, server.hitCount("/manga/$MANGA_ID/feed"))
			}
		}

		TestRunner.test("an empty feed page ends the pagination instead of looping") {
			FakeServer().use { server ->
				server.json("/manga/$MANGA_ID", MangaDexFixtures.mangaResponse())
				server.json(
					"/manga/$MANGA_ID/feed",
					"""{"result":"ok","response":"collection","data":[],"limit":500,"offset":0,"total":0}""",
				)
				val details = assertNotNull("details", source(server).details(MANGA_ID).getOrNull())
				assertEquals("no chapters", 0, details.chapters.size)
				assertEquals("single request", 1, server.hitCount("/manga/$MANGA_ID/feed"))
			}
		}
	}

	// --- 3. Errors -----------------------------------------------------------------------------

	private fun errorTests() = TestRunner.suite("MangaDex · error reporting") {
		TestRunner.test("a HTTP 400 carries the message returned by the API") {
			FakeServer().use { server ->
				val body = """{"result":"error","errors":[{"id":"9c346772","status":400,
					"title":"validation_exception",
					"detail":"Error validating /contentRating/3: Does not have a value in the enumeration [\"safe\",\"suggestive\",\"erotica\",\"pornographic\"]",
					"context":null}]}"""
				server.onPath("/manga/$MANGA_ID/feed") {
					FakeServer.Respond(code = 400, body = body.toByteArray())
				}
				server.json("/manga/$MANGA_ID", MangaDexFixtures.mangaResponse())
				val outcome = source(server).details(MANGA_ID)
				val failure = (outcome as? SourceOutcome.Failure)?.failure
					?: TestRunner.fail("details should fail on HTTP 400")
				assertEquals("kind", FailureKind.HTTP_ERROR, failure.kind)
				assertEquals("code", 400, failure.httpCode)
				val message = assertNotNull("server message", failure.serverMessage)
				assertTrue("mentions the parameter: $message", message.contains("/contentRating/3"))
				assertTrue("shown to the user: ${failure.message()}", failure.message().contains("/contentRating/3"))
			}
		}

		TestRunner.test("a 404 is reported as 'not found', a 429 as rate limited") {
			FakeServer().use { server ->
				server.onPath("/manga/$MANGA_ID") {
					FakeServer.Respond(
						code = 404,
						body = """{"result":"error","errors":[{"status":404,"detail":"Manga not found"}]}"""
							.toByteArray(),
					)
				}
				val notFound = source(server).details(MANGA_ID)
				assertEquals("404 kind", FailureKind.NOT_FOUND, notFound.failureOrNull()?.kind)
				assertEquals("404 detail", "Manga not found", notFound.failureOrNull()?.serverMessage)
			}
			FakeServer().use { server ->
				server.status("/manga", 429, headers = mapOf("Retry-After" to "5"))
				val limited = source(server).popular(0)
				val failure = limited.failureOrNull() ?: TestRunner.fail("popular should fail on 429")
				assertEquals("429 kind", FailureKind.RATE_LIMITED, failure.kind)
				assertEquals("retry after", 5, failure.retryAfterSeconds)
			}
		}

		TestRunner.test("an HTML error page is not shown as an API message") {
			FakeServer().use { server ->
				server.onPath("/manga") {
					FakeServer.Respond(
						code = 502,
						body = "<html><body>Bad gateway</body></html>".toByteArray(),
						contentType = "text/html",
					)
				}
				val failure = source(server).popular(0).failureOrNull()
					?: TestRunner.fail("popular should fail on 502")
				assertEquals("kind", FailureKind.HTTP_ERROR, failure.kind)
				assertEquals("no invented message", null, failure.serverMessage)
			}
		}

		TestRunner.test("URLs are redacted before they reach the log") {
			val url = "https://api.mangadex.org/manga?title=alpha&token=secret123&limit=10"
			val redacted = redactUrl(url)
			assertTrue("token hidden: $redacted", !redacted.contains("secret123"))
			assertTrue("query kept", redacted.contains("title=alpha"))
			assertTrue("limit kept", redacted.contains("limit=10"))
		}
	}

	// --- 4. The whole flow ---------------------------------------------------------------------

	private fun flowTests() = TestRunner.suite("MangaDex · search → details → pages") {
		TestRunner.test("search, details, chapter list and pages all succeed on a valid answer") {
			FakeServer().use { server ->
				server.mangaDexOk()
				val source = source(server)
				val hits = assertNotNull("search", source.search("alpha").getOrNull())
				assertEquals("one hit", 1, hits.size)
				val details = assertNotNull("details", source.details(hits.first().remoteId).getOrNull())
				assertEquals("title", "Alpha Saga", details.manga.title)
				assertEquals("chapters", 2, details.chapters.size)
				val pages = assertNotNull(
					"pages",
					source.pages(details.chapters.first()).getOrNull(),
				)
				assertEquals("page count", 2, pages.size)
				assertTrue("page url", pages.first().url.startsWith("${server.baseUrl}/data/hash123/"))
				assertEquals("referer", MangaDexSource.REFERER, pages.first().referer)
			}
		}

		TestRunner.test("a chapter hosted elsewhere is skipped") {
			FakeServer().use { server ->
				server.json("/manga/$MANGA_ID", MangaDexFixtures.mangaResponse())
				server.json(
					"/manga/$MANGA_ID/feed",
					"""{"result":"ok","response":"collection","data":[${MangaDexFixtures.chapter(
						MangaDexFixtures.CHAPTER_1, "1", "1", "External", 2,
						externalUrl = "https://example.com/chapter",
					)}],"limit":500,"offset":0,"total":1}""",
				)
				val details = assertNotNull("details", source(server).details(MANGA_ID).getOrNull())
				assertEquals("external chapter skipped", 0, details.chapters.size)
			}
		}

		TestRunner.test("unavailable chapters are skipped") {
			FakeServer().use { server ->
				server.json("/manga/$MANGA_ID", MangaDexFixtures.mangaResponse())
				val chapter = """{"id":"${MangaDexFixtures.CHAPTER_1}","type":"chapter","attributes":{
					"title":"Gone","chapter":"1","volume":"1","translatedLanguage":"en","pages":2,
					"publishAt":"2024-01-01T10:00:00+00:00","isUnavailable":true},"relationships":[]}"""
				server.json(
					"/manga/$MANGA_ID/feed",
					"""{"result":"ok","response":"collection","data":[$chapter],"limit":500,"offset":0,"total":1}""",
				)
				val details = assertNotNull("details", source(server).details(MANGA_ID).getOrNull())
				assertEquals("unavailable chapter skipped", 0, details.chapters.size)
			}
		}
	}
}
