/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Natural ("human") string comparison: `chapter 2` sorts before `chapter 10`, and pure numeric
 * names sort numerically. Needed for page names inside archives and for chapter folders, which are
 * almost always numbered but rarely zero-padded.
 */
object NaturalOrder : Comparator<String> {

	override fun compare(a: String, b: String): Int {
		var i = 0
		var j = 0
		while (i < a.length && j < b.length) {
			val ca = a[i]
			val cb = b[j]
			if (ca.isDigit() && cb.isDigit()) {
				val startA = i
				while (i < a.length && a[i].isDigit()) i++
				val startB = j
				while (j < b.length && b[j].isDigit()) j++
				val numA = a.substring(startA, i).trimStart('0')
				val numB = b.substring(startB, j).trimStart('0')
				val byLength = numA.length.compareTo(numB.length)
				if (byLength != 0) return byLength
				val byValue = numA.compareTo(numB)
				if (byValue != 0) return byValue
			} else {
				val la = ca.lowercaseChar()
				val lb = cb.lowercaseChar()
				if (la != lb) return la.compareTo(lb)
				i++
				j++
			}
		}
		return (a.length - i).compareTo(b.length - j)
	}
}

fun humanFileSize(bytes: Long): String {
	if (bytes < 1024) return "$bytes B"
	val units = arrayOf("KB", "MB", "GB", "TB")
	var value = bytes.toDouble() / 1024.0
	var unitIndex = 0
	while (value >= 1024 && unitIndex < units.lastIndex) {
		value /= 1024.0
		unitIndex++
	}
	return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex])
}

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun formatDateTime(epochMillis: Long): String {
	if (epochMillis <= 0L) return "—"
	return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateFormatter)
}

fun formatPercent(percent: Float): String = when {
	percent < 0f -> "—"
	percent >= 0.99999f -> "100%"
	else -> "${(percent * 100).toInt()}%"
}

/** Parses a chapter number out of a chapter name such as "Chapter 12.5" or "012". */
fun parseChapterNumber(name: String): Float {
	val match = Regex("""(\d+(?:[.,]\d+)?)""").find(name) ?: return 0f
	return match.groupValues[1].replace(',', '.').toFloatOrNull() ?: 0f
}
