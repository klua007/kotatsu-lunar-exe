/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Persistence for the online source layer (`sources.json`).
 *
 * Kept separate from `settings.json` because it is written whenever a source is enabled, pinned or
 * re-checked, and because an Android backup's `sources` section maps onto it one to one (see
 * docs/BACKUP_FORMAT.md). Nothing here is destructive: unknown keys written by a newer build are
 * preserved on load/save.
 */
package org.koitharu.kotatsu.desktop.data.store

import org.koitharu.kotatsu.desktop.core.json.JsonValue
import org.koitharu.kotatsu.desktop.core.json.add
import org.koitharu.kotatsu.desktop.core.json.asBooleanOrNull
import org.koitharu.kotatsu.desktop.core.json.asIntOrNull
import org.koitharu.kotatsu.desktop.core.json.asLongOrNull
import org.koitharu.kotatsu.desktop.core.json.asObj
import org.koitharu.kotatsu.desktop.core.json.asStringOrNull
import org.koitharu.kotatsu.desktop.core.json.boolJson
import org.koitharu.kotatsu.desktop.core.json.intJson
import org.koitharu.kotatsu.desktop.core.json.jsonArray
import org.koitharu.kotatsu.desktop.core.json.jsonObject
import org.koitharu.kotatsu.desktop.core.json.longJson
import org.koitharu.kotatsu.desktop.core.json.stringJson
import org.koitharu.kotatsu.desktop.data.model.stableId
import org.koitharu.kotatsu.desktop.sources.FailureKind
import org.koitharu.kotatsu.desktop.sources.SourceAvailability
import org.koitharu.kotatsu.desktop.sources.SourceFailure
import java.nio.file.Path

data class SourceState(
	/** null means "not chosen by the user"; the source descriptor's default applies. */
	val enabled: Boolean?,
	val pinned: Boolean,
	val baseUrlOverride: String? = null,
	val availability: SourceAvailability? = null,
)

data class OpdsCatalog(
	val id: String,
	val title: String,
	val url: String,
) {
	companion object {

		/** Stable id: adding the same URL twice reuses the entry instead of duplicating it. */
		fun idFor(url: String): String = "OPDS_" + stableId(url.trim().lowercase()).toString(36).uppercase()
	}
}

class SourcesStore(file: Path) : PersistentStore(file) {

	private val states = LinkedHashMap<String, SourceState>()
	private val catalogs = LinkedHashMap<String, OpdsCatalog>()

	override fun onLoad(root: JsonValue?) {
		states.clear()
		catalogs.clear()
		val obj = root?.asObj() ?: return
		obj["sources"].asObj()?.fields?.forEach { (id, value) ->
			val node = value.asObj() ?: return@forEach
			states[id] = SourceState(
				enabled = node["enabled"].asBooleanOrNull(),
				pinned = node["pinned"].asBooleanOrNull() ?: false,
				baseUrlOverride = node["base_url"].asStringOrNull()?.takeIf { it.isNotBlank() },
				availability = node["availability"].asObj()?.let { availability ->
					SourceAvailability(
						available = availability["available"].asBooleanOrNull() ?: false,
						checkedAt = availability["checked_at"].asLongOrNull() ?: 0L,
						latencyMs = availability["latency_ms"].asLongOrNull() ?: 0L,
						failure = availability["failure_kind"].asStringOrNull()?.let { kind ->
							SourceFailure(
								kind = runCatching { FailureKind.valueOf(kind) }.getOrDefault(FailureKind.OFFLINE),
								detail = availability["failure_detail"].asStringOrNull().orEmpty(),
								httpCode = availability["http_code"].asIntOrNull() ?: 0,
							)
						},
					)
				},
			)
		}
		obj["opds"].let { node ->
			(node as? JsonValue.Arr)?.items?.forEach { item ->
				val entry = item.asObj() ?: return@forEach
				val url = entry["url"].asStringOrNull()?.takeIf { it.isNotBlank() } ?: return@forEach
				val title = entry["title"].asStringOrNull()?.takeIf { it.isNotBlank() } ?: url
				val id = entry["id"].asStringOrNull() ?: OpdsCatalog.idFor(url)
				catalogs[id] = OpdsCatalog(id, title, url)
			}
		}
	}

	override fun buildDocument(): JsonValue = jsonObject {
		put("schema_version", intJson(SCHEMA_VERSION))
		put(
			"sources",
			jsonObject {
				states.forEach { (id, state) ->
					put(
						id,
						jsonObject {
							put("enabled", boolJson(state.enabled))
							put("pinned", boolJson(state.pinned))
							put("base_url", stringJson(state.baseUrlOverride))
							state.availability?.let { availability ->
								put(
									"availability",
									jsonObject {
										put("available", boolJson(availability.available))
										put("checked_at", longJson(availability.checkedAt))
										put("latency_ms", longJson(availability.latencyMs))
										put("failure_kind", stringJson(availability.failure?.kind?.name))
										put("failure_detail", stringJson(availability.failure?.detail))
										put("http_code", intJson(availability.failure?.httpCode))
									},
								)
							}
						},
					)
				}
			},
		)
		put(
			"opds",
			jsonArray {
				catalogs.values.forEach { catalog ->
					add(
						jsonObject {
							put("id", stringJson(catalog.id))
							put("title", stringJson(catalog.title))
							put("url", stringJson(catalog.url))
						},
					)
				}
			},
		)
	}

	fun stateOf(id: String): SourceState? = locked { states[id] }

	fun allStates(): Map<String, SourceState> = locked { LinkedHashMap(states) }

	fun hasState(id: String): Boolean = locked { states.containsKey(id) }

	fun setEnabled(id: String, enabled: Boolean) = locked {
		val previous = states[id]
		states[id] = (previous ?: SourceState(enabled = enabled, pinned = false)).copy(enabled = enabled)
		markDirty()
	}

	fun setPinned(id: String, pinned: Boolean) = locked {
		val previous = states[id]
		states[id] = (previous ?: SourceState(enabled = null, pinned = pinned)).copy(pinned = pinned)
		markDirty()
	}

	fun setBaseUrlOverride(id: String, baseUrl: String?) = locked {
		val previous = states[id]
		states[id] = (previous ?: SourceState(enabled = null, pinned = false)).copy(
			baseUrlOverride = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.startsWith("http") },
		)
		markDirty()
	}

	fun setAvailability(id: String, availability: SourceAvailability) = locked {
		val previous = states[id]
		states[id] = (previous ?: SourceState(enabled = null, pinned = false)).copy(availability = availability)
		markDirty()
	}

	fun removeState(id: String) = locked {
		if (states.remove(id) != null) markDirty()
	}

	fun catalogs(): List<OpdsCatalog> = locked { catalogs.values.toList() }

	fun addCatalog(title: String, url: String): OpdsCatalog = locked {
		val id = OpdsCatalog.idFor(url)
		val catalog = OpdsCatalog(id = id, title = title.trim().ifEmpty { url }, url = url.trim())
		catalogs[id] = catalog
		val previous = states[id]
		states[id] = (previous ?: SourceState(enabled = null, pinned = false)).copy(enabled = true)
		markDirty()
		catalog
	}

	fun removeCatalog(id: String) = locked {
		catalogs.remove(id)
		states.remove(id)
		markDirty()
	}

	/** Used by the backup importer; never overwrites an existing state. */
	fun importState(id: String, enabled: Boolean, pinned: Boolean): Boolean = locked {
		if (states.containsKey(id)) return@locked false
		states[id] = SourceState(enabled = enabled, pinned = pinned)
		markDirty()
		true
	}

	companion object {
		const val SCHEMA_VERSION = 1
	}
}
