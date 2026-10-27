/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Preferences are stored as a flat key -> JSON value map, mirroring how the Android app dumps its
 * SharedPreferences into a backup (`BackupSection.SETTINGS`). Keys that the desktop build does not
 * understand are still preserved when a backup is imported, so that information is not lost.
 */
package org.koitharu.kotatsu.desktop.data.store

import org.koitharu.kotatsu.desktop.core.i18n.AppLanguage
import org.koitharu.kotatsu.desktop.core.json.JsonValue
import org.koitharu.kotatsu.desktop.core.json.asBooleanOrNull
import org.koitharu.kotatsu.desktop.core.json.asFloatOrNull
import org.koitharu.kotatsu.desktop.core.json.asIntOrNull
import org.koitharu.kotatsu.desktop.core.json.asObj
import org.koitharu.kotatsu.desktop.core.json.asStringOrNull
import org.koitharu.kotatsu.desktop.core.json.boolJson
import org.koitharu.kotatsu.desktop.core.json.floatJson
import org.koitharu.kotatsu.desktop.core.json.intJson
import org.koitharu.kotatsu.desktop.core.json.jsonObject
import org.koitharu.kotatsu.desktop.core.json.stringJson
import org.koitharu.kotatsu.desktop.data.model.ListSortOrder
import org.koitharu.kotatsu.desktop.data.model.ReaderMode
import java.nio.file.Path

enum class ThemeMode {
	DARK,
	LIGHT,
	;

	companion object {

		fun fromName(name: String?): ThemeMode? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
	}
}

enum class ReaderBackground {
	BLACK,
	GRAY,
	WHITE,
	;

	companion object {

		fun fromName(name: String?): ReaderBackground? =
			entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
	}
}

class SettingsStore(file: Path) : PersistentStore(file) {

	private val values = LinkedHashMap<String, JsonValue>()

	override fun onLoad(root: JsonValue?) {
		values.clear()
		val map = root?.asObj()?.let { obj -> obj["values"].asObj() ?: obj } // tolerate a flat legacy file
		map?.fields?.forEach { (key, value) -> values[key] = value }
	}

	override fun buildDocument(): JsonValue = jsonObject {
		put("schema_version", intJson(SCHEMA_VERSION))
		put("values", jsonObject { values.forEach { (key, value) -> put(key, value) } })
	}

	fun snapshot(): Map<String, JsonValue> = locked { LinkedHashMap(values) }

	fun putRaw(key: String, value: JsonValue) = locked {
		values[key] = value
		markDirty()
	}

	/**
	 * Applies values coming from an imported backup. Only keys in [knownKeys] are interpreted as
	 * settings by the desktop build; every other key is preserved verbatim so that a later
	 * version (or the Android app, if the backup is taken back) still sees it.
	 *
	 * @return number of known keys that were applied.
	 */
	fun applyImported(imported: Map<String, JsonValue>, knownKeys: Set<String>): Int {
		var applied = 0
		locked {
			imported.forEach { (key, value) ->
				if (key in EXCLUDED_KEYS) return@forEach
				values[key] = value
				if (key in knownKeys) applied++
			}
			markDirty()
		}
		return applied
	}

	private fun string(key: String): String? = locked { values[key].asStringOrNull() }

	private fun bool(key: String, default: Boolean): Boolean =
		locked { values[key].asBooleanOrNull() ?: default }

	private fun int(key: String, default: Int): Int = locked { values[key].asIntOrNull() ?: default }

	private fun float(key: String, default: Float): Float =
		locked { values[key].asFloatOrNull() ?: default }

	var language: AppLanguage
		get() = AppLanguage.fromTag(string(KEY_APP_LANGUAGE)) ?: AppLanguage.ENGLISH
		set(value) = putRaw(KEY_APP_LANGUAGE, stringJson(value.tag))

	var theme: ThemeMode
		get() = ThemeMode.fromName(string(KEY_THEME)) ?: ThemeMode.DARK
		set(value) = putRaw(KEY_THEME, stringJson(value.name))

	/**
	 * Interface scale (1.0 = 100 %). Every font of the application is derived from it, which is
	 * how the UI stays usable on a 4K screen or on a scaled Windows display.
	 */
	var uiScale: Float
		get() = float(KEY_UI_SCALE, 1f).coerceIn(0.75f, 2.5f)
		set(value) = putRaw(KEY_UI_SCALE, floatJson(value.coerceIn(0.75f, 2.5f)))

	var defaultReaderMode: ReaderMode
		get() = ReaderMode.fromName(string(KEY_READER_MODE)) ?: ReaderMode.STANDARD
		set(value) = putRaw(KEY_READER_MODE, stringJson(value.name))

	var readerBackground: ReaderBackground
		get() = ReaderBackground.fromName(string(KEY_READER_BACKGROUND)) ?: ReaderBackground.BLACK
		set(value) = putRaw(KEY_READER_BACKGROUND, stringJson(value.name))

	var readerPageGap: Int
		get() = int(KEY_READER_PAGE_GAP, 8).coerceIn(0, 96)
		set(value) = putRaw(KEY_READER_PAGE_GAP, intJson(value.coerceIn(0, 96)))

	var readerPreload: Boolean
		get() = bool(KEY_READER_PRELOAD, true)
		set(value) = putRaw(KEY_READER_PRELOAD, boolJson(value))

	var readerSaveProgress: Boolean
		get() = bool(KEY_READER_SAVE_PROGRESS, true)
		set(value) = putRaw(KEY_READER_SAVE_PROGRESS, boolJson(value))

	var readerScrollZoom: Boolean
		get() = bool(KEY_READER_SCROLL_ZOOM, true)
		set(value) = putRaw(KEY_READER_SCROLL_ZOOM, boolJson(value))

	var readerZoomStep: Float
		get() = float(KEY_READER_ZOOM_STEP, 0.1f).coerceIn(0.02f, 0.5f)
		set(value) = putRaw(KEY_READER_ZOOM_STEP, floatJson(value))

	var librarySortOrder: ListSortOrder
		get() = ListSortOrder.fromName(string(KEY_LIBRARY_SORT)) ?: ListSortOrder.NEWEST
		set(value) = putRaw(KEY_LIBRARY_SORT, stringJson(value.name))

	var lastImportDirectory: String?
		get() = string(KEY_LAST_IMPORT_DIR)
		set(value) = putRaw(KEY_LAST_IMPORT_DIR, stringJson(value))

	// Window geometry (desktop-only convenience, not part of any upstream format)
	var windowWidth: Int
		get() = int(KEY_WINDOW_WIDTH, 1280)
		set(value) = putRaw(KEY_WINDOW_WIDTH, intJson(value))

	var windowHeight: Int
		get() = int(KEY_WINDOW_HEIGHT, 860)
		set(value) = putRaw(KEY_WINDOW_HEIGHT, intJson(value))

	var windowMaximized: Boolean
		get() = bool(KEY_WINDOW_MAXIMIZED, false)
		set(value) = putRaw(KEY_WINDOW_MAXIMIZED, boolJson(value))

	var windowX: Int
		get() = int(KEY_WINDOW_X, Int.MIN_VALUE)
		set(value) = putRaw(KEY_WINDOW_X, intJson(value))

	var windowY: Int
		get() = int(KEY_WINDOW_Y, Int.MIN_VALUE)
		set(value) = putRaw(KEY_WINDOW_Y, intJson(value))

	companion object {

		const val SCHEMA_VERSION = 1

		const val KEY_APP_LANGUAGE = "app_language"
		const val KEY_THEME = "app_theme"
		const val KEY_UI_SCALE = "ui_scale"
		const val KEY_READER_MODE = "reader_mode"
		const val KEY_READER_BACKGROUND = "reader_background"
		const val KEY_READER_PAGE_GAP = "reader_page_gap"
		const val KEY_READER_PRELOAD = "reader_preload"
		const val KEY_READER_SAVE_PROGRESS = "reader_save_progress"
		const val KEY_READER_SCROLL_ZOOM = "reader_scroll_zoom"
		const val KEY_READER_ZOOM_STEP = "reader_zoom_step"
		const val KEY_LIBRARY_SORT = "list_sort_order"
		const val KEY_LAST_IMPORT_DIR = "last_import_dir"
		const val KEY_WINDOW_WIDTH = "window_width"
		const val KEY_WINDOW_HEIGHT = "window_height"
		const val KEY_WINDOW_MAXIMIZED = "window_maximized"
		const val KEY_WINDOW_X = "window_x"
		const val KEY_WINDOW_Y = "window_y"

		/** Keys never imported, for the same reasons upstream excludes them from its own dumps. */
		val EXCLUDED_KEYS = setOf(
			"app_password",
			"proxy_password",
			"proxy_login",
			"incognito_mode",
		)

		val KNOWN_KEYS = setOf(
			KEY_APP_LANGUAGE,
			KEY_THEME,
			KEY_UI_SCALE,
			KEY_READER_MODE,
			KEY_READER_BACKGROUND,
			KEY_READER_PAGE_GAP,
			KEY_READER_PRELOAD,
			KEY_READER_SAVE_PROGRESS,
			KEY_READER_SCROLL_ZOOM,
			KEY_READER_ZOOM_STEP,
			KEY_LIBRARY_SORT,
		)
	}
}
