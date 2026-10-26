/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file, replacing the Android history screen with a Swing list. Licensed under GPL-3.0-or-later.
 */
package org.koitharu.kotatsu.desktop.ui.history

import org.koitharu.kotatsu.desktop.core.i18n.K
import org.koitharu.kotatsu.desktop.core.i18n.tr
import org.koitharu.kotatsu.desktop.core.util.formatDateTime
import org.koitharu.kotatsu.desktop.core.util.formatPercent
import org.koitharu.kotatsu.desktop.data.AppRepository
import org.koitharu.kotatsu.desktop.data.HistoryItem
import org.koitharu.kotatsu.desktop.data.local.ImageDecoder
import org.koitharu.kotatsu.desktop.ui.Async
import org.koitharu.kotatsu.desktop.ui.Navigator
import org.koitharu.kotatsu.desktop.ui.Screen
import org.koitharu.kotatsu.desktop.ui.Theme
import org.koitharu.kotatsu.desktop.ui.Ui
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

class HistoryPanel(
	private val repo: AppRepository,
	private val navigator: Navigator,
) : JPanel(BorderLayout()), Screen {

	private val model = DefaultListModel<HistoryItem>()
	private val list = JList(model)
	private val covers = HashMap<Long, BufferedImage?>()
	private val emptyLabel = Ui.label(tr(K.HISTORY_EMPTY), Theme.fontH2, Theme.onSurfaceVariant)

	init {
		background = Theme.background
		list.cellRenderer = HistoryRenderer()
		list.selectionMode = ListSelectionModel.SINGLE_SELECTION
		list.background = Theme.background
		list.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				if (e.clickCount >= 2) resumeSelected()
			}
		})
		add(createHeader(), BorderLayout.NORTH)
		val scroll = JScrollPane(list)
		scroll.background = Theme.background
		scroll.border = BorderFactory.createEmptyBorder()
		scroll.verticalScrollBar.unitIncrement = 24
		add(scroll, BorderLayout.CENTER)
		add(
			JPanel(BorderLayout()).apply {
				background = Theme.background
				border = BorderFactory.createEmptyBorder(6, 16, 10, 16)
				add(emptyLabel, BorderLayout.WEST)
			},
			BorderLayout.SOUTH,
		)
	}

	private fun createHeader(): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 10)).apply {
		background = Theme.background
		border = BorderFactory.createEmptyBorder(8, 8, 0, 8)
		add(Ui.title(tr(K.HISTORY_TITLE)))
		add(Ui.button(tr(K.ACTION_REFRESH)) { onShown() })
		add(
			Ui.button(tr(K.ACTION_CLEAR)) {
				if (Ui.confirm(this@HistoryPanel, tr(K.HISTORY_CLEAR_CONFIRM))) {
					repo.clearHistory()
					onShown()
				}
			},
		)
		add(Ui.button(tr(K.ACTION_CONTINUE)) { resumeSelected() })
		add(
			Ui.button(tr(K.ACTION_REMOVE)) {
				val item = list.selectedValue ?: return@button
				repo.removeHistory(item.manga.id)
				onShown()
			},
		)
	}

	override fun onShown() {
		Async.compute({ repo.historyItems() }) { items ->
			model.clear()
			items.forEach(model::addElement)
			emptyLabel.isVisible = items.isEmpty()
			covers.clear()
			items.take(40).forEach { item ->
				Async.compute({ repo.coverFor(item.manga) }) { path ->
					covers[item.manga.id] = path?.let(ImageDecoder::read)
					list.repaint()
				}
			}
		}
	}

	private fun resumeSelected() {
		val item = list.selectedValue ?: return
		Async.compute({ repo.resumePosition(item.manga.id) }) { position ->
			val (chapterIndex, page) = position ?: (0 to 0)
			navigator.openReader(item.manga.id, chapterIndex, page)
		}
	}

	private inner class HistoryRenderer : JPanel(BorderLayout()), ListCellRenderer<HistoryItem> {

		private val cover = object : JPanel() {
			override fun paintComponent(g: java.awt.Graphics) {
				super.paintComponent(g)
				val image = covers[currentMangaId]
				if (image == null) {
					g.color = Theme.surfaceVariant
					g.fillRoundRect(0, 0, width, height, 6, 6)
					return
				}
				val g2 = g.create() as java.awt.Graphics2D
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
				val ratio = minOf(width.toDouble() / image.width, height.toDouble() / image.height)
				val w = (image.width * ratio).toInt()
				val h = (image.height * ratio).toInt()
				g2.drawImage(image, (width - w) / 2, 0, w, h, null)
				g2.dispose()
			}
		}
		private var currentMangaId: Long? = null
		private val title = Ui.label("", Theme.baseFont.deriveFont(java.awt.Font.BOLD))
		private val subtitle = Ui.label("", Theme.fontSmall, Theme.onSurfaceVariant)

		init {
			background = Theme.background
			border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
			cover.preferredSize = Dimension(44, 60)
			add(cover, BorderLayout.WEST)
			add(
				JPanel().apply {
					layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
					background = Theme.background
					add(title)
					add(subtitle)
				},
				BorderLayout.CENTER,
			)
		}

		override fun getListCellRendererComponent(
			list: JList<out HistoryItem>,
			value: HistoryItem?,
			index: Int,
			isSelected: Boolean,
			cellHasFocus: Boolean,
		): Component {
			currentMangaId = value?.manga?.id
			if (value != null) {
				title.text = value.manga.displayTitle
				subtitle.text = buildString {
					val chapter = value.chapter?.title
					if (chapter != null) append(chapter).append("  ·  ")
					append(formatPercent(value.entry.percent))
					append("  ·  ").append(tr(K.HISTORY_UPDATED).format(formatDateTime(value.entry.updatedAt)))
				}
			} else {
				title.text = ""
				subtitle.text = ""
			}
			val bg = when {
				isSelected -> Theme.accent
				index % 2 == 0 -> Theme.surface
				else -> Theme.background
			}
			background = bg
			cover.background = bg
			val foreground = if (isSelected) Color.WHITE else Theme.onBackground
			title.foreground = foreground
			subtitle.foreground = if (isSelected) Color.WHITE else Theme.onSurfaceVariant
			return this
		}
	}
}
