/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.ui

/** Navigation surface implemented by the main window, used by the screens. */
interface Navigator {

	fun showLibrary()

	fun showHistory()

	fun showDownloads()

	fun showSettings()

	fun showDetails(mangaId: Long)

	fun backToLibrary()

	/** Opens the reader window for a chapter. */
	fun openReader(mangaId: Long, chapterIndex: Int, page: Int)

	fun setStatus(text: String)

	/** Re-reads the library from disk and repaints the current screen. */
	fun refreshAll()
}

/** Screens implement this to reload their content when they become visible. */
interface Screen {
	fun onShown()
}
