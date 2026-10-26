/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file, replacing the Android details screen (fragment + RecyclerView) with a Swing layout.
 * Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.ui.details

import org.koitharu.kotatsu.desktop.core.i18n.K
import org.koitharu.kotatsu.desktop.core.i18n.tr
import org.koitharu.kotatsu.desktop.core.util.formatDateTime
import org.koitharu.kotatsu.desktop.core.util.formatPercent
import org.koitharu.kotatsu.desktop.data.AppRepository
import org.koitharu.kotatsu.desktop.data.local.ImageDecoder
import org.koitharu.kotatsu.desktop.data.model.Chapter
import org.koitharu.kotatsu.desktop.data.model.MangaWithChapters
import org.koitharu.kotatsu.desktop.reader.ReadingProgress
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
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

class DetailsPanel(
	private val repo: AppRepository,
	private val navigator: Navigator,
) : JPanel(BorderLayout()), Screen {

	private var mangaId: Long? = null
	private var details: MangaWithChapters? = null
	private var cover: BufferedImage? = null

	private val titleLabel = Ui.label("", Theme.fontH1)
	private val authorLabel = Ui.label("", Theme.fontSmall, Theme.onSurfaceVariant)
	private val progressLabel = Ui.label("", Theme.fontSmall, Theme.onSurfaceVariant)
	private val tagsLabel = Ui.label("", Theme.fontSmall, Theme.onSurfaceVariant)
	private val chapterListModel = DefaultListModel<Chapter>()
	private val chapterList = JList(chapterListModel)
	private val coverPanel = object : JPanel() {
		override fun paintComponent(g: Graphics) {
			super.paintComponent(g)
			val image = cover
			if (image == null) {
				g.color = Theme.surfaceVariant
				g.fillRoundRect(0, 0, width, height, 10, 10)
				return
			}
			val g2 = g.create() as Graphics2D
			val ratio = minOf(width.toDouble() / image.width, height.toDouble() / image.height)
			val w = (image.width * ratio).toInt()
			val h = (image.height * ratio).toInt()
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
			g2.drawImage(image, (width - w) / 2, (height - h) / 2, w, h, null)
			g2.dispose()
		}
	}
	private val favouriteButton = Ui.button(tr(K.DETAILS_ADD_FAVOURITE)) { toggleFavourite() }
	private val readButton = Ui.accentButton(tr(K.ACTION_CONTINUE)) { continueReading() }
	private val refreshButton = Ui.button(tr(K.DETAILS_REFRESH_CHAPTERS)) { refreshChapters() }.apply {
		isVisible = false
	}
	private val browserButton = Ui.button(tr(K.DETAILS_OPEN_IN_BROWSER)) { openInBrowser() }.apply {
		isVisible = false
	}
	private val chapterCountLabel = Ui.label("", Theme.fontSmall, Theme.onSurfaceVariant)

	init {
		background = Theme.background
		add(createTopBar(), BorderLayout.NORTH)
		chapterList.cellRenderer = ChapterRenderer()
		chapterList.selectionMode = ListSelectionModel.SINGLE_SELECTION
		chapterList.background = Theme.background
		chapterList.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				if (e.clickCount >= 2) openSelectedChapter()
			}
		})
		chapterList.registerKeyboardAction(
			{ openSelectedChapter() },
			javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0),
			javax.swing.JComponent.WHEN_FOCUSED,
		)

		val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createInfoPanel(), createChapterPanel())
		split.background = Theme.background
		split.border = BorderFactory.createEmptyBorder()
		split.dividerLocation = 320
		split.resizeWeight = 0.28
		add(split, BorderLayout.CENTER)
	}

	private fun createTopBar(): JPanel = JPanel(BorderLayout()).apply {
		background = Theme.background
		border = BorderFactory.createEmptyBorder(12, 16, 8, 16)
		add(
			JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
				background = Theme.background
				add(Ui.button("← " + tr(K.NAV_LIBRARY)) { navigator.backToLibrary() })
				add(Ui.button(tr(K.NAV_HUB)) { navigator.showHub() })
			},
			BorderLayout.WEST,
		)
	}

	private fun createInfoPanel(): JPanel = JPanel().apply {
		background = Theme.background
		layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
		border = BorderFactory.createEmptyBorder(0, 16, 16, 12)
		coverPanel.preferredSize = Dimension(260, 360)
		coverPanel.maximumSize = Dimension(400, 460)
		coverPanel.background = Theme.background
		coverPanel.alignmentX = LEFT_ALIGNMENT
		add(coverPanel)
		add(Ui.vgap(10))
		add(titleLabel.apply { alignmentX = LEFT_ALIGNMENT })
		add(authorLabel.apply { alignmentX = LEFT_ALIGNMENT })
		add(Ui.vgap(6))
		add(progressLabel.apply { alignmentX = LEFT_ALIGNMENT })
		add(tagsLabel.apply { alignmentX = LEFT_ALIGNMENT })
		add(Ui.vgap(10))
		add(
			JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
				background = Theme.background
				alignmentX = LEFT_ALIGNMENT
				add(readButton)
				add(favouriteButton)
			},
		)
		add(
			JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
				background = Theme.background
				alignmentX = LEFT_ALIGNMENT
				add(Ui.button(tr(K.DETAILS_DOWNLOAD_CHAPTER)) { downloadSelectedChapter() })
				add(Ui.button(tr(K.ACTION_DELETE)) { deleteSelectedDownload() })
				add(refreshButton)
				add(browserButton)
			},
		)
		add(
			JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
				background = Theme.background
				alignmentX = LEFT_ALIGNMENT
				add(Ui.button(tr(K.DETAILS_MARK_READ)) { markSelectedRead() })
				add(
					Ui.button(tr(K.ACTION_REMOVE)) {
						val id = mangaId ?: return@button
						if (Ui.confirm(this@DetailsPanel, tr(K.ACTION_REMOVE) + "?")) {
							repo.removeManga(id)
							navigator.backToLibrary()
						}
					},
				)
			},
		)
		add(javax.swing.Box.createVerticalGlue())
	}

	private fun createChapterPanel(): JPanel = JPanel(BorderLayout()).apply {
		background = Theme.background
		border = BorderFactory.createEmptyBorder(0, 0, 0, 12)
		add(
			JPanel(BorderLayout()).apply {
				background = Theme.background
				border = BorderFactory.createEmptyBorder(0, 6, 6, 0)
				add(Ui.subtitle(tr(K.DETAILS_CHAPTERS)), BorderLayout.WEST)
				add(chapterCountLabel, BorderLayout.EAST)
			},
			BorderLayout.NORTH,
		)
		val scroll = JScrollPane(chapterList)
		scroll.background = Theme.background
		scroll.border = BorderFactory.createLineBorder(Theme.border)
		scroll.verticalScrollBar.unitIncrement = 24
		add(scroll, BorderLayout.CENTER)
	}

	fun showManga(mangaId: Long) {
		this.mangaId = mangaId
		cover = null
		onShown()
	}

	fun onHidden() {
		// nothing to release
	}

	override fun onShown() {
		val id = mangaId ?: return
		Async.compute({
			val details = repo.details(id)
			val cover = details?.manga?.let { repo.coverFor(it) }?.let(ImageDecoder::read)
			details to cover
		}) { (details, coverImage) ->
			this.details = details
			this.cover = coverImage
			coverPanel.repaint()
			if (details == null) {
				titleLabel.text = "?"
				return@compute
			}
			val manga = details.manga
			titleLabel.text = manga.displayTitle
			val sourceTitle = repo.sourceFor(manga)?.descriptor?.title ?: manga.source
			val isOnline = manga.source != org.koitharu.kotatsu.desktop.data.model.SOURCE_LOCAL
			authorLabel.text = listOfNotNull(
				manga.authors,
				(if (isOnline) tr(K.DETAILS_ONLINE_SOURCE) else tr(K.LIBRARY_SOURCE)) + ": " + sourceTitle,
			).joinToString(" · ")
			refreshButton.isVisible = isOnline && repo.sourceFor(manga) != null
			browserButton.isVisible = isOnline && manga.publicUrl.startsWith("http")
			val history = repo.activity.historyFor(id)
			progressLabel.text = buildString {
				append(tr(K.DETAILS_PROGRESS)).append(": ")
				append(formatPercent(history?.percent ?: -1f))
				history?.let { append("  ·  ").append(tr(K.HISTORY_UPDATED).format(formatDateTime(it.updatedAt))) }
			}
			tagsLabel.text = if (manga.tags.isEmpty()) "" else manga.tags.joinToString(", ") { it.title }
			favouriteButton.text =
				if (repo.activity.isFavourite(id)) tr(K.DETAILS_REMOVE_FAVOURITE) else tr(K.DETAILS_ADD_FAVOURITE)

			chapterListModel.clear()
			details.sortedChapters.forEach { chapterListModel.addElement(it) }
			chapterCountLabel.text = tr(K.DETAILS_CHAPTERS_COUNT).format(details.chapters.size)
			if (details.chapters.isEmpty()) {
				progressLabel.text = tr(K.DETAILS_NO_CHAPTERS)
			}
			if (chapterListModel.size > 0) {
				val index = details.sortedChapters.indexOfFirst { it.chapterId == history?.chapterId }
				chapterList.selectedIndex = index.coerceAtLeast(0)
				chapterList.ensureIndexIsVisible(chapterList.selectedIndex)
			}
		}
	}

	private fun refreshChapters() {
		val id = mangaId ?: return
		navigator.setStatus(tr(K.APP_LOADING))
		Async.compute({ repo.refreshRemote(id) }, onError = { error ->
			navigator.setStatus(tr(K.APP_READY))
			Ui.showError(this, error.message ?: error.javaClass.simpleName)
		}) { outcome ->
			navigator.setStatus(tr(K.APP_READY))
			when (outcome) {
				is org.koitharu.kotatsu.desktop.sources.SourceOutcome.Success ->
					Ui.showInfo(this, tr(K.DETAILS_REFRESHED).format(outcome.value))

				is org.koitharu.kotatsu.desktop.sources.SourceOutcome.Failure ->
					Ui.showError(this, outcome.failure.message())
			}
			navigator.refreshAll()
			onShown()
		}
	}

	private fun openInBrowser() {
		val url = details?.manga?.publicUrl ?: return
		runCatching {
			if (java.awt.Desktop.isDesktopSupported()) {
				java.awt.Desktop.getDesktop().browse(java.net.URI.create(url))
			}
		}.onFailure { Ui.showError(this, it.message ?: it.javaClass.simpleName) }
	}

	private fun selectedChapter(): Chapter? = chapterList.selectedValue

	private fun toggleFavourite() {
		val id = mangaId ?: return
		repo.toggleFavourite(id)
		onShown()
	}

	private fun continueReading() {
		val id = mangaId ?: return
		Async.compute({ repo.resumePosition(id) }) { position ->
			val (chapterIndex, page) = position ?: (0 to 0)
			navigator.openReader(id, chapterIndex, page)
		}
	}

	private fun openSelectedChapter() {
		val id = mangaId ?: return
		val chapter = selectedChapter() ?: return
		val details = details ?: return
		val index = details.sortedChapters.indexOfFirst { it.chapterId == chapter.chapterId }
		if (index >= 0) navigator.openReader(id, index, 0)
	}

	private fun downloadSelectedChapter() {
		val chapter = selectedChapter() ?: return
		Async.compute({ repo.downloadChapter(chapter) }) { result ->
			result.onSuccess {
				Ui.showInfo(this, tr(K.DOWNLOADS_OFFLINE_READY) + ": " + chapter.title)
			}.onFailure {
				Ui.showError(this, tr(K.ERROR_IO).format(it.message ?: it.javaClass.simpleName))
			}
			onShown()
		}
	}

	private fun deleteSelectedDownload() {
		val chapter = selectedChapter() ?: return
		repo.deleteDownload(chapter)
		onShown()
	}

	private fun markSelectedRead() {
		val chapter = selectedChapter() ?: return
		val details = details ?: return
		repo.markChapterRead(details.manga, chapter, !chapter.isRead)
		onShown()
	}

	private inner class ChapterRenderer : JPanel(), ListCellRenderer<Chapter> {

		private val title = Ui.label("", Theme.baseFont)
		private val subtitle = Ui.label("", Theme.fontSmall, Theme.onSurfaceVariant)

		init {
			layout = BorderLayout()
			background = Theme.background
			isOpaque = true
			border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
			add(title, BorderLayout.CENTER)
			add(subtitle, BorderLayout.EAST)
		}

		override fun getListCellRendererComponent(
			list: JList<out Chapter>,
			value: Chapter?,
			index: Int,
			isSelected: Boolean,
			cellHasFocus: Boolean,
		): Component {
			title.text = value?.title ?: ""
			subtitle.text = value?.let { chapter ->
				buildString {
					append(chapter.pagesCount).append(" p.")
					if (chapter.isDownloaded) append("  ⬇")
					if (chapter.isRead) append("  ✓")
				}
			}.orEmpty()
			background = when {
				isSelected -> Theme.accent
				index % 2 == 0 -> Theme.surface
				else -> Theme.background
			}
			val foreground = if (isSelected) Color.WHITE else Theme.onBackground
			title.foreground = foreground
			subtitle.foreground = if (isSelected) Color.WHITE else Theme.onSurfaceVariant
			// A JPanel renderer is not laid out by JList: without this the labels keep a zero size
			// and the row is painted as an empty coloured band.
			val width = list.width
			if (width > 0) setSize(width, preferredSize.height)
			Ui.layoutTree(this)
			return this
		}
	}
}
