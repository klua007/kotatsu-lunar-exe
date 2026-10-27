/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file, replacing the Android download manager screens with a Swing overview.
 * Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.ui.downloads

import org.koitharu.kotatsu.desktop.core.AppDirs
import org.koitharu.kotatsu.desktop.core.i18n.K
import org.koitharu.kotatsu.desktop.core.i18n.tr
import org.koitharu.kotatsu.desktop.core.util.humanFileSize
import org.koitharu.kotatsu.desktop.data.AppRepository
import org.koitharu.kotatsu.desktop.data.model.Chapter
import org.koitharu.kotatsu.desktop.data.model.Manga
import org.koitharu.kotatsu.desktop.ui.Async
import org.koitharu.kotatsu.desktop.ui.Navigator
import org.koitharu.kotatsu.desktop.ui.Screen
import org.koitharu.kotatsu.desktop.ui.Theme
import org.koitharu.kotatsu.desktop.ui.Ui
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

class DownloadsPanel(
	private val repo: AppRepository,
	private val navigator: Navigator,
) : JPanel(BorderLayout()), Screen {

	private val list = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		background = Theme.background
	}
	private val summary = Ui.label("", Theme.fontSmall, Theme.onSurfaceVariant)

	init {
		background = Theme.background
		add(createHeader(), BorderLayout.NORTH)
		val scroll = JScrollPane(list)
		scroll.background = Theme.background
		scroll.border = BorderFactory.createEmptyBorder()
		scroll.verticalScrollBar.unitIncrement = 24
		add(scroll, BorderLayout.CENTER)
	}

	private fun createHeader(): JPanel = JPanel(BorderLayout()).apply {
		background = Theme.background
		border = BorderFactory.createEmptyBorder(8, 12, 6, 16)
		add(
			JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply {
				background = Theme.background
				add(Ui.title(tr(K.DOWNLOADS_TITLE)))
				add(Ui.button(tr(K.ACTION_OPEN_FOLDER)) { Ui.openFolder(this@DownloadsPanel, AppDirs.downloadsDir) })
				add(Ui.button(tr(K.ACTION_REFRESH)) { onShown() })
			},
			BorderLayout.WEST,
		)
		add(
			JPanel(BorderLayout()).apply {
				background = Theme.background
				add(summary, BorderLayout.EAST)
			},
			BorderLayout.CENTER,
		)
	}

	override fun onShown() {
		Async.compute({ repo.downloadsByManga() to repo.downloadsSize() }) { (groups, size) ->
			list.removeAll()
			summary.text = "${tr(K.STATUS_ITEMS).format(groups.sumOf { it.second.size })}  ·  ${humanFileSize(size)}"
			if (groups.isEmpty()) {
				list.add(
					JPanel(BorderLayout()).apply {
						background = Theme.background
						border = BorderFactory.createEmptyBorder(40, 20, 20, 20)
						add(Ui.label(tr(K.DOWNLOADS_EMPTY), Theme.fontH2, Theme.onSurfaceVariant), BorderLayout.NORTH)
					},
				)
			}
			groups.forEach { (manga, chapters) -> list.add(createGroup(manga, chapters)) }
			list.revalidate()
			list.repaint()
		}
	}

	private fun createGroup(manga: Manga, chapters: List<Chapter>): JPanel = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		background = Theme.background
		alignmentX = LEFT_ALIGNMENT
		border = BorderFactory.createEmptyBorder(8, 14, 8, 14)
		add(
			JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply {
				background = Theme.background
				// BoxLayout gives the extra vertical space to the first component whose maximum
				// allows it; without a cap the header would stretch and push the chapters down.
				maximumSize = Dimension(Int.MAX_VALUE, 40)
				add(Ui.label(manga.displayTitle, Theme.fontH2))
				add(Ui.label(tr(K.DOWNLOADS_CHAPTERS).format(chapters.size), Theme.fontSmall, Theme.onSurfaceVariant))
				add(Ui.button(tr(K.ACTION_OPEN)) { navigator.showDetails(manga.id) })
			},
		)
		chapters.sortedBy { it.index }.forEach { chapter ->
			add(
				JPanel(BorderLayout()).apply {
					background = Theme.surface
					maximumSize = Dimension(Int.MAX_VALUE, 34)
					border = BorderFactory.createCompoundBorder(
						BorderFactory.createEmptyBorder(2, 0, 2, 0),
						BorderFactory.createEmptyBorder(4, 10, 4, 10),
					)
					add(Ui.label(chapter.title, Theme.baseFont), BorderLayout.WEST)
					add(
						JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
							background = Theme.surface
							add(
								Ui.button(tr(K.ACTION_READ)) {
									val index = chapters.sortedBy { it.index }
										.indexOfFirst { it.chapterId == chapter.chapterId }
									navigator.openReader(manga.id, index.coerceAtLeast(0), 0)
								},
							)
							add(
								Ui.button(tr(K.ACTION_DELETE)) {
									Async.compute({
										repo.deleteDownload(chapter)
									}) { onShown() }
								},
							)
						},
						BorderLayout.EAST,
					)
				},
			)
		}
	}
}

private fun JLabel.bold(): JLabel = apply { font = Theme.fontH2 }
