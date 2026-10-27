/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: logic ported from the Android app
 *   app/src/main/kotlin/org/koitharu/kotatsu/list/domain/ReadingProgress.kt
 *   app/src/main/kotlin/org/koitharu/kotatsu/reader/ui/ReaderViewModel.kt (computePercent)
 * Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Progress semantics are kept identical to upstream so that an imported Android backup shows the
 * same percentages: `percent` covers the whole manga (0..1), the page currently on screen counts as
 * read, and 1.0 means "finished".
 */
package org.koitharu.kotatsu.desktop.reader

import org.koitharu.kotatsu.desktop.data.model.PROGRESS_COMPLETED
import org.koitharu.kotatsu.desktop.data.model.PROGRESS_NONE

object ReadingProgress {

	const val PROGRESS_NONE = org.koitharu.kotatsu.desktop.data.model.PROGRESS_NONE
	private const val COMPLETED_THRESHOLD = 0.99999f

	fun isValid(percent: Float): Boolean = percent in 0f..1f || percent == PROGRESS_NONE

	fun isCompleted(percent: Float): Boolean = percent >= COMPLETED_THRESHOLD

	fun percentToString(percent: Float): String = when {
		!isValid(percent) || percent == PROGRESS_NONE -> "0%"
		isCompleted(percent) -> "100%"
		else -> "${(percent * 100f).toInt()}%"
	}

	/** Fraction of a chapter that has been read, counting the current page as read. */
	fun chapterFraction(page: Int, pagesCount: Int): Float {
		if (pagesCount <= 0) return 0f
		val clamped = page.coerceIn(0, pagesCount - 1)
		return ((clamped + 1).toFloat() / pagesCount.toFloat()).coerceIn(0f, 1f)
	}

	/** Whole-manga progress, mirroring upstream `computePercent`. */
	fun percentOf(chapterIndex: Int, chaptersCount: Int, page: Int, pagesCount: Int): Float {
		if (chaptersCount <= 0) return PROGRESS_NONE
		if (chapterIndex < 0) return PROGRESS_NONE
		val index = chapterIndex.coerceAtMost(chaptersCount - 1)
		val chapterFraction = chapterFraction(page, pagesCount)
		val percent = (index + chapterFraction) / chaptersCount.toFloat()
		return percent.coerceIn(0f, PROGRESS_COMPLETED)
	}

	/** How many chapters are considered read for a given whole-manga percent. */
	fun estimatedReadChapters(percent: Float, chaptersCount: Int): Int {
		if (chaptersCount <= 0 || !isValid(percent) || percent == PROGRESS_NONE) return 0
		return if (isCompleted(percent)) {
			chaptersCount
		} else {
			(percent * chaptersCount).toInt().coerceIn(0, chaptersCount)
		}
	}

	/**
	 * Where to resume: the saved page inside a chapter, or the first page of the chapter whose
	 * index matches the saved percent when the chapter id is not known (e.g. after a re-import).
	 *
	 * @return pair of chapter index and page index, or null when there is nothing to resume.
	 */
	fun resumePosition(
		percent: Float,
		chaptersCount: Int,
		savedPage: Int,
		pagesCount: Int,
	): Pair<Int, Int>? {
		if (chaptersCount <= 0) return null
		if (percent == PROGRESS_NONE && savedPage <= 0) return null
		val chaptersRead = estimatedReadChapters(percent, chaptersCount)
		val chapterIndex = (chaptersRead.coerceAtLeast(0)).coerceIn(0, chaptersCount - 1)
		val page = if (pagesCount > 0) savedPage.coerceIn(0, pagesCount - 1) else 0
		return chapterIndex to page
	}

	/** Fraction of a vertical/webtoon strip, from a scroll offset in pixels. */
	fun scrollFraction(offset: Int, viewHeight: Int, contentHeight: Int): Float {
		if (contentHeight <= viewHeight) return 1f
		val max = (contentHeight - viewHeight).toFloat()
		return (offset.toFloat() / max).coerceIn(0f, 1f)
	}

	/** Inverse of [scrollFraction]. */
	fun scrollOffsetFor(fraction: Float, viewHeight: Int, contentHeight: Int): Int {
		if (contentHeight <= viewHeight) return 0
		return ((contentHeight - viewHeight) * fraction.coerceIn(0f, 1f)).toInt()
	}

	val NONE: Float = PROGRESS_NONE
}
