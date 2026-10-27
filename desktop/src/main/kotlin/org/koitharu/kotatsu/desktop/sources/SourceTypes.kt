/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Common types of the desktop source layer.
 *
 * The Android app talks to ~900 sites through `kotatsu-parsers-redo`. That library is a Kotlin/JVM
 * project, but only its *generated* artefact (KSP builds `MangaParserSource`) is usable, and it
 * needs an implementation of `MangaLoaderContext` (WebView, Bitmap, cookies, config) that does not
 * exist outside Android. `docs/SOURCES.md` describes the three-tier model implemented here:
 *
 *   1. LOCAL        – the user's own files (always available)
 *   2. NATIVE       – sources implemented directly on the JVM, no Android dependencies
 *   3. PARSER_PACK  – the upstream parser library, loaded when its jar is present (optional pack)
 */
package org.koitharu.kotatsu.desktop.sources

import org.koitharu.kotatsu.desktop.data.model.RATING_UNKNOWN

/** Where a source comes from. */
enum class SourceKind {
	LOCAL,
	NATIVE,
	PARSER_PACK,
}

/**
 * Immutable description of a source, safe to show in the UI before any network call happens.
 *
 * @param id stable identifier; this is what gets written into `Manga.source`, the backup file and
 *   the (enabled/pinned) settings, so it must never change.
 */
data class SourceDescriptor(
	val id: String,
	val title: String,
	val language: String,
	val kind: SourceKind,
	val nsfw: Boolean = false,
	val enabledByDefault: Boolean = false,
	/** Human readable reason shown in the Hub when the source cannot run on this machine. */
	val unavailableReason: String? = null,
) {
	val isLocal: Boolean get() = kind == SourceKind.LOCAL
	val isAvailable: Boolean get() = unavailableReason == null
}

/** A title as returned by a source, before it becomes a library entity. */
data class RemoteManga(
	val sourceId: String,
	/** Identifier inside the source (MangaDex UUID, parser slug/id, …). */
	val remoteId: String,
	val title: String,
	val url: String,
	val altTitles: List<String> = emptyList(),
	val coverUrl: String? = null,
	val description: String? = null,
	val authors: List<String> = emptyList(),
	val tags: List<String> = emptyList(),
	val state: String? = null,
	val rating: Float = RATING_UNKNOWN,
	val isNsfw: Boolean = false,
	val contentRating: String? = null,
)

/**
 * How the pages of a chapter are obtained.
 *
 * PAGES   – the source answers with an individual image URL per page (MangaDex, most sites)
 * ARCHIVE – the source offers a downloadable archive (CBZ/ZIP), e.g. an OPDS acquisition link
 */
enum class ChapterKind { PAGES, ARCHIVE }

data class RemoteChapter(
	val remoteId: String,
	val title: String,
	val url: String,
	val number: Float = 0f,
	val volume: Int = 0,
	val uploadedAt: Long = 0L,
	val pagesCount: Int = 0,
	val scanlator: String? = null,
	val kind: ChapterKind = ChapterKind.PAGES,
)

/** A title with its chapter list, as returned by [MangaSource.details]. */
data class RemoteMangaDetails(
	val manga: RemoteManga,
	val chapters: List<RemoteChapter>,
	/** True when the source cannot produce more chapters than the ones here (e.g. paginated feeds). */
	val chaptersComplete: Boolean = true,
)

/** One reader page served by a remote source. */
data class RemotePage(
	val url: String,
	/** Value for the `Referer` header, required by several image hosts. */
	val referer: String? = null,
)

/** Filters that every source understands; sources ignore what they cannot do. */
data class SearchFilters(
	val tags: List<String> = emptyList(),
	val states: List<String> = emptyList(),
	val includeNsfw: Boolean = false,
	val language: String? = null,
)

/** What a source can search for; shown in the Hub so filters are never silently ignored. */
data class SourceCapabilities(
	val supportsSearch: Boolean = true,
	val supportsTagFilter: Boolean = false,
	val supportsStateFilter: Boolean = false,
	val supportedLanguages: List<String> = emptyList(),
)

data class SourceAvailability(
	val available: Boolean,
	val checkedAt: Long,
	val latencyMs: Long,
	val failure: SourceFailure? = null,
)
