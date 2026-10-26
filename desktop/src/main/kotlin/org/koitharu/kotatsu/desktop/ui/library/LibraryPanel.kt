/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file, replacing the Android list screen (RecyclerView + fragment) with a Swing cover grid.
 * Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.ui.library

import org.koitharu.kotatsu.desktop.core.AppDirs
import org.koitharu.kotatsu.desktop.core.i18n.K
import org.koitharu.kotatsu.desktop.core.i18n.tr
import org.koitharu.kotatsu.desktop.core.util.formatPercent
import org.koitharu.kotatsu.desktop.data.AppRepository
import org.koitharu.kotatsu.desktop.data.LibraryFilter
import org.koitharu.kotatsu.desktop.data.LibraryItem
import org.koitharu.kotatsu.desktop.data.model.ListSortOrder
import org.koitharu.kotatsu.desktop.ui.Async
import org.koitharu.kotatsu.desktop.ui.Navigator
import org.koitharu.kotatsu.desktop.ui.Screen
import org.koitharu.kotatsu.desktop.ui.Theme
import org.koitharu.kotatsu.desktop.ui.Ui
import org.koitharu.kotatsu.desktop.ui.WrapLayout
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.nio.file.Paths
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

class LibraryPanel(
	private val repo: AppRepository,
	private val navigator: Navigator,
) : JPanel(BorderLayout()), Screen {

	private val searchField: JTextField = Ui.searchField(tr(K.LIBRARY_SEARCH_HINT)) { reload() }
	private val filterBox = JComboBox(arrayOf(tr(K.LIBRARY_FILTER_ALL), tr(K.LIBRARY_FILTER_FAVOURITES), tr(K.LIBRARY_FILTER_DOWNLOADED)))
	private val sortBox = JComboBox<SortChoice>()
	private val grid = JPanel(WrapLayout(java.awt.FlowLayout.LEFT, 12, 12))
	private val gridScroll = JScrollPane(grid)
	private val emptyState = createEmptyState()
	private val statusLabel = Ui.label("", Theme.fontSmall, Theme.onSurfaceVariant)
	private val covers = HashMap<Long, BufferedImage?>()
	private val loadingCovers = HashSet<Long>()

	private var selectedId: Long? = null

	init {
		background = Theme.background
		buildSortBox()
		add(createHeader(), BorderLayout.NORTH)
		grid.background = Theme.background
		gridScroll.background = Theme.background
		gridScroll.border = BorderFactory.createEmptyBorder()
		gridScroll.verticalScrollBar.unitIncrement = 24
		gridScroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
		add(gridScroll, BorderLayout.CENTER)
		add(createFooter(), BorderLayout.SOUTH)
		filterBox.addActionListener { reload() }
		sortBox.addActionListener { reload() }
		installKeys()
	}

	private fun buildSortBox() {
		sortBox.removeAllItems()
		sortBox.addItem(SortChoice(ListSortOrder.NEWEST, tr(K.LIBRARY_SORT_ADDED)))
		sortBox.addItem(SortChoice(ListSortOrder.OLDEST, tr(K.LIBRARY_SORT_ADDED) + " ↑"))
		sortBox.addItem(SortChoice(ListSortOrder.ALPHABETIC, tr(K.LIBRARY_SORT_TITLE)))
		sortBox.addItem(SortChoice(ListSortOrder.PROGRESS, tr(K.LIBRARY_SORT_PROGRESS)))
		sortBox.addItem(SortChoice(ListSortOrder.LAST_READ, tr(K.LIBRARY_SORT_LAST_READ)))
		val current = repo.settings.librarySortOrder
		sortBox.selectedIndex = (0 until sortBox.itemCount)
			.firstOrNull { (sortBox.getItemAt(it) as SortChoice).order == current } ?: 0
	}

	private fun createHeader(): JPanel = JPanel(BorderLayout()).apply {
		background = Theme.background
		border = BorderFactory.createEmptyBorder(14, 16, 6, 16)
		add(
			JPanel(BorderLayout()).apply {
				background = Theme.background
				add(Ui.title(tr(K.LIBRARY_TITLE)), BorderLayout.WEST)
				add(statusLabel, BorderLayout.EAST)
			},
			BorderLayout.NORTH,
		)
		add(
			JPanel(WrapLayout(java.awt.FlowLayout.LEFT, 8, 6)).apply {
				background = Theme.background
				searchField.preferredSize = Dimension(260, 30)
				add(searchField)
				filterBox.preferredSize = Dimension(150, 30)
				add(filterBox)
				add(sortBox)
				add(
					Ui.accentButton(tr(K.LOCAL_IMPORT_FILES)) {
						val paths = Ui.chooseFiles(this@LibraryPanel, AppDirs.dataDir)
						if (paths.isNotEmpty()) {
							navigator.setStatus(tr(K.APP_LOADING))
							Async.compute({ repo.importPaths(paths) }) {
								navigator.refreshAll()
								navigator.setStatus(tr(K.APP_READY))
							}
						}
					},
				)
				add(Ui.button(tr(K.LOCAL_IMPORT_FOLDER)) {
					val path = Ui.chooseDirectory(this@LibraryPanel, AppDirs.dataDir) ?: return@button
					navigator.setStatus(tr(K.APP_LOADING))
					Async.compute({ repo.importPaths(listOf(path)) }) {
						navigator.refreshAll()
						navigator.setStatus(tr(K.APP_READY))
					}
				})
				add(Ui.button(tr(K.ACTION_REFRESH)) {
					navigator.setStatus(tr(K.APP_LOADING))
					Async.compute({ repo.refreshLibrary() }) {
						navigator.refreshAll()
						navigator.setStatus(tr(K.APP_READY))
					}
				})
			},
			BorderLayout.CENTER,
		)
	}

	private fun createFooter(): JPanel = JPanel(BorderLayout()).apply {
		background = Theme.background
		border = BorderFactory.createEmptyBorder(2, 16, 8, 16)
		add(
			Ui.label(tr(K.LOCAL_DROP_HINT), Theme.fontSmall, Theme.onSurfaceVariant),
			BorderLayout.WEST,
		)
	}

	private fun createEmptyState(): JPanel = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		background = Theme.background
		border = BorderFactory.createEmptyBorder(60, 20, 20, 20)
		add(Ui.label(tr(K.LIBRARY_EMPTY), Theme.fontH1, Theme.onSurfaceVariant).apply { alignmentX = CENTER_ALIGNMENT })
		add(Ui.vgap(8))
		add(Ui.label(tr(K.LIBRARY_EMPTY_HINT), Theme.baseFont, Theme.onSurfaceVariant).apply { alignmentX = CENTER_ALIGNMENT })
		add(Ui.vgap(12))
		add(
			Ui.accentButton(tr(K.NAV_HUB)) { navigator.showHub() }.apply { alignmentX = CENTER_ALIGNMENT },
		)
	}

	private fun installKeys() {
		val inputMap = gridScroll.inputMap
		inputMap.put(Ui.keyEnter(), "open-selected")
		gridScroll.actionMap.put(
			"open-selected",
			object : javax.swing.AbstractAction() {
				override fun actionPerformed(e: java.awt.event.ActionEvent) = openSelected()
			},
		)
	}

	fun focusSearch() {
		searchField.requestFocusInWindow()
	}

	override fun onShown() {
		reload()
	}

	// --- Data ---------------------------------------------------------------------------------

	private fun reload() {
		val query = searchField.text
		val filter = when (filterBox.selectedIndex) {
			1 -> LibraryFilter.FAVOURITES
			2 -> LibraryFilter.DOWNLOADED
			else -> LibraryFilter.ALL
		}
		val sort = (sortBox.selectedItem as? SortChoice)?.order ?: ListSortOrder.NEWEST
		Async.compute({
			repo.libraryItems(query, filter, sort).map { item ->
				item to repo.coverFor(item.manga)
			}
		}) { rows ->
			render(rows.map { it.first }, rows.mapNotNull { it.second })
			statusLabel.text = tr(K.LIBRARY_COUNT).format(rows.size)
		}
	}

	private fun render(items: List<LibraryItem>, coverPaths: List<java.nio.file.Path>) {
		grid.removeAll()
		if (items.isEmpty()) {
			grid.layout = BorderLayout()
			grid.add(emptyState, BorderLayout.CENTER)
		} else {
			grid.layout = WrapLayout(java.awt.FlowLayout.LEFT, 12, 12)
			items.forEach { item ->
				grid.add(createCard(item))
			}
		}
		grid.revalidate()
		grid.repaint()
	}

	private fun createCard(item: LibraryItem): JPanel {
		val card = CardPanel(item)
		card.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				selectedId = item.manga.id
				if (e.clickCount >= 2) {
					navigator.showDetails(item.manga.id)
				}
				grid.repaint()
			}

			override fun mousePressed(e: MouseEvent) = maybePopup(e, item)
			override fun mouseReleased(e: MouseEvent) = maybePopup(e, item)
		})
		return card
	}

	private fun maybePopup(event: MouseEvent, item: LibraryItem) {
		if (!event.isPopupTrigger) return
		val popup = JPopupMenu()
		popup.add(
			javax.swing.JMenuItem(tr(K.ACTION_OPEN)).apply {
				addActionListener { navigator.showDetails(item.manga.id) }
			},
		)
		popup.add(
			javax.swing.JMenuItem(tr(K.ACTION_CONTINUE)).apply {
				addActionListener { continueReading(item.manga.id) }
			},
		)
		popup.add(
			javax.swing.JMenuItem(
				if (item.isFavourite) tr(K.DETAILS_REMOVE_FAVOURITE) else tr(K.DETAILS_ADD_FAVOURITE),
			).apply {
				addActionListener {
					repo.toggleFavourite(item.manga.id)
					reload()
				}
			},
		)
		popup.add(javax.swing.JSeparator())
		popup.add(
			javax.swing.JMenuItem(tr(K.ACTION_REMOVE)).apply {
				addActionListener {
					if (Ui.confirm(this@LibraryPanel, tr(K.ACTION_REMOVE) + ": " + item.manga.title + "?")) {
						repo.removeManga(item.manga.id)
						reload()
					}
				}
			},
		)
		popup.show(event.component, event.x, event.y)
	}

	fun openSelected() {
		val id = selectedId ?: return
		navigator.showDetails(id)
	}

	private fun continueReading(mangaId: Long) {
		Async.compute({ repo.resumePosition(mangaId) }) { position ->
			val (chapterIndex, page) = position ?: (0 to 0)
			navigator.openReader(mangaId, chapterIndex, page)
		}
	}

	private data class SortChoice(val order: ListSortOrder, val label: String) {
		override fun toString(): String = label
	}

	/** Cover + title + progress, painted without extra components to keep the grid cheap. */
	private inner class CardPanel(private val item: LibraryItem) : JPanel() {

		init {
			background = Theme.surface
			preferredSize = Dimension(172, 300)
			isOpaque = true
			border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
			cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
			toolTipText = buildString {
				append("<html><b>").append(item.manga.displayTitle).append("</b>")
				append("<br>").append(tr(K.LIBRARY_CHAPTERS)).append(": ").append(item.chaptersCount)
				append("<br>").append(tr(K.DETAILS_PROGRESS)).append(": ").append(formatPercent(item.progress))
				append("<br>").append(tr(K.LIBRARY_SOURCE)).append(": ")
				append(repo.sourceFor(item.manga)?.descriptor?.title ?: item.manga.source)
				item.manga.authors?.let { append("<br>").append(it) }
				append("</html>")
			}
			loadCover()
		}

		private fun loadCover() {
			covers[item.manga.id]?.let { return }
			if (!loadingCovers.add(item.manga.id)) return
			Async.compute({ repo.coverFor(item.manga) }) { path ->
				loadingCovers.remove(item.manga.id)
				val image = path?.let { org.koitharu.kotatsu.desktop.data.local.ImageDecoder.read(it) }
				covers[item.manga.id] = image
				repaint()
			}
		}

		override fun paintComponent(g: Graphics) {
			super.paintComponent(g)
			val g2 = g.create() as Graphics2D
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
				val isSelected = selectedId == item.manga.id
				if (isSelected) {
					g2.color = Theme.accent
					g2.drawRect(0, 0, width - 1, height - 1)
				}
				val coverWidth = width - 16
				val coverHeight = (coverWidth * 1.44).toInt().coerceAtMost(height - 92)
				val coverRect = java.awt.Rectangle(8, 8, coverWidth, coverHeight)
				val image = covers[item.manga.id]
				if (image != null) {
					g2.drawImage(fitImage(image, coverRect.width, coverRect.height), coverRect.x, coverRect.y, null)
				} else {
					g2.color = Theme.surfaceVariant
					g2.fillRoundRect(coverRect.x, coverRect.y, coverRect.width, coverRect.height, 8, 8)
					g2.color = Theme.onSurfaceVariant
					g2.font = Theme.fontSmall
					val text = item.manga.displayTitle
					g2.drawString(text.take(18), coverRect.x + 8, coverRect.y + 20)
				}
				g2.color = Theme.onBackground
				g2.font = Theme.baseFont.deriveFont(java.awt.Font.BOLD)
				drawClipped(g2, item.manga.displayTitle, 8, coverRect.y + coverRect.height + 18, coverWidth, 2)
				g2.color = Theme.onSurfaceVariant
				g2.font = Theme.fontSmall
				val subtitle = "${item.chaptersCount} ${tr(K.LIBRARY_CHAPTERS).lowercase()} · ${formatPercent(item.progress)}"
				g2.drawString(subtitle.take(28), 8, coverRect.y + coverRect.height + 36)
				val barY = height - 14
				g2.color = Theme.surfaceVariant
				g2.fillRoundRect(8, barY, coverWidth, 5, 4, 4)
				val percent = item.progress.coerceIn(0f, 1f)
				if (percent > 0f) {
					g2.color = Theme.accent
					g2.fillRoundRect(8, barY, (coverWidth * percent).toInt().coerceAtLeast(3), 5, 4, 4)
				}
			} finally {
				g2.dispose()
			}
		}

		private fun drawClipped(g2: Graphics2D, text: String, x: Int, y: Int, maxWidth: Int, lines: Int): Int {
			val metrics = g2.fontMetrics
			var remaining = text
			var currentY = y
			var drawn = 0
			while (drawn < lines && remaining.isNotEmpty()) {
				var line = remaining
				while (metrics.stringWidth(line) > maxWidth && line.length > 1) {
					line = line.dropLast(1)
				}
				g2.drawString(line, x, currentY)
				remaining = remaining.removePrefix(line).trimStart()
				currentY += metrics.height
				drawn++
			}
			return currentY
		}

		private fun fitImage(image: BufferedImage, maxWidth: Int, maxHeight: Int): BufferedImage {
			val ratio = minOf(maxWidth.toDouble() / image.width, maxHeight.toDouble() / image.height)
			val width = (image.width * ratio).toInt().coerceAtLeast(1)
			val height = (image.height * ratio).toInt().coerceAtLeast(1)
			val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
			val g = result.createGraphics()
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
			g.drawImage(image, 0, 0, width, height, null)
			g.dispose()
			return result
		}
	}
}

