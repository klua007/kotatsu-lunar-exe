/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * A real HTTP server on 127.0.0.1 (JDK `com.sun.net.httpserver`) plus fixtures that look like the
 * answers of the supported sources. Every source test runs against this server, so the suite is
 * reproducible without network access and can simulate latency, 429/500, timeouts, invalid bodies
 * and a completely unreachable host.
 */
package org.koitharu.kotatsu.desktop.test

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FakeServer : AutoCloseable {

	data class Respond(
		val code: Int = 200,
		val body: ByteArray = ByteArray(0),
		val contentType: String = "application/json; charset=utf-8",
		val headers: Map<String, String> = emptyMap(),
		val delayMs: Long = 0,
	)

	private class Route(
		val match: (URI) -> Boolean,
		val respond: (URI) -> Respond,
	)

	private val routes = CopyOnWriteArrayList<Route>()

	/** Every request URI, in order, so tests can assert what was (not) requested. */
	val requests = CopyOnWriteArrayList<String>()

	private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
		createContext("/") { exchange -> handle(exchange) }
		executor = Executors.newFixedThreadPool(6) { runnable ->
			Thread(runnable, "fake-server").apply { isDaemon = true }
		}
		start()
	}

	val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

	fun on(match: (URI) -> Boolean, respond: (URI) -> Respond): FakeServer {
		routes.add(Route(match, respond))
		return this
	}

	fun onPath(path: String, respond: (URI) -> Respond): FakeServer = on({ it.path == path }, respond)

	fun onPrefix(prefix: String, respond: (URI) -> Respond): FakeServer =
		on({ it.path.startsWith(prefix) }, respond)

	fun json(path: String, body: String): FakeServer = onPath(path) { Respond(body = body.toByteArray()) }

	fun json(prefix: String, predicate: (URI) -> Boolean, body: String, code: Int = 200): FakeServer =
		on({ it.path == prefix && predicate(it) }) { Respond(code = code, body = body.toByteArray()) }

	fun bytes(path: String, body: ByteArray, contentType: String): FakeServer =
		onPath(path) { Respond(body = body, contentType = contentType) }

	fun status(path: String, code: Int, headers: Map<String, String> = emptyMap()): FakeServer =
		onPath(path) { Respond(code = code, headers = headers) }

	fun delay(prefix: String, delayMs: Long, respond: (URI) -> Respond): FakeServer =
		on({ it.path.startsWith(prefix) }) { uri -> respond(uri).copy(delayMs = delayMs) }

	fun requestsFor(path: String): List<String> = requests.filter { URI.create(it).path == path }

	fun hitCount(path: String): Int = requestsFor(path).size

	/** A path that nothing is listening on, for "offline" tests. */
	fun unusedPortUrl(): String {
		val socket = java.net.ServerSocket(0)
		val port = socket.localPort
		socket.close()
		return "http://127.0.0.1:$port"
	}

	private fun handle(exchange: HttpExchange) {
		val uri = exchange.requestURI
		requests.add(uri.toString())
		// Later registrations win, so a test can override a generic route with a specific failure.
		val route = routes.lastOrNull { it.match(uri) }
		val respond = route?.respond?.invoke(uri) ?: Respond(code = 404, body = "not found".toByteArray())
		if (respond.delayMs > 0) {
			runCatching { Thread.sleep(respond.delayMs) }
		}
		val headers = exchange.responseHeaders
		headers.set("Content-Type", respond.contentType)
		headers.set("Connection", "close")
		respond.headers.forEach { (name, value) -> headers.set(name, value) }
		runCatching {
			exchange.sendResponseHeaders(respond.code, respond.body.size.toLong())
			exchange.responseBody.use { it.write(respond.body) }
		}
		exchange.close()
	}

	override fun close() {
		runCatching { server.stop(0) }
	}
}

/** JSON answers shaped like the MangaDex API (see its OpenAPI description). */
object MangaDexFixtures {

	const val MANGA_ID = "0f2d9b1e-1111-4c2a-9d0d-000000000001"
	const val CHAPTER_1 = "7a1c0000-0000-4000-8000-000000000001"
	const val CHAPTER_2 = "7a1c0000-0000-4000-8000-000000000002"

	/** Removes indentation/newlines so the fixtures stay readable in this file. */
	private fun compact(raw: String): String = raw.trimIndent().lines().joinToString("") { it.trim() }

	fun manga(
		id: String = MANGA_ID,
		title: String = "Alpha Saga",
		tag: String = "Action",
		cover: String? = "cover.png",
	): String {
		val coverRelation = if (cover == null) "" else """
			{"id":"cover-1","type":"cover_art","attributes":{"fileName":"$cover"}},
		""".trimIndent()
		return compact(
			"""
			{
			  "id":"$id","type":"manga","attributes":{
			    "title":{"en":"$title"},
			    "altTitles":[{"ja":"アルファ"}],
			    "description":{"en":"A test title.","pt-br":"Um título de teste."},
			    "status":"ongoing","contentRating":"safe","year":2024,
			    "tags":[{"id":"tag-1","type":"tag","attributes":{"name":{"en":"$tag"}}}]
			  },
			  "relationships":[
			    $coverRelation
			    {"id":"author-1","type":"author","attributes":{"name":"Author One"}},
			    {"id":"artist-1","type":"artist","attributes":{"name":"Artist Two"}}
			  ]
			}
			""",
		)
	}

	fun mangaResponse(id: String = MANGA_ID): String =
		"""{"result":"ok","response":"entity","data":${manga(id)}}"""

	fun searchResults(): String =
		"""{"result":"ok","response":"collection","data":[${manga()}],"limit":24,"offset":0,"total":1}"""

	fun popularResults(): String =
		"""{"result":"ok","response":"collection","data":[""" +
			manga(id = "popular-1", title = "Popular One") + "," +
			manga(id = "popular-2", title = "Popular Two") +
			"""],"limit":24,"offset":0,"total":2}"""

	fun feed(): String =
		"""{"result":"ok","response":"collection","data":[""" +
			chapter(CHAPTER_1, number = "1", volume = "1", title = "The beginning", pages = 2) + "," +
			chapter(CHAPTER_2, number = "2", volume = "1", title = "Next", pages = 1) +
			"""],"limit":500,"offset":0,"total":2}"""

	fun chapter(
		id: String,
		number: String,
		volume: String,
		title: String,
		pages: Int,
		externalUrl: String? = null,
	): String {
		val external = if (externalUrl == null) "null" else "\"$externalUrl\""
		return compact(
			"""
			{"id":"$id","type":"chapter","attributes":{
			  "title":"$title","chapter":"$number","volume":"$volume",
			  "translatedLanguage":"en","pages":$pages,
			  "publishAt":"2024-01-0${number}T10:00:00+00:00",
			  "externalUrl":$external
			},"relationships":[{"id":"group-1","type":"scanlation_group","attributes":{"name":"Test Group"}}]}
			""",
		)
	}

	fun atHome(baseUrl: String, files: List<String> = listOf("page1.png", "page2.png")): String {
		val data = files.joinToString(",") { "\"$it\"" }
		val saver = files.joinToString(",") { "\"s-${it.replace(".png", ".jpg")}\"" }
		return compact(
			"""
			{"result":"ok","baseUrl":"$baseUrl","chapter":{
			  "hash":"hash123","data":[$data],"dataSaver":[$saver]
			}}
			""",
		)
	}
}

/** Atom/OPDS feeds: a catalogue → series → books, with one non-ZIP acquisition to be skipped. */
object OpdsFixtures {

	fun catalog(): String = """<?xml version="1.0" encoding="utf-8"?>
		<feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
		  <id>urn:test:catalog</id>
		  <title>Test Catalogue</title>
		  <subtitle>Offline fixtures</subtitle>
		  <link rel="self" href="/opds" type="application/atom+xml;profile=opds-catalog"/>
		  <link rel="search" type="application/atom+xml" href="/opds/search?q={searchTerms}"/>
		  <entry>
		    <id>urn:test:series:1</id>
		    <title>Series One</title>
		    <author><name>Writer One</name></author>
		    <content>First series.</content>
		    <link rel="subsection" href="/opds/series/1" type="application/atom+xml;profile=opds-catalog"/>
		    <link rel="http://opds-spec.org/image" href="/img/series1.png"/>
		  </entry>
		  <entry>
		    <id>urn:test:series:2</id>
		    <title>Series Two</title>
		    <link rel="subsection" href="/opds/series/2" type="application/atom+xml;profile=opds-catalog"/>
		  </entry>
		</feed>
	"""

	fun seriesOne(): String = """<?xml version="1.0" encoding="utf-8"?>
		<feed xmlns="http://www.w3.org/2005/Atom">
		  <id>urn:test:series:1</id>
		  <title>Series One</title>
		  <entry>
		    <id>urn:test:book:1</id>
		    <title>Book 1</title>
		    <link rel="http://opds-spec.org/acquisition" href="/files/book1.cbz" type="application/vnd.comicbook+zip"/>
		    <link rel="http://opds-spec.org/image" href="/img/book1.png"/>
		  </entry>
		  <entry>
		    <id>urn:test:book:2</id>
		    <title>Book 2 (EPUB, unsupported)</title>
		    <link rel="http://opds-spec.org/acquisition" href="/files/book2.epub" type="application/epub+zip"/>
		  </entry>
		</feed>
	"""

	fun searchResults(): String = """<?xml version="1.0" encoding="utf-8"?>
		<feed xmlns="http://www.w3.org/2005/Atom">
		  <id>urn:test:search</id>
		  <title>Search</title>
		  <entry>
		    <id>urn:test:series:1</id>
		    <title>Series One</title>
		    <link rel="subsection" href="/opds/series/1" type="application/atom+xml;profile=opds-catalog"/>
		  </entry>
		</feed>
	"""

	fun flatCatalog(): String = """<?xml version="1.0" encoding="utf-8"?>
		<feed xmlns="http://www.w3.org/2005/Atom">
		  <id>urn:test:flat</id>
		  <title>Flat</title>
		  <link rel="self" href="/opds" type="application/atom+xml"/>
		  <entry>
		    <id>urn:test:book:9</id>
		    <title>Standalone &amp; Alone</title>
		    <link rel="http://opds-spec.org/acquisition" href="/files/book1.cbz" type="application/vnd.comicbook+zip"/>
		  </entry>
		</feed>
	"""

	fun emptyFeed(): String = """<?xml version="1.0" encoding="utf-8"?>
		<feed xmlns="http://www.w3.org/2005/Atom"><id>urn:empty</id><title>Empty</title></feed>
	"""

	fun cbz(pages: Int = 2): ByteArray {
		val buffer = java.io.ByteArrayOutputStream()
		ZipOutputStream(buffer).use { zip ->
			for (index in 0 until pages) {
				zip.putNextEntry(ZipEntry(String.format("%02d.png", index + 1)))
				zip.write(Fixtures.pngBytes(shade = 60 + index * 20))
				zip.closeEntry()
			}
		}
		return buffer.toByteArray()
	}
}
