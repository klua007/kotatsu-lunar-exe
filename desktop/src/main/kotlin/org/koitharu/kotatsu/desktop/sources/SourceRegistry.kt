/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * The catalogue of sources ("Hub"): which exist, which are enabled/pinned, when they were last
 * reachable and why a source is unavailable. This class owns no UI; the Hub screen renders it and
 * the tests drive it directly.
 *
 * Unavailable entries are shown on purpose: the Android app ships ~900 parsers, and telling the
 * user *why* a given site is missing from this build is more useful than hiding it. See
 * docs/SOURCES.md for the exact split.
 */
package org.koitharu.kotatsu.desktop.sources

import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.core.i18n.K
import org.koitharu.kotatsu.desktop.data.store.OpdsCatalog
import org.koitharu.kotatsu.desktop.data.store.SourcesStore
import org.koitharu.kotatsu.desktop.sources.mangadex.MangaDexSource
import org.koitharu.kotatsu.desktop.sources.net.HttpRequestSpec
import org.koitharu.kotatsu.desktop.sources.net.HttpTransport
import org.koitharu.kotatsu.desktop.sources.net.JdkHttpTransport
import org.koitharu.kotatsu.desktop.sources.opds.OpdsSource

data class SourceEntry(
	val descriptor: SourceDescriptor,
	val enabled: Boolean,
	val pinned: Boolean,
	val availability: SourceAvailability?,
) {
	val isAvailable: Boolean get() = descriptor.isAvailable
}

class SourceRegistry(
	private val store: SourcesStore,
	private val transport: HttpTransport = JdkHttpTransport(),
	private val timeoutMs: Long = HttpRequestSpec.DEFAULT_TIMEOUT_MS,
	private val extraSources: List<MangaSource> = emptyList(),
) {

	private val instances = LinkedHashMap<String, MangaSource>()

	init {
		rebuild()
	}

	/** Re-instantiates the sources (after adding/removing a catalogue or changing a mirror). */
	fun rebuild() {
		instances.clear()
		val mangadex = MangaDexSource(transport = transport, timeoutMs = timeoutMs)
		store.stateOf(MangaDexSource.SOURCE_ID)?.baseUrlOverride?.let(mangadex::configureBaseUrl)
		instances[mangadex.descriptor.id] = mangadex
		store.catalogs().forEach { catalog ->
			instances[catalog.id] = OpdsSource(
				transport = transport,
				id = catalog.id,
				title = catalog.title,
				rootUrl = catalog.url,
				timeoutMs = timeoutMs,
			)
		}
		extraSources.forEach { source -> instances[source.descriptor.id] = source }
	}

	/** Every source this build can talk to (enabled or not). */
	fun sources(): List<MangaSource> = instances.values.toList()

	fun enabledSources(): List<MangaSource> = instances.values
		.filter { isEnabled(it.descriptor) }
		.sortedByDescending { isPinned(it.descriptor.id) }

	fun get(id: String): MangaSource? = instances[id]

	fun isEnabled(descriptor: SourceDescriptor): Boolean =
		store.stateOf(descriptor.id)?.enabled ?: descriptor.enabledByDefault

	fun isPinned(id: String): Boolean = store.stateOf(id)?.pinned ?: false

	fun setEnabled(id: String, enabled: Boolean) {
		if (instances[id] == null) return
		store.setEnabled(id, enabled)
	}

	fun setPinned(id: String, pinned: Boolean) {
		if (instances[id] == null) return
		store.setPinned(id, pinned)
	}

	fun setBaseUrlOverride(id: String, baseUrl: String?) {
		store.setBaseUrlOverride(id, baseUrl)
		instances[id]?.configureBaseUrl(baseUrl)
	}

	fun baseUrlOverride(id: String): String? = store.stateOf(id)?.baseUrlOverride

	/** Enabled, available sources first, pinned on top; unavailable ones last, for the Hub list. */
	fun catalog(): List<SourceEntry> {
		val implemented = instances.values.map { source ->
			val descriptor = source.descriptor
			SourceEntry(
				descriptor = descriptor,
				enabled = isEnabled(descriptor),
				pinned = isPinned(descriptor.id),
				availability = store.stateOf(descriptor.id)?.availability,
			)
		}
		val rows = implemented + UNAVAILABLE_DESCRIPTORS.map { descriptor ->
			SourceEntry(descriptor, enabled = false, pinned = false, availability = null)
		}
		return rows.sortedWith(
			compareByDescending<SourceEntry> { it.pinned }
				.thenByDescending { it.enabled }
				.thenByDescending { it.isAvailable }
				.thenBy { it.descriptor.title.lowercase() },
		)
	}

	/** Runs the cheap availability probe and remembers the result. */
	fun checkAvailability(id: String): SourceOutcome<SourceAvailability> {
		val source = instances[id]
			?: return SourceOutcome.Failure(SourceFailure.unavailable("unknown source '$id'"))
		val outcome = source.checkAvailability()
		when (outcome) {
			is SourceOutcome.Success -> {
				store.setAvailability(id, outcome.value)
				Log.i(TAG, "$id availability: ${if (outcome.value.available) "ok" else "down"} (${outcome.value.latencyMs} ms)")
			}

			is SourceOutcome.Failure -> Log.w(TAG, "$id availability probe failed: ${outcome.failure.kind}")
		}
		return outcome
	}

	fun availability(id: String): SourceAvailability? = store.stateOf(id)?.availability

	// --- OPDS catalogues -----------------------------------------------------------------------

	fun addOpdsCatalog(title: String, url: String): SourceOutcome<OpdsSource> {
		val trimmed = url.trim()
		if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
			return SourceOutcome.Failure(SourceFailure.invalidResponse("catalogue URL must start with http(s)://"))
		}
		val catalog = store.addCatalog(title, trimmed)
		rebuild()
		val source = instances[catalog.id] as? OpdsSource
			?: return SourceOutcome.Failure(SourceFailure.unavailable("catalogue could not be registered"))
		return SourceOutcome.Success(source)
	}

	fun removeOpdsCatalog(id: String) {
		if (store.catalogs().none { it.id == id }) return
		store.removeCatalog(id)
		rebuild()
	}

	fun catalogs(): List<OpdsCatalog> = store.catalogs()

	companion object {

		private const val TAG = "Sources"

		/**
		 * Sources that exist upstream (kotatsu-parsers-redo) but are not usable here. They are
		 * listed in the Hub with an explicit reason instead of being silently omitted.
		 */
		val UNAVAILABLE_DESCRIPTORS: List<SourceDescriptor> = listOf(
			SourceDescriptor(
				id = "MANGAPARK",
				title = "MangaPark",
				language = "en",
				kind = SourceKind.PARSER_PACK,
				unavailableReason = K.SOURCES_REASON_PARSER_PACK,
			),
			SourceDescriptor(
				id = "BATOTO",
				title = "Bato.to",
				language = "multi",
				kind = SourceKind.PARSER_PACK,
				unavailableReason = K.SOURCES_REASON_CLOUDFLARE,
			),
			SourceDescriptor(
				id = "MANGAFIRE",
				title = "MangaFire",
				language = "multi",
				kind = SourceKind.PARSER_PACK,
				unavailableReason = K.SOURCES_REASON_JAVASCRIPT,
			),
			SourceDescriptor(
				id = "COMICK",
				title = "Comick",
				language = "multi",
				kind = SourceKind.PARSER_PACK,
				unavailableReason = K.SOURCES_REASON_PARSER_PACK,
			),
		)
	}
}
