/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.core.i18n

import java.util.Locale

enum class AppLanguage(val tag: String) {
	ENGLISH("en"),
	PORTUGUESE_BR("pt-BR"),
	;

	val displayName: String
		get() = when (this) {
			ENGLISH -> "English"
			PORTUGUESE_BR -> "Português (Brasil)"
		}

	val locale: Locale
		get() = when (this) {
			ENGLISH -> Locale.ENGLISH
			PORTUGUESE_BR -> Locale.forLanguageTag("pt-BR")
		}

	companion object {

		/** Follows the OS locale as far as our tables allow, defaulting to English. */
		fun fromSystemOrDefault(): AppLanguage {
			val tag = Locale.getDefault().toLanguageTag().lowercase(Locale.ROOT)
			return when {
				tag.startsWith("pt") -> PORTUGUESE_BR
				tag.startsWith("en") -> ENGLISH
				else -> ENGLISH
			}
		}

		fun fromTag(tag: String?): AppLanguage? = entries.find {
			it.tag.equals(tag, ignoreCase = true) || it.name.equals(tag, ignoreCase = true)
		}
	}
}

/**
 * Very small translation layer.
 *
 * The Android app ships ~60 locales as Android string resources; those stay untouched in `app/`
 * and are still used by the Android build (see docs/FEATURE_MATRIX.md). The desktop build ships
 * English and Brazilian Portuguese, which is what this table holds. Keys are named after the
 * upstream resource names where an equivalent exists, so a future migration of the full resource
 * set is mechanical.
 */
object I18n {

	val DEFAULT_LANGUAGE = AppLanguage.ENGLISH

	var language: AppLanguage = DEFAULT_LANGUAGE
		private set

	fun setLanguage(language: AppLanguage) {
		this.language = language
		Locale.setDefault(language.locale)
	}

	operator fun get(key: String): String {
		val row = Strings.TRANSLATIONS[key] ?: return key
		val index = language.ordinal
		return row.getOrNull(index) ?: row.firstOrNull() ?: key
	}

	fun format(key: String, vararg args: Any?): String {
		val template = get(key)
		return runCatching { String.format(language.locale, template, *args) }.getOrDefault(template)
	}
}

fun tr(key: String): String = I18n[key]
