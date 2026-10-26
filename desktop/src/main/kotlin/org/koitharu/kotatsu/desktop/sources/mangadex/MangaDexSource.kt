/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * MangaDex source (https://api.mangadex.org/docs/).
 *
 * Why this source: it is the one large catalogue with a documented, stable, public JSON API that
 * does not need JavaScript, a WebView, cookies or an account, so it can be implemented and *tested*
 * on the JVM with nothing but HTTP. The Android app reaches the same catalogue through the
 * upstream parser library; see docs/SOURCES.md for the comparison and for the sources whose HTML
 * scraping requires the (currently unbundled) parser pack.
 *
 * Endpoints used:
 *   GET /manga                     search / browse
 *   GET /manga/{id}                details
 *   GET /manga/{id}/feed           chapter list
 *   GET /at-home/server/{id}       page image host for a chapter
 * Images are served from the URL returned by /at-home/server, with the CDN cover host for covers.
 */
package org.koitharu.kotatsu.desktop.sources.mangadex

import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.core.json.Json
import org.koitharu.kotatsu.desktop.core.json.JsonValue
import org.koitharu.kotatsu.desktop.core.json.asArr
import org.koitharu.kotatsu.desktop.core.json.arr
import org.koitharu.kotatsu.desktop.core.json.asIntOrNull
import org.koitharu.kotatsu.desktop.core.json.int
import org.koitharu.kotatsu.desktop.core.json.obj
import org.koitharu.kotatsu.desktop.core.json.string
import org.koitharu.kotatsu.desktop.core.json.asObj
import org.koitharu.kotatsu.desktop.core.json.asStringOrNull
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
import org.koitharu.kotatsu.desktop.sources.net.EndpointChooser
import org.koitharu.kotatsu.desktop.sources.net.HttpRequestSpec
import org.koitharu.kotatsu.desktop.sources.net.HttpTransport
import org.koitharu.kotatsu.desktop.sources.net.joinUrl
import org.koitharu.kotatsu.desktop.sources.net.urlWithQuery
import java.time.OffsetDateTime

class MangaDexSource(
	private val transport: HttpTransport,
	baseUrls: List<String> = DEFAULT_BASES,
	private val languages: List<String> = DEFAULT_LANGUAGES,
	private val timeoutMs: Long = HttpRequestSpec.DEFAULT_TIMEOUT_MS,
	private val pageSize: Int = 24,
	/** Language used for titles/descriptions when the API offers a choice. */
	private val preferredLanguage: String = "en",
) : MangaSource {

	override val descriptor: SourceDescriptor = SourceDescriptor(
		id = SOURCE_ID,
		title = "MangaDex",
		language = "multi",
		kind = SourceKind.NATIVE,
		enabledByDefault = true,
	)

	override val capabilities: SourceCapabilities = SourceCapabilities(
		supportsSearch = true,
		supportsTagFilter = true,
		supportsStateFilter = true,
		supportedLanguages = languages,
	)

	private val endpoints = EndpointChooser(baseUrls) { index, failed, reason ->
		Log.w(TAG, "MangaDex endpoint '$failed' failed ($reason); using ${baseUrls.getOrNull(index)}")
	}

	override val baseUrl: String get() = endpoints.current

	private var baseOverride: String? = null

	override fun configureBaseUrl(url: String?) {
		baseOverride = url?.trim()?.trimEnd('/')?.takeIf { it.startsWith("http") }
	}

	// --- MangaSource ---------------------------------------------------------------------------

	override fun popular(page: Int): SourceOutcome<List<RemoteManga>> = SourceOutcome.catching {
		val json = api(
			"/manga",
			baseQuery(page) + listOf(
				"order[followedCount]" to "desc",
				"hasAvailableChapters" to "true",
			),
		)
		parseMangaList(json)
	}

	override fun search(query: String, page: Int, filters: SearchFilters): SourceOutcome<List<RemoteManga>> =
		SourceOutcome.catching {
			val params = baseQuery(page, filters.includeNsfw) + buildList {
				val trimmed = query.trim()
				if (trimmed.isNotEmpty()) {
					add("title" to trimmed)
					add("order[relevance]" to "desc")
				} else {
					add("order[latestUploadedChapter]" to "desc")
				}
				filters.states.firstOrNull()?.let { add("status" to it.lowercase()) }
				add("hasAvailableChapters" to "true")
			}
			val json = api("/manga", params)
			parseMangaList(json).let { list ->
				// Tag filtering: MangaDex accepts `includedTags[]` with ids, but the Hub offers tag
				// names (from details), so filter locally on the returned page and say so in the UI.
				if (filters.tags.isEmpty()) list else list.filter { manga ->
					filters.tags.all { wanted -> manga.tags.any { it.equals(wanted, ignoreCase = true) } }
				}
			}
		}

	override fun details(remoteId: String): SourceOutcome<RemoteMangaDetails> = SourceOutcome.catching {
		val id = remoteId.trim()
		if (id.isEmpty()) throw SourceException(SourceFailure.invalidResponse("empty manga id"))
		val json = api("/manga/$id", LIST_INCLUDES)
		val entity = json.obj("data")
			?: throw SourceException(SourceFailure.invalidResponse("missing data", baseUrl))
		val manga = parseManga(entity)
			?: throw SourceException(SourceFailure.invalidResponse("manga has no title", baseUrl))
		val (chapters, complete) = feed(id)
		RemoteMangaDetails(manga, chapters, complete)
	}

	override fun pages(chapter: RemoteChapter): SourceOutcome<List<RemotePage>> = SourceOutcome.catching {
		val json = api("/at-home/server/${chapter.remoteId}", emptyList())
		val base = json.string("baseUrl")
			?: throw SourceException(SourceFailure.invalidResponse("missing baseUrl", baseUrl))
		val chapterNode = json.obj("chapter")
			?: throw SourceException(SourceFailure.invalidResponse("missing chapter node", baseUrl))
		val hash = chapterNode["hash"].asStringOrNull()
			?: throw SourceException(SourceFailure.invalidResponse("missing hash", baseUrl))
		val files = chapterNode["data"].asArr()?.items?.mapNotNull { it.asStringOrNull() }.orEmpty()
		if (files.isEmpty()) {
			throw SourceException(SourceFailure.invalidResponse("chapter has no pages", baseUrl))
		}
		files.map { file ->
			RemotePage(
				url = "$base/data/$hash/$file",
				referer = REFERER,
			)
		}
	}

	override fun imageHeaders(page: RemotePage): Map<String, String> = buildMap {
		put("Accept", "image/*")
		page.referer?.let { put("Referer", it) }
	}

	// --- HTTP ----------------------------------------------------------------------------------

	private fun api(path: String, params: List<Pair<String, String?>>): JsonValue {
		val override = baseOverride
		val raw = if (override != null) {
			requestText(urlWithQuery(joinUrl(override, path), params))
		} else {
			endpoints.withFallback { base -> requestText(urlWithQuery(joinUrl(base, path), params)) }
		}
		return Json.parseOrNull(raw)
			?: throw SourceException(SourceFailure.invalidResponse("response is not JSON", path))
	}

	private fun requestText(url: String): String {
		val result = try {
			transport.execute(
				HttpRequestSpec(
					url = url,
					timeoutMs = timeoutMs,
					headers = mapOf(
						"Accept" to "application/json",
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
		return result.text()
	}

	private fun baseQuery(page: Int, includeNsfw: Boolean = false): List<Pair<String, String?>> = buildList {
		add("limit" to pageSize.toString())
		add("offset" to (page * pageSize).coerceAtLeast(0).toString())
		add("includes[]" to "cover_art")
		add("includes[]" to "author")
		add("includes[]" to "artist")
		addAll(contentRatingParams(includeNsfw))
	}

	private fun contentRatingParams(includeNsfw: Boolean): List<Pair<String, String?>> {
		val ratings = mutableListOf("safe", "suggestive")
		if (includeNsfw) {
			ratings += "erotica"
			ratings += "porn"
		}
		return ratings.map { "contentRating[]" to it }
	}

	// --- JSON parsing --------------------------------------------------------------------------

	private fun parseMangaList(json: JsonValue): List<RemoteManga> {
		if (json.string("result") == "error") {
			val message = json.arr("errors")?.items?.firstOrNull()
				?.asObj()?.get("detail").asStringOrNull() ?: "API error"
			throw SourceException(SourceFailure.invalidResponse(message, baseUrl))
		}
		val data = json.arr("data")
			?: throw SourceException(SourceFailure.invalidResponse("missing data array", baseUrl))
		return data.items.mapNotNull { parseManga(it.asObj() ?: return@mapNotNull null) }
	}

	private fun parseManga(node: JsonValue.Obj): RemoteManga? {
		val id = node["id"].asStringOrNull() ?: return null
		val attributes = node["attributes"].asObj() ?: return null
		val title = pickLocalized(attributes["title"]) ?: return null
		val relationships = node["relationships"].asArr()?.items.orEmpty().mapNotNull { it.asObj() }
		val coverFile = relationships.firstOrNull { it["type"].asStringOrNull() == "cover_art" }
			?.get("attributes")?.asObj()?.get("fileName")?.asStringOrNull()
		val authors = relationships.mapNotNull { relation ->
			val type = relation["type"].asStringOrNull()
			if (type != "author" && type != "artist") return@mapNotNull null
			relation["attributes"].asObj()?.get("name").asStringOrNull()
		}.distinct()
		val tags = attributes["tags"].asArr()?.items.orEmpty().mapNotNull { tag ->
			val tagAttributes = tag.asObj()?.get("attributes").asObj() ?: return@mapNotNull null
			pickLocalized(tagAttributes["name"])
		}.distinct()
		val contentRating = attributes["contentRating"].asStringOrNull()
		val altTitles = attributes["altTitles"].asArr()?.items.orEmpty().mapNotNull { item ->
			item.asObj()?.fields?.values?.firstOrNull()?.asStringOrNull()
		}.distinct()
		return RemoteManga(
			sourceId = descriptor.id,
			remoteId = id,
			title = title,
			url = "$PUBLIC_BASE/title/$id",
			altTitles = altTitles,
			coverUrl = coverFile?.let { "$COVER_BASE/covers/$id/$it.512.jpg" },
			description = pickLocalized(attributes["description"]),
			authors = authors,
			tags = tags,
			state = attributes["status"].asStringOrNull(),
			rating = RATING_UNKNOWN,
			isNsfw = contentRating == "erotica" || contentRating == "porn",
			contentRating = contentRating,
		)
	}

	private fun pickLocalized(node: JsonValue?): String? {
		val obj = node.asObj() ?: return node.asStringOrNull()
		obj[preferredLanguage].asStringOrNull()?.let { return it }
		obj["en"].asStringOrNull()?.let { return it }
		obj["pt-br"].asStringOrNull()?.let { return it }
		return obj.fields.values.firstOrNull { it.asStringOrNull()?.isNotBlank() == true }.asStringOrNull()
	}

	/** Fetches the whole chapter feed, following pagination, and maps it to [RemoteChapter]s. */
	private fun feed(mangaId: String): Pair<List<RemoteChapter>, Boolean> {
		val collected = ArrayList<RemoteChapter>()
		var offset = 0
		var complete = true
		var iterations = 0
		while (true) {
			val params = buildList {
				add("limit" to FEED_PAGE_SIZE.toString())
				add("offset" to offset.toString())
				add("order[volume]" to "asc")
				add("order[chapter]" to "asc")
				add("includes[]" to "scanlation_group")
				add("includeExternalUrl" to "0")
				languages.forEach { add("translatedLanguage[]" to it) }
				addAll(contentRatingParams(includeNsfw = true))
			}
			val json = api("/manga/$mangaId/feed", params)
			val data = json.arr("data")?.items.orEmpty().mapNotNull { it.asObj() }
			data.forEach { entity -> parseChapter(entity)?.let(collected::add) }
			val total = json.int("total") ?: data.size
			offset += FEED_PAGE_SIZE
			iterations++
			if (data.isEmpty() || offset >= total || iterations >= MAX_FEED_PAGES) {
				if (offset < total) complete = false
				break
			}
		}
		val deduped = collected
			.distinctBy { it.remoteId }
			.sortedWith(compareBy({ it.volume }, { it.number }, { it.uploadedAt }))
		if (deduped.isEmpty()) Log.i(TAG, "Manga $mangaId has no chapters in $languages")
		return deduped to complete
	}

	private fun parseChapter(entity: JsonValue.Obj): RemoteChapter? {
		val id = entity["id"].asStringOrNull() ?: return null
		val attributes = entity["attributes"].asObj() ?: return null
		if (attributes["externalUrl"].asStringOrNull() != null) return null // hosted elsewhere
		if (attributes["isUnavailable"].asStringOrNull() == "true") return null
		val number = attributes["chapter"].asStringOrNull()?.toFloatOrNull() ?: 0f
		val volume = attributes["volume"].asStringOrNull()?.toIntOrNull() ?: 0
		val title = attributes["title"].asStringOrNull().orEmpty()
		val display = buildString {
			if (volume > 0) append("Vol. ").append(volume).append(' ')
			if (number > 0f) append("Ch. ").append(formatNumber(number)).append(' ')
			if (title.isNotBlank()) append(title)
		}.trim().ifEmpty { "Chapter" }
		val scanlator = entity["relationships"].asArr()?.items.orEmpty()
			.mapNotNull { it.asObj() }
			.firstOrNull { it["type"].asStringOrNull() == "scanlation_group" }
			?.get("attributes")?.asObj()?.get("name")?.asStringOrNull()
		return RemoteChapter(
			remoteId = id,
			title = display,
			url = "$PUBLIC_BASE/chapter/$id",
			number = number,
			volume = volume,
			uploadedAt = parseTimestamp(attributes["publishAt"].asStringOrNull()),
			pagesCount = attributes["pages"].asIntOrNull() ?: 0,
			scanlator = scanlator,
			kind = ChapterKind.PAGES,
		)
	}

	private fun parseTimestamp(value: String?): Long {
		if (value.isNullOrBlank()) return 0L
		return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrDefault(0L)
	}

	private fun formatNumber(number: Float): String =
		if (number == number.toInt().toFloat()) number.toInt().toString() else number.toString()

	/** Number of chapters the API reports for a title, used by the Hub result badge. */
	fun chapterCount(remoteId: String): Int = runCatching {
		val json = api("/manga/$remoteId/aggregate", listOf("translatedLanguage[]" to languages.first()))
		json.obj("volumes")?.fields?.values.orEmpty().sumOf { volume ->
			volume.asObj()?.get("chapters").asObj()?.size ?: 0
		}
	}.getOrDefault(0)

	companion object {

		const val SOURCE_ID = "MANGADEX"

		val DEFAULT_BASES = listOf(
			"https://api.mangadex.org",
			// A community mirror is not officially operated; the second entry only exists so that a
			// self-hosted deployment can be configured without a code change. If both fail the UI
			// reports the source as unavailable instead of retrying forever.
			"https://api.mangadex.dev",
		)

		val DEFAULT_LANGUAGES = listOf("en", "pt-br")

		const val PUBLIC_BASE = "https://mangadex.org"
		const val COVER_BASE = "https://uploads.mangadex.org"
		const val REFERER = "https://mangadex.org/"

		private const val FEED_PAGE_SIZE = 500
		private const val MAX_FEED_PAGES = 20
		private const val TAG = "MangaDex"

		private val LIST_INCLUDES = listOf(
			"includes[]" to "cover_art",
			"includes[]" to "author",
			"includes[]" to "artist",
		)
	}
}
