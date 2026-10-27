/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * The contract every online source implements. It mirrors the subset of
 * `org.koitharu.kotatsu.parsers.MangaParser` that a reader needs, but it is synchronous and free of
 * Android types, which is what makes it usable from Swing and testable against a local server.
 */
package org.koitharu.kotatsu.desktop.sources

import org.koitharu.kotatsu.desktop.sources.net.HttpRequestSpec

interface MangaSource {

	val descriptor: SourceDescriptor

	val capabilities: SourceCapabilities

	/** Base URL currently in use (the first mirror unless one failed). */
	val baseUrl: String

	/** Overrides the built-in base URL list, e.g. for a self-hosted mirror. */
	fun configureBaseUrl(url: String?) = Unit

	/** Most recent headlines of the source, used by the Explore tab. */
	fun popular(page: Int = 0): SourceOutcome<List<RemoteManga>>

	fun search(
		query: String,
		page: Int = 0,
		filters: SearchFilters = SearchFilters(),
	): SourceOutcome<List<RemoteManga>>

	fun details(remoteId: String): SourceOutcome<RemoteMangaDetails>

	/** Image URLs of a chapter (only for [ChapterKind.PAGES]). */
	fun pages(chapter: RemoteChapter): SourceOutcome<List<RemotePage>>

	/** Extra headers needed by the image host (Referer, User-Agent, cookies). */
	fun imageHeaders(page: RemotePage): Map<String, String> = emptyMap()

	/**
	 * Cheap round-trip used by the Hub's availability badge. Never downloads images and asks for a
	 * single result, so checking 20 sources does not hammer anybody.
	 */
	fun checkAvailability(): SourceOutcome<SourceAvailability> {
		val started = System.currentTimeMillis()
		val probe = popular(0)
		val elapsed = System.currentTimeMillis() - started
		return when (probe) {
			is SourceOutcome.Success -> SourceOutcome.Success(
				SourceAvailability(
					available = true,
					checkedAt = System.currentTimeMillis(),
					latencyMs = elapsed,
				),
			)

			is SourceOutcome.Failure -> SourceOutcome.Success(
				SourceAvailability(
					available = false,
					checkedAt = System.currentTimeMillis(),
					latencyMs = elapsed,
					failure = probe.failure,
				),
			)
		}
	}

	/** Request used to fetch one page image; exposed so the cache can prefetch with the right UA. */
	fun imageRequest(page: RemotePage, timeoutMs: Long = HttpRequestSpec.DEFAULT_TIMEOUT_MS): HttpRequestSpec =
		HttpRequestSpec(
			url = page.url,
			headers = imageHeaders(page),
			timeoutMs = timeoutMs,
			userAgent = imageHeaders(page)["User-Agent"] ?: HttpRequestSpec.DEFAULT_USER_AGENT,
		)
}
