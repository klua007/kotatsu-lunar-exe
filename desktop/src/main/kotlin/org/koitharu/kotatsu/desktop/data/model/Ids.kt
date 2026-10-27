/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.data.model

/**
 * Deterministic 64-bit identifier (FNV-1a) for content that has no server-side id, e.g. a local
 * archive page or chapter. Stability matters: reading progress is keyed by these ids and must
 * survive restarts and re-imports of the same file.
 */
fun stableId(vararg parts: String): Long {
	var hash = -0x340d631b7bdddcdbL // FNV offset basis
	for (part in parts) {
		for (c in part) {
			hash = hash xor c.code.toLong()
			hash *= 0x100000001b3L
		}
		hash = hash xor 0x1fL
		hash *= 0x100000001b3L
	}
	return if (hash == Long.MIN_VALUE) 0L else kotlin.math.abs(hash)
}
