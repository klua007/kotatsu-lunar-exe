/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * OPDS 1.2 (Atom) source.
 *
 * OPDS is the open catalogue protocol used by self-hosted comic servers (Komga, Kavita, Calibre)
 * and by several public book/comic catalogues. Implementing it gives the desktop build a second
 * *real* online source without HTML scraping, and it is the supported way to read a user's own
 * server. Chapters are acquisition links (CBZ/ZIP), so reading a chapter is "download the archive
 * once, then open it like a local file" — downloads and offline reading reuse the same code path
 * as imported files.
 *
 * A `remoteId` is `<feed url>|entry|<entry id>`; the separator never appears in an OPDS href.
 */
package org.koitharu.kotatsu.desktop.sources.opds

import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.data.model.RATING_UNKNOWN
import org.koitharu.kotatsu.desktop.sources.ChapterKind
import org.koitharu.kotatsu.desktop.sources.MangaSource
import org.koitharu.kotatsu.desktop.sources.RemoteChapter
import org.koitharu.kotatsu.desktop.sources.RemoteManga
import org.koitharu.kotatsu.desktop.sources.RemoteMangaDetails
import org.koitharu.kotatsu.desktop.sources.RemotePage
import org.koitharu.kotatsu.desktop.sources.SearchFilters
import org.koitharu.kotatsu.desktop.sources.SourceCapabilities
import org.koitharu.kotatsu.desktop.sources.SourceDescriptor
import org.koitharu.kotatsu.desktop.sources.SourceException
import org.koitharu.kotatsu.desktop.sources.SourceFailure
import org.koitharu.kotatsu.desktop.sources.SourceKind
import org.koitharu.kotatsu.desktop.sources.SourceOutcome
import org.koitharu.kotatsu.desktop.sources.net.HttpRequestSpec
import org.koitharu.kotatsu.desktop.sources.net.HttpTransport
import org.koitharu.kotatsu.desktop.sources.net.encodeQueryValue
import org.koitharu.kotatsu.desktop.sources.xml.Xml
import org.koitharu.kotatsu.desktop.sources.xml.XmlNode
import java.net.URI

class OpdsSource(
	private val transport: HttpTransport,
	val id: String,
	val title: String,
	val rootUrl: String,
	private val timeoutMs: Long = HttpRequestSpec.DEFAULT_TIMEOUT_MS,
) : MangaSource {

	override val descriptor: SourceDescriptor = SourceDescriptor(
		id = id,
		title = title,
		language = "multi",
		kind = SourceKind.NATIVE,
		enabledByDefault = false,
	)

	override val capabilities: SourceCapabilities = SourceCapabilities(
		supportsSearch = true,
		supportsTagFilter = false,
		supportsStateFilter = false,
		supportedLanguages = emptyList(),
	)

	override val baseUrl: String get() = rootUrl

	override fun configureBaseUrl(url: String?) {
		// The catalogue URL is the source's identity (the user added it), so it is not overridable
		// at runtime: changing it means adding another catalogue from the Hub.
	}

	override fun popular(page: Int): SourceOutcome<List<RemoteManga>> = SourceOutcome.catching {
		val feed = feedAtPage(page)
		val mangas = collectMangas(feed)
		if (mangas.isEmpty()) {
			throw SourceException(SourceFailure.invalidResponse("no catalog entries in feed", feed.url))
		}
		mangas
	}

	override fun search(query: String, page: Int, filters: SearchFilters): SourceOutcome<List<RemoteManga>> =
		SourceOutcome.catching {
			val trimmed = query.trim()
			if (trimmed.isEmpty()) {
				return@catching when (val result = popular(page)) {
					is SourceOutcome.Success -> result.value
					is SourceOutcome.Failure -> throw SourceException(result.failure)
				}
			}
			val rootFeed = feedAtPage(0)
			val template = searchTemplate(rootFeed)
			if (template != null) {
				val url = template
					.replace("{searchTerms}", encodeQueryValue(trimmed))
					.replace("{startIndex}", (page * SEARCH_PAGE_SIZE).toString())
				val feed = fetchFeed(url)
				val items = collectMangas(feed)
				if (items.isNotEmpty()) items else filterByTitle(feed, trimmed)
			} else {
				// No OpenSearch description: filter the shelf client-side (documented limitation).
				Log.i(TAG, "$title has no OpenSearch link; filtering the feed locally")
				filterByTitle(feedAtPage(page), trimmed)
			}
		}

	override fun details(remoteId: String): SourceOutcome<RemoteMangaDetails> = SourceOutcome.catching {
		val (feedUrl, entryId) = splitId(remoteId)
		val feed = fetchFeed(feedUrl)
		if (entryId.isNullOrBlank()) {
			val chapters = chaptersOf(feed, depth = 0)
			if (chapters.isEmpty()) {
				throw SourceException(SourceFailure.invalidResponse("feed has no downloadable archives", feed.url))
			}
			return@catching RemoteMangaDetails(mangaFromFeed(feed), chapters, true)
		}
		val entry = feed.root.descendants("entry").firstOrNull { it.childText("id") == entryId }
			?: throw SourceException(
				SourceFailure.invalidResponse("entry is no longer in the feed", feed.url),
			)
		val subsection = entry.childrenNamed("link")
			.firstOrNull { it.attr("rel") == "subsection" }
			?.attr("href")
		val ownAcquisitions = acquisitionLinks(feed.url, entry)
		val manga: RemoteManga
		val chapters: List<RemoteChapter>
		when {
			ownAcquisitions.isNotEmpty() && subsection == null -> {
				manga = mangaFromEntry(feed, entry)
				chapters = ownAcquisitions.mapIndexed { index, link ->
					link.toChapter(index, entry.childText("title") ?: entryId)
				}
			}

			subsection != null -> {
				val seriesUrl = absolute(feed.url, subsection)
				val seriesFeed = fetchFeed(seriesUrl)
				manga = mangaFromFeed(seriesFeed)
				chapters = chaptersOf(seriesFeed, depth = 0)
			}

			else -> throw SourceException(
				SourceFailure.invalidResponse("entry has no downloadable archive", feed.url),
			)
		}
		if (chapters.isEmpty()) {
			throw SourceException(
				SourceFailure.invalidResponse("no CBZ/ZIP chapters (other formats are not supported)", feed.url),
			)
		}
		RemoteMangaDetails(manga, chapters, true)
	}

	override fun pages(chapter: RemoteChapter): SourceOutcome<List<RemotePage>> =
		// OPDS chapters are archives; the reader downloads and opens them instead of listing pages.
		SourceOutcome.Success(emptyList())

	// --- Feed handling -------------------------------------------------------------------------

	private data class Feed(val url: String, val root: XmlNode)

	private fun fetchFeed(url: String): Feed {
		val result = try {
			transport.execute(
				HttpRequestSpec(
					url = url,
					timeoutMs = timeoutMs,
					headers = mapOf(
						"Accept" to "application/atom+xml,application/xml;q=0.9,*/*;q=0.5",
					),
				),
			)
		} catch (e: SourceException) {
			throw e
		} catch (e: Exception) {
			throw SourceException(SourceFailure.io(e.message ?: e.javaClass.simpleName, url))
		}
		if (!result.isSuccessful) {
			throw SourceException(SourceFailure.http(result.code, url, result.retryAfterSeconds()))
		}
		val document = Xml.parse(result.text())
		val root = document.children.firstOrNull { it.localName == "feed" }
			?: throw SourceException(SourceFailure.invalidResponse("not an Atom/OPDS feed", url))
		return Feed(result.url.ifBlank { url }, root)
	}

	/** Feed of the requested page, following `rel="next"` links (bounded). */
	private fun feedAtPage(page: Int): Feed {
		var feed = fetchFeed(rootUrl)
		repeat(page.coerceIn(0, MAX_PAGE_STEPS)) {
			val next = feed.root.childrenNamed("link")
				.firstOrNull { it.attr("rel") == "next" }
				?.attr("href") ?: return feed
			feed = fetchFeed(absolute(feed.url, next))
		}
		return feed
	}

	private fun mangaFromFeed(feed: Feed): RemoteManga = RemoteManga(
		sourceId = descriptor.id,
		remoteId = joinId(feed.url, null),
		title = feed.root.childText("title") ?: title,
		url = feed.url,
		coverUrl = imageLink(feed.url, feed.root),
		description = feed.root.childText("subtitle"),
		authors = feed.root.childrenNamed("author").mapNotNull { it.childText("name") },
		rating = RATING_UNKNOWN,
	)

	private fun mangaFromEntry(feed: Feed, entry: XmlNode): RemoteManga {
		val entryId = entry.childText("id") ?: return mangaFromFeed(feed)
		val href = absolute(feed.url, entryLink(entry) ?: entryId)
		return RemoteManga(
			sourceId = descriptor.id,
			remoteId = joinId(feed.url, entryId),
			title = entry.childText("title") ?: entryId,
			url = href,
			coverUrl = imageLink(feed.url, entry),
			description = entry.childText("content") ?: entry.childText("summary"),
			authors = entry.childrenNamed("author").mapNotNull { it.childText("name") },
			tags = entry.childrenNamed("category").mapNotNull { it.attr("label") },
			rating = RATING_UNKNOWN,
		)
	}

	/** Entries of a feed; follows navigation feeds one level down (library → series). */
	private fun collectMangas(feed: Feed): List<RemoteManga> {
		val direct = feed.root.descendants("entry").mapNotNull { entry -> entryToManga(feed, entry) }
		if (direct.isNotEmpty()) return direct
		return navigationFeeds(feed).flatMap { child ->
			child.root.descendants("entry").mapNotNull { entry -> entryToManga(child, entry) }
		}
	}

	private fun entryToManga(feed: Feed, entry: XmlNode): RemoteManga? {
		val hasArchive = acquisitionLinks(feed.url, entry).isNotEmpty()
		val subsection = entry.childrenNamed("link").firstOrNull { it.attr("rel") == "subsection" }?.attr("href")
		if (!hasArchive && subsection == null) return null
		val entryId = entry.childText("id") ?: return null
		return RemoteManga(
			sourceId = descriptor.id,
			remoteId = joinId(feed.url, entryId),
			title = entry.childText("title") ?: entryId,
			url = absolute(feed.url, entryLink(entry) ?: subsection ?: entryId),
			coverUrl = imageLink(feed.url, entry),
			description = entry.childText("content") ?: entry.childText("summary"),
			authors = entry.childrenNamed("author").mapNotNull { it.childText("name") },
			tags = entry.childrenNamed("category").mapNotNull { it.attr("label") },
			rating = RATING_UNKNOWN,
		)
	}

	private fun navigationFeeds(feed: Feed): List<Feed> = feed.root.childrenNamed("link")
		.filter { it.attr("rel") == "subsection" }
		.mapNotNull { it.attr("href") }
		.take(MAX_NAVIGATION_FEEDS)
		.mapNotNull { href ->
			runCatching { fetchFeed(absolute(feed.url, href)) }.getOrElse { error ->
				Log.w(TAG, "Cannot read navigation feed $href", error)
				null
			}
		}

	/** Chapters of a feed (and of its navigation children, one level deep). */
	private fun chaptersOf(feed: Feed, depth: Int): List<RemoteChapter> {
		val result = ArrayList<RemoteChapter>()
		feed.root.descendants("entry").forEach { entry ->
			val links = acquisitionLinks(feed.url, entry)
			if (links.isEmpty()) return@forEach
			val entryTitle = entry.childText("title") ?: "Chapter"
			links.forEachIndexed { index, link ->
				result += link.toChapter(result.size, if (links.size > 1) "$entryTitle (${index + 1})" else entryTitle)
			}
		}
		if (result.isEmpty() && depth < MAX_NAVIGATION_DEPTH) {
			navigationFeeds(feed).forEach { child ->
				result += chaptersOf(child, depth + 1)
			}
		}
		return result
	}

	private data class Acquisition(val href: String, val type: String, val title: String?)

	private fun acquisitionLinks(feedUrl: String, entry: XmlNode): List<Acquisition> =
		entry.childrenNamed("link")
			.filter { it.attr("rel")?.startsWith("http://opds-spec.org/acquisition") == true }
			.mapNotNull { link ->
				val href = link.attr("href") ?: return@mapNotNull null
				val url = absolute(feedUrl, href)
				val type = link.attr("type").orEmpty()
				// Only archives this build can read are offered (see ArchiveReader): no EPUB/PDF.
				if (!isReadableArchive(url, type)) return@mapNotNull null
				Acquisition(url, type, link.attr("title"))
			}

	private fun isReadableArchive(url: String, type: String): Boolean {
		val lowerType = type.lowercase()
		val path = url.substringBefore('?').substringBefore('#').lowercase()
		// EPUB/PDF also carry "zip" in their media type, but this build cannot render them.
		if ("epub" in lowerType || "pdf" in lowerType) return false
		if (path.endsWith(".epub") || path.endsWith(".pdf")) return false
		if (path.endsWith(".cbz") || path.endsWith(".zip")) return true
		return ("comicbook+zip" in lowerType || lowerType == "application/zip" || "x-cbz" in lowerType)
	}

	private fun Acquisition.toChapter(index: Int, fallbackTitle: String): RemoteChapter = RemoteChapter(
		remoteId = href,
		title = title ?: fallbackTitle,
		url = href,
		number = (index + 1).toFloat(),
		volume = 0,
		uploadedAt = 0L,
		pagesCount = 0,
		scanlator = null,
		kind = ChapterKind.ARCHIVE,
	)

	private fun imageLink(feedUrl: String, node: XmlNode): String? = node.childrenNamed("link")
		.firstOrNull {
			it.attr("rel") == "http://opds-spec.org/image" ||
				it.attr("rel") == "http://opds-spec.org/image/thumbnail"
		}
		?.attr("href")
		?.let { absolute(feedUrl, it) }

	private fun entryLink(entry: XmlNode?): String? = entry?.childrenNamed("link")
		?.firstOrNull { it.attr("rel") == "alternate" }
		?.attr("href")

	private fun searchTemplate(feed: Feed): String? = feed.root.childrenNamed("link")
		.firstOrNull { it.attr("rel") == "search" }
		?.attr("href")
		?.let { absolute(feed.url, it) }

	private fun filterByTitle(feed: Feed, needle: String): List<RemoteManga> =
		feed.root.descendants("entry")
			.mapNotNull { entry -> entryToManga(feed, entry) }
			.filter { it.title.contains(needle, ignoreCase = true) }

	/**
	 * Resolves an href against the feed URL without going through `URI.resolve` — OPDS search
	 * templates contain `{searchTerms}`, which `URI` rejects.
	 */
	private fun absolute(base: String?, href: String?): String {
		if (href.isNullOrBlank()) return base ?: rootUrl
		if (href.startsWith("http://") || href.startsWith("https://")) return href
		val baseUrl = base ?: rootUrl
		val origin = runCatching {
			val uri = URI(baseUrl)
			if (uri.scheme == null || uri.authority == null) null else "${uri.scheme}://${uri.authority}"
		}.getOrNull()
		if (href.startsWith("/")) return (origin ?: "") + href
		val path = baseUrl.substringBefore('?').substringBefore('#')
		val directory = path.substringBeforeLast('/', "")
		val prefix = if (directory.isEmpty()) (origin ?: "") + "/" else directory.trimEnd('/') + "/"
		return prefix + href
	}

	private fun splitId(remoteId: String): Pair<String, String?> {
		val index = remoteId.indexOf(ID_SEPARATOR)
		if (index < 0) return remoteId to null
		val feed = remoteId.substring(0, index)
		val entry = remoteId.substring(index + ID_SEPARATOR.length)
		return feed to entry.takeIf { it.isNotBlank() }
	}

	private fun joinId(feedUrl: String, entryId: String?): String =
		if (entryId.isNullOrBlank()) feedUrl else feedUrl + ID_SEPARATOR + entryId

	private companion object {
		const val TAG = "OPDS"
		const val ID_SEPARATOR = "|entry|"
		const val MAX_NAVIGATION_FEEDS = 8
		const val MAX_NAVIGATION_DEPTH = 1
		const val MAX_PAGE_STEPS = 5
		const val SEARCH_PAGE_SIZE = 20
	}
}
