/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: pure geometry for the reader canvas, kept out of the Swing component so it can be
 * unit-tested without a display. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.ui.reader

/** Rectangle where a page must be painted, in view coordinates. */
data class PagePlacement(
	val x: Int,
	val y: Int,
	val width: Int,
	val height: Int,
) {
	val right: Int get() = x + width
	val bottom: Int get() = y + height
}

object PageLayout {

	const val MIN_ZOOM = 0.5f
	const val MAX_ZOOM = 4f

	/**
	 * Computes where a page goes inside the viewport.
	 *
	 * The page is centred when it fits, and clamped so that it always covers the viewport when it is
	 * bigger (no empty border while panning). [panX] / [panY] are the accumulated drag offsets.
	 */
	fun place(
		imageWidth: Int,
		imageHeight: Int,
		viewWidth: Int,
		viewHeight: Int,
		fitToWidth: Boolean,
		zoom: Float,
		panX: Int = 0,
		panY: Int = 0,
	): PagePlacement {
		if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
			return PagePlacement(0, 0, 0, 0)
		}
		val base = if (fitToWidth) {
			viewWidth.toDouble() / imageWidth
		} else {
			viewHeight.toDouble() / imageHeight
		}
		val scale = (base * zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)).coerceAtLeast(0.05)
		val width = (imageWidth * scale).toInt().coerceAtLeast(1)
		val height = (imageHeight * scale).toInt().coerceAtLeast(1)
		val x = clampAxis(viewWidth, width, panX)
		val y = clampAxis(viewHeight, height, panY)
		return PagePlacement(x, y, width, height)
	}

	/** Centres the axis when it fits, otherwise clamps the pan so no gap is visible. */
	fun clampAxis(viewSize: Int, contentSize: Int, pan: Int): Int = if (contentSize <= viewSize) {
		(viewSize - contentSize) / 2
	} else {
		((viewSize - contentSize) / 2 + pan).coerceIn(viewSize - contentSize, 0)
	}

	/** Scale factor used by the continuous (vertical/webtoon) canvas. */
	fun continuousScale(imageWidth: Int, viewWidth: Int, zoom: Float): Double {
		if (imageWidth <= 0 || viewWidth <= 0) return 1.0
		return (viewWidth.toDouble() / imageWidth) * zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
	}
}
