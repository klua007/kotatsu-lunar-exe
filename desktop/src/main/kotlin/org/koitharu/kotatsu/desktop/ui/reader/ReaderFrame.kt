/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file. Replaces the Android reader (ReaderActivity + ViewPager/RecyclerView + SSIV view) with a
 * Swing window that implements the same four reading modes, zoom, keyboard control and progress
 * saving. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.ui.reader

import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.core.i18n.K
import org.koitharu.kotatsu.desktop.core.i18n.tr
import org.koitharu.kotatsu.desktop.data.AppRepository
import org.koitharu.kotatsu.desktop.data.local.ChapterContent
import org.koitharu.kotatsu.desktop.data.local.ImageDecoder
import org.koitharu.kotatsu.desktop.data.local.PageImageCache
import org.koitharu.kotatsu.desktop.data.local.PageLoad
import org.koitharu.kotatsu.desktop.data.model.Chapter
import org.koitharu.kotatsu.desktop.data.model.MangaWithChapters
import org.koitharu.kotatsu.desktop.data.model.ReaderMode
import org.koitharu.kotatsu.desktop.reader.ReadingProgress
import org.koitharu.kotatsu.desktop.ui.Async
import org.koitharu.kotatsu.desktop.ui.Navigator
import org.koitharu.kotatsu.desktop.ui.Theme
import org.koitharu.kotatsu.desktop.ui.Ui
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants

class ReaderFrame internal constructor(
	private val owner: JFrame,
	private val repo: AppRepository,
	private val manga: MangaWithChapters,
	private val navigator: Navigator,
	startChapterIndex: Int,
	startPage: Int,
) : JFrame() {

	private companion object {
		const val TAG = "Reader"
		const val MIN_ZOOM = 0.5f
		const val MAX_ZOOM = 4f
	}

	private var chapterIndex: Int = startChapterIndex.coerceIn(0, (manga.sortedChapters.size - 1).coerceAtLeast(0))
	private var page: Int = startPage
	private var generation = 0

	private var mode: ReaderMode = repo.settings.defaultReaderMode
	private var zoom: Float = 1f
	private var fitToWidth = true
	private val images = HashMap<Int, BufferedImage?>()
	private val loading = HashSet<Int>()
	private val pageErrors = HashMap<Int, String>()
	private var content: ChapterContent = ChapterContent.EMPTY
	/** Non-null when the whole chapter could not be opened (e.g. the source is offline). */
	private var contentError: String? = null

	private val titleLabel = JLabel("").apply { font = Theme.fontH2; foreground = Theme.onBackground }
	private val pageLabel = JLabel("").apply { font = Theme.fontSmall; foreground = Theme.onSurfaceVariant }
	private val statusLabel = JLabel("").apply { font = Theme.fontSmall; foreground = Theme.onSurfaceVariant }
	private val modeBox = Ui.comboBox(ReaderMode.entries.toList())
	private val chapterListModel = javax.swing.DefaultListModel<Chapter>()
	private val chapterList = JList(chapterListModel)
	private val tocPanel = JPanel(BorderLayout())
	private val tocScroll: JScrollPane
	private val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
	private val contentHolder = JPanel(BorderLayout())
	private val pagedCanvas = PagedCanvas()
	private val continuousCanvas = ContinuousCanvas()
	private val continuousScroll = JScrollPane(continuousCanvas)
	private val splitPane: JSplitPane
	private val saveTimer = Timer(700) { saveProgress() }.apply { isRepeats = false }
	private var panOffset = Point(0, 0)
	private var dragStart: Point? = null
	private var restoringScroll = false

	init {
		title = tr(K.READER_TITLE)
		defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
		minimumSize = Dimension(560, 480)
		setSize(1000, 900)
		setLocationRelativeTo(owner)

		modeBox.selectedItem = mode
		modeBox.renderer = ModeRenderer()
		modeBox.addActionListener {
			val selected = modeBox.selectedItem as? ReaderMode ?: return@addActionListener
			setMode(selected)
		}

		chapterList.cellRenderer = ChapterCell()
		chapterList.background = Theme.background
		chapterList.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				if (e.clickCount >= 2) {
					val index = chapterList.selectedIndex
					if (index >= 0) openChapter(index, 0)
				}
			}
		})
		tocScroll = JScrollPane(chapterList).apply {
			background = Theme.background
			border = BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.border)
			verticalScrollBar.unitIncrement = 24
		}
		tocPanel.background = Theme.background
		tocPanel.add(tocScroll, BorderLayout.CENTER)
		tocPanel.preferredSize = Dimension(240, 100)
		tocPanel.isVisible = false

		buildToolbar()
		contentHolder.background = Theme.readerBackground(repo.settings.readerBackground)
		continuousScroll.background = Theme.readerBackground(repo.settings.readerBackground)
		continuousScroll.border = BorderFactory.createEmptyBorder()
		continuousScroll.verticalScrollBar.unitIncrement = 40
		continuousScroll.verticalScrollBar.addAdjustmentListener { onContinuousScroll() }

		splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, contentHolder, tocPanel)
		splitPane.background = Theme.background
		splitPane.border = BorderFactory.createEmptyBorder()
		splitPane.dividerSize = 6

		layout = BorderLayout()
		add(toolbar, BorderLayout.NORTH)
		add(splitPane, BorderLayout.CENTER)
		add(
			JPanel(BorderLayout()).apply {
				background = Theme.surface
				border = BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border),
					BorderFactory.createEmptyBorder(3, 10, 3, 10),
				)
				add(statusLabel, BorderLayout.WEST)
				add(pageLabel, BorderLayout.EAST)
			},
			BorderLayout.SOUTH,
		)

		installKeys()
		installMouse()
		updateContentPane()

		addWindowListener(object : java.awt.event.WindowAdapter() {
			override fun windowClosing(e: java.awt.event.WindowEvent) {
				close()
			}
		})

		fillChapterList()
		openChapter(chapterIndex, page, initial = true)
	}

	// --- Construction helpers -----------------------------------------------------------------

	private fun buildToolbar() {
		toolbar.background = Theme.surface
		toolbar.border = BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border),
			BorderFactory.createEmptyBorder(2, 8, 2, 8),
		)
		toolbar.add(Ui.button("◀ " + tr(K.READER_PREV_CHAPTER)) { previousChapter() })
		toolbar.add(Ui.button(tr(K.READER_NEXT_CHAPTER) + " ▶") { nextChapter() })
		toolbar.add(titleLabel)
		toolbar.add(Box.createHorizontalStrut(8))
		toolbar.add(modeBox)
		toolbar.add(Ui.button("−", tr(K.READER_ZOOM_OUT)) { changeZoom(-repo.settings.readerZoomStep) })
		toolbar.add(Ui.button("+", tr(K.READER_ZOOM_IN)) { changeZoom(repo.settings.readerZoomStep) })
		toolbar.add(Ui.button("1:1", tr(K.READER_ZOOM_RESET)) { resetZoom() })
		toolbar.add(Ui.button("↔", tr(K.READER_FIT_WIDTH)) { fitToWidth = true; repaintCanvas() })
		toolbar.add(Ui.button("↕", tr(K.READER_FIT_HEIGHT)) { fitToWidth = false; repaintCanvas() })
		toolbar.add(Ui.button(tr(K.READER_CONTENTS)) { toggleToc() })
		toolbar.add(Ui.button(tr(K.READER_PAGE_SAVE)) { saveCurrentPage() })
		toolbar.add(Ui.button(tr(K.ACTION_CLOSE)) { close() })
	}

	private fun installKeys() {
		val map = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
		val actions = rootPane.actionMap

		fun bind(keystroke: KeyStroke, name: String, action: () -> Unit) {
			map.put(keystroke, name)
			actions.put(name, object : AbstractAction() {
				override fun actionPerformed(e: ActionEvent) = action()
			})
		}

		bind(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "next") { nextPage() }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "next-down") { nextPage() }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "next-space") { nextPage() }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "next-pgdn") { nextPage() }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prev") { previousPage() }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "prev-up") { previousPage() }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "prev-pgup") { previousPage() }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), "first") { goToPage(0) }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), "last") { goToPage(content.pageCount - 1) }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_N, 0), "next-chapter") { nextChapter() }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0), "prev-chapter") { previousChapter() }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_T, 0), "toc") { toggleToc() }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_M, 0), "cycle-mode") { cycleMode() }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), "save-page") { saveCurrentPage() }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0), "zoom-in") { changeZoom(repo.settings.readerZoomStep) }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), "zoom-in-plus") { changeZoom(repo.settings.readerZoomStep) }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), "zoom-in-add") { changeZoom(repo.settings.readerZoomStep) }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "zoom-out") { changeZoom(-repo.settings.readerZoomStep) }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), "zoom-out-sub") { changeZoom(-repo.settings.readerZoomStep) }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_0, 0), "zoom-reset") { resetZoom() }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), "fit-width") { fitToWidth = true; repaintCanvas() }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_H, 0), "fit-height") { fitToWidth = false; repaintCanvas() }
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exit") {
			if (tocPanel.isVisible) toggleToc() else close()
		}
		bind(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "fullscreen") {
			isUndecorated = false
			extendedState = if (extendedState == JFrame.MAXIMIZED_BOTH) JFrame.NORMAL else JFrame.MAXIMIZED_BOTH
		}
	}

	private fun installMouse() {
		val listener = object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				if (SwingUtilities.isRightMouseButton(e)) {
					nextPage()
					return
				}
				val width = e.component.width
				when {
					e.x < width * 0.3 -> if (isRtl()) nextPage() else previousPage()
					e.x > width * 0.7 -> if (isRtl()) previousPage() else nextPage()
					else -> toolbar.isVisible = !toolbar.isVisible
				}
			}

			override fun mousePressed(e: MouseEvent) {
				dragStart = e.point
			}

			override fun mouseReleased(e: MouseEvent) {
				dragStart = null
			}

			override fun mouseDragged(e: MouseEvent) {
				if (mode.isContinuous) return
				val start = dragStart ?: return
				panOffset = Point(panOffset.x + e.x - start.x, panOffset.y + e.y - start.y)
				dragStart = e.point
				pagedCanvas.repaint()
			}

			override fun mouseWheelMoved(e: MouseWheelEvent) {
				if (e.isControlDown && repo.settings.readerScrollZoom) {
					changeZoom(if (e.wheelRotation < 0) repo.settings.readerZoomStep else -repo.settings.readerZoomStep)
				} else if (!mode.isContinuous) {
					if (e.wheelRotation > 0) nextPage() else previousPage()
				}
			}
		}
		listOf<JComponent>(pagedCanvas, continuousCanvas).forEach { component ->
			component.addMouseListener(listener)
			component.addMouseMotionListener(listener)
			component.addMouseWheelListener(listener)
		}
	}

	private fun updateContentPane() {
		contentHolder.removeAll()
		if (mode.isContinuous) {
			contentHolder.add(continuousScroll, BorderLayout.CENTER)
			continuousCanvas.revalidateContent()
		} else {
			contentHolder.add(pagedCanvas, BorderLayout.CENTER)
		}
		contentHolder.revalidate()
		contentHolder.repaint()
	}

	// --- State --------------------------------------------------------------------------------

	private val ReaderMode.isContinuous: Boolean
		get() = this == ReaderMode.VERTICAL || this == ReaderMode.WEBTOON

	private fun isRtl(): Boolean = mode == ReaderMode.REVERSED

	private fun currentChapter(): Chapter? = manga.sortedChapters.getOrNull(chapterIndex)

	private fun fillChapterList() {
		chapterListModel.clear()
		manga.sortedChapters.forEach(chapterListModel::addElement)
	}

	private fun openChapter(index: Int, startPage: Int, initial: Boolean = false) {
		if (manga.sortedChapters.isEmpty()) {
			statusLabel.text = tr(K.DETAILS_NO_CHAPTERS)
			return
		}
		if (!initial) saveProgress()
		chapterIndex = index.coerceIn(0, manga.sortedChapters.lastIndex)
		generation++
		images.clear()
		loading.clear()
		pageErrors.clear()
		contentError = null
		page = startPage.coerceAtLeast(0)
		panOffset = Point(0, 0)
		val chapter = currentChapter() ?: return
		titleLabel.text = "${manga.manga.displayTitle} — ${chapter.title}"
		statusLabel.text = tr(K.READER_LOADING)
		chapterList.selectedIndex = chapterIndex
		chapterList.ensureIndexIsVisible(chapterIndex)
		val gen = generation
		Async.compute({ repo.chapterContentResult(chapter) }) { outcome ->
			if (gen != generation) return@compute
			if (outcome is org.koitharu.kotatsu.desktop.sources.SourceOutcome.Failure) {
				content = ChapterContent.EMPTY
				contentError = outcome.failure.message()
				statusLabel.text = contentError
				Log.w(TAG, "Chapter ${chapter.chapterId} failed: ${outcome.failure.kind}")
				updateContentPane()
				repaintCanvas()
				return@compute
			}
			content = (outcome as org.koitharu.kotatsu.desktop.sources.SourceOutcome.Success).value
			if (content.pageCount == 0) {
				statusLabel.text = "${tr(K.READER_NO_PAGES)} — ${chapter.url}"
			} else {
				statusLabel.text = ""
				page = page.coerceIn(0, content.pageCount - 1)
				updatePageLabel()
				updateContentPane()
				requestPage(page)
				if (repo.settings.readerPreload) {
					PageImageCache.preload(
						chapter.chapterId,
						listOf(page + 1, page + 2).filter { it < content.pageCount },
						content,
					)
				}
				if (mode.isContinuous) {
					SwingUtilities.invokeLater { restoreContinuousPosition(page) }
				}
			}
			repaintCanvas()
		}
	}

	private fun requestPage(index: Int) {
		if (index !in 0 until content.pageCount) return
		if (images.containsKey(index)) return
		if (!loading.add(index)) return
		val gen = generation
		val chapter = currentChapter() ?: return
		Async.compute({ PageImageCache.loadDetailed(chapter.chapterId, index, content) }) { loaded ->
			if (gen != generation) return@compute
			loading.remove(index)
			when (loaded) {
				is PageLoad.Ok -> {
					images[index] = loaded.image
					pageErrors.remove(index)
				}

				PageLoad.Missing -> {
					images[index] = null
					pageErrors[index] = tr(K.READER_ERROR_NETWORK)
					Log.w(TAG, "Page $index of chapter ${chapter.chapterId} could not be loaded")
				}

				PageLoad.Undecodable -> {
					images[index] = null
					pageErrors[index] = tr(K.READER_ERROR_DECODE)
					Log.w(TAG, "Page $index of chapter ${chapter.chapterId} is not a supported image format")
				}
			}
			repaintCanvas()
		}
	}

	private fun updatePageLabel() {
		pageLabel.text = if (content.pageCount > 0) {
			tr(K.READER_PAGE_OF).format(page + 1, content.pageCount)
		} else {
			""
		}
	}

	private fun repaintCanvas() {
		if (mode.isContinuous) {
			continuousCanvas.revalidateContent()
			continuousCanvas.repaint()
		} else {
			pagedCanvas.repaint()
		}
		updatePageLabel()
	}

	private fun scheduleSave() {
		if (!repo.settings.readerSaveProgress) return
		saveTimer.restart()
	}

	private fun saveProgress() {
		val chapter = currentChapter() ?: return
		val chapterCount = manga.sortedChapters.size
		val scroll = if (mode.isContinuous) continuousScroll.verticalScrollBar.value else 0
		repo.saveProgress(
			manga = manga.manga,
			chapter = chapter,
			chapterIndex = chapterIndex,
			page = page,
			pagesCount = content.pageCount.coerceAtLeast(1),
			scroll = scroll,
		)
		Log.d(TAG, "Progress saved: chapter=$chapterIndex page=$page of ${content.pageCount}")
		statusLabel.text = tr(K.READER_SAVED)
	}

	// --- Navigation ---------------------------------------------------------------------------

	private fun goToPage(target: Int) {
		if (content.pageCount == 0) return
		page = target.coerceIn(0, content.pageCount - 1)
		panOffset = Point(0, 0)
		requestPage(page)
		if (repo.settings.readerPreload) {
			val chapter = currentChapter()
			if (chapter != null) {
				PageImageCache.preload(
					chapter.chapterId,
					listOf(page + 1, page + 2).filter { it < content.pageCount },
					content,
				)
			}
		}
		if (mode.isContinuous) {
			restoreContinuousPosition(page)
		} else {
			repaintCanvas()
		}
		statusLabel.text = ""
		scheduleSave()
	}

	private fun nextPage() {
		if (mode.isContinuous) {
			scrollContinuous(1)
			return
		}
		if (page < content.pageCount - 1) {
			goToPage(page + 1)
		} else if (chapterIndex < manga.sortedChapters.lastIndex) {
			openChapter(chapterIndex + 1, 0)
		} else {
			statusLabel.text = tr(K.READER_END_OF_MANGA)
		}
	}

	private fun previousPage() {
		if (mode.isContinuous) {
			scrollContinuous(-1)
			return
		}
		if (page > 0) {
			goToPage(page - 1)
		} else if (chapterIndex > 0) {
			openChapter(chapterIndex - 1, Int.MAX_VALUE)
		} else {
			statusLabel.text = tr(K.HISTORY_EMPTY)
		}
	}

	private fun nextChapter() {
		if (chapterIndex < manga.sortedChapters.lastIndex) {
			openChapter(chapterIndex + 1, 0)
		} else {
			statusLabel.text = tr(K.READER_END_OF_MANGA)
		}
	}

	private fun previousChapter() {
		if (chapterIndex > 0) openChapter(chapterIndex - 1, 0) else statusLabel.text = tr(K.HISTORY_EMPTY)
	}

	private fun setMode(newMode: ReaderMode) {
		if (mode == newMode) return
		mode = newMode
		repo.settings.defaultReaderMode = newMode
		if (modeBox.selectedItem != newMode) modeBox.selectedItem = newMode
		zoom = 1f
		updateContentPane()
		repaintCanvas()
	}

	private fun cycleMode() {
		val entries = ReaderMode.entries
		val next = entries[(entries.indexOf(mode) + 1) % entries.size]
		setMode(next)
	}

	private fun changeZoom(delta: Float) {
		zoom = (zoom + delta).coerceIn(MIN_ZOOM, MAX_ZOOM)
		if (mode.isContinuous) continuousCanvas.revalidateContent() else pagedCanvas.repaint()
		statusLabel.text = "Zoom: ${(zoom * 100).toInt()}%"
	}

	private fun resetZoom() {
		zoom = 1f
		panOffset = Point(0, 0)
		if (mode.isContinuous) continuousCanvas.revalidateContent() else pagedCanvas.repaint()
		statusLabel.text = "Zoom: 100%"
	}

	private fun toggleToc() {
		tocPanel.isVisible = !tocPanel.isVisible
		splitPane.dividerLocation = width - if (tocPanel.isVisible) 246 else 0
		splitPane.revalidate()
		splitPane.repaint()
	}

	private fun saveCurrentPage() {
		val chapter = currentChapter() ?: return
		val bytes = content.readPage(page)
		if (bytes == null) {
			Ui.showError(this, tr(K.READER_ERROR_PAGE))
			return
		}
		val chooser = javax.swing.JFileChooser().apply {
			dialogTitle = tr(K.READER_PAGE_SAVE)
			val extension = content.pageName(page).substringAfterLast('.', "png")
			selectedFile = File("${chapter.title}-${page + 1}.$extension")
		}
		if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return
		val target = chooser.selectedFile
		runCatching { Files.write(target.toPath(), bytes) }
			.onSuccess { Ui.showInfo(this, tr(K.READER_PAGE_SAVED).format(target.absolutePath)) }
			.onFailure { Ui.showError(this, it.message ?: it.javaClass.simpleName) }
	}

	private fun close() {
		saveTimer.stop()
		saveProgress()
		isVisible = false
		dispose()
		PageImageCache.clear()
		navigator.refreshAll()
	}

	// --- Continuous scrolling helpers ---------------------------------------------------------

	private fun scrollContinuous(pages: Int) {
		val bar: JScrollBar = continuousScroll.verticalScrollBar
		val step = (continuousScroll.viewport.height * 0.9).toInt()
		bar.value = (bar.value + pages * step).coerceIn(0, (bar.maximum - bar.visibleAmount).coerceAtLeast(0))
	}

	private fun onContinuousScroll() {
		if (restoringScroll) return
		val current = continuousCanvas.pageAtY(continuousScroll.verticalScrollBar.value)
		if (current != page && current in 0 until content.pageCount) {
			page = current
			requestPage(current)
			if (repo.settings.readerPreload) {
				currentChapter()?.let { chapter ->
					PageImageCache.preload(
						chapter.chapterId,
						listOf(current + 1, current + 2).filter { it < content.pageCount },
						content,
					)
				}
			}
			updatePageLabel()
			scheduleSave()
		}
		continuousCanvas.repaint()
	}

	private fun restoreContinuousPosition(pageIndex: Int) {
		continuousCanvas.revalidateContent()
		val y = continuousCanvas.yOfPage(pageIndex)
		restoringScroll = true
		try {
			val bar = continuousScroll.verticalScrollBar
			bar.value = y.coerceIn(0, (bar.maximum - bar.visibleAmount).coerceAtLeast(0))
		} finally {
			restoringScroll = false
		}
	}

	// --- Canvases -----------------------------------------------------------------------------

	private fun pageWidth(): Int = (contentHolder.width.coerceAtLeast(200)).toDouble().toInt()

	private inner class PagedCanvas : JPanel() {

		init {
			background = Theme.readerBackground(repo.settings.readerBackground)
			isOpaque = true
			isFocusable = true
		}

		override fun paintComponent(g: Graphics) {
			super.paintComponent(g)
			val g2 = g.create() as Graphics2D
			try {
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
				contentError?.let { message ->
					drawCenteredMessage(g2, shorten(message))
					return
				}
				if (content.pageCount == 0) {
					drawCenteredMessage(g2, tr(K.READER_NO_PAGES))
					return
				}
				val image = images[page]
				if (image == null) {
					requestPage(page)
					drawCenteredMessage(g2, pageErrors[page] ?: tr(K.READER_LOADING))
					return
				}
				val placement = PageLayout.place(
					imageWidth = image.width,
					imageHeight = image.height,
					viewWidth = width,
					viewHeight = height,
					fitToWidth = fitToWidth,
					zoom = zoom,
					panX = panOffset.x,
					panY = panOffset.y,
				)
				// Feed the clamped position back so dragging never "escapes" the page.
				panOffset.x = placement.x - (width - placement.width) / 2
				panOffset.y = placement.y - (height - placement.height) / 2
				g2.drawImage(image, placement.x, placement.y, placement.width, placement.height, null)
				drawPageNumberOverlay(g2)
			} finally {
				g2.dispose()
			}
		}
	}

	private inner class ContinuousCanvas : JPanel() {

		private var layout: List<Pair<Int, Int>> = emptyList() // y offset to height
		private var totalHeight = 0

		init {
			background = Theme.readerBackground(repo.settings.readerBackground)
			isOpaque = true
			isFocusable = true
		}

		private fun gap(): Int = if (mode == ReaderMode.WEBTOON) 0 else repo.settings.readerPageGap

		fun revalidateContent() {
			val viewWidth = (continuousScroll.viewport.width.takeIf { it > 0 } ?: contentHolder.width)
				.coerceAtLeast(200)
			val result = ArrayList<Pair<Int, Int>>(content.pageCount)
			var y = 0
			for (index in 0 until content.pageCount) {
				requestPage(index)
				val image = images[index]
				val scaledHeight = if (image != null) {
					val scale = PageLayout.continuousScale(image.width, viewWidth, zoom)
					(image.height * scale).toInt().coerceAtLeast(1)
				} else {
					// Placeholder height until the real page arrives
					(viewWidth * 1.4).toInt()
				}
				result.add(y to scaledHeight)
				y += scaledHeight + gap()
			}
			layout = result
			totalHeight = y.coerceAtLeast(1)
			preferredSize = Dimension(viewWidth, totalHeight)
			revalidate()
		}

		fun yOfPage(index: Int): Int = layout.getOrNull(index)?.first ?: 0

		fun pageAtY(y: Int): Int {
			if (layout.isEmpty()) return 0
			for (i in layout.indices) {
				val (top, height) = layout[i]
				if (y >= top && y < top + height) return i
			}
			return if (y >= totalHeight) layout.lastIndex else 0
		}

		override fun paintComponent(g: Graphics) {
			super.paintComponent(g)
			val g2 = g.create() as Graphics2D
			try {
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
				val clip = g2.clipBounds
				val viewWidth = width
				layout.forEachIndexed { index, (top, height) ->
					if (top + height < clip.y - 400 || top > clip.y + clip.height + 400) return@forEachIndexed
					val image = images[index]
					if (image == null) {
						g2.color = Theme.surfaceVariant
						g2.fillRect(0, top, viewWidth, height)
						g2.color = Theme.onSurfaceVariant
						g2.font = Theme.fontSmall
						val placeholder = pageErrors[index] ?: "…"
						g2.drawString(
							shorten(placeholder),
							(viewWidth - g2.fontMetrics.stringWidth(shorten(placeholder))) / 2,
							top + height / 2,
						)
						return@forEachIndexed
					}
					val drawWidth = (viewWidth * zoom).toInt()
					val drawHeight = (image.height * (drawWidth.toDouble() / image.width)).toInt()
					g2.drawImage(image, 0, top, drawWidth, drawHeight, null)
				}
			} finally {
				g2.dispose()
			}
		}
	}

	/** Keeps a long error message inside the page area. */
	private fun shorten(message: String): String =
		if (message.length <= 70) message else message.take(67) + "…"

	private fun drawCenteredMessage(g2: Graphics2D, message: String) {
		g2.color = Theme.onSurfaceVariant
		g2.font = Theme.fontH2
		val metrics = g2.fontMetrics
		val x = (width - metrics.stringWidth(message)) / 2
		g2.drawString(message, x, height / 2)
	}

	private fun drawPageNumberOverlay(g2: Graphics2D) {
		val text = "${page + 1} / ${content.pageCount}"
		g2.font = Theme.fontSmall
		val metrics = g2.fontMetrics
		val borderRadius = 8
		val padding = 6
		val width = metrics.stringWidth(text) + padding * 2
		val height = metrics.height + padding
		g2.color = Color(0, 0, 0, 150)
		g2.fillRoundRect(this.width - width - 12, this.height - height - 12, width, height, borderRadius, borderRadius)
		g2.color = Color.WHITE
		g2.drawString(text, this.width - width - 12 + padding, this.height - height - 12 + metrics.ascent + padding / 2)
	}

	// --- Renderers ----------------------------------------------------------------------------

	private inner class ModeRenderer : javax.swing.DefaultListCellRenderer() {
		override fun getListCellRendererComponent(
			list: JList<*>?,
			value: Any?,
			index: Int,
			isSelected: Boolean,
			cellHasFocus: Boolean,
		): java.awt.Component {
			val label = when (value) {
				ReaderMode.STANDARD -> tr(K.READER_MODE_STANDARD)
				ReaderMode.REVERSED -> tr(K.READER_MODE_RTL)
				ReaderMode.VERTICAL -> tr(K.READER_MODE_VERTICAL)
				ReaderMode.WEBTOON -> tr(K.READER_MODE_WEBTOON)
				else -> value?.toString() ?: ""
			}
			return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus)
		}
	}

	private inner class ChapterCell : JPanel(), javax.swing.ListCellRenderer<Chapter> {

		private val label = JLabel().apply { font = Theme.baseFont }

		init {
			layout = BorderLayout()
			background = Theme.background
			border = BorderFactory.createEmptyBorder(5, 8, 5, 8)
			add(label, BorderLayout.CENTER)
		}

		override fun getListCellRendererComponent(
			list: JList<out Chapter>?,
			value: Chapter?,
			index: Int,
			isSelected: Boolean,
			cellHasFocus: Boolean,
		): java.awt.Component {
			label.text = value?.title.orEmpty()
			background = when {
				isSelected -> Theme.accent
				index == chapterIndex -> Theme.surfaceVariant
				else -> Theme.background
			}
			label.foreground = if (isSelected) Color.WHITE else Theme.onBackground
			// A JPanel renderer is not laid out by JList: do it here or the label stays invisible.
			val width = list?.width ?: 0
			if (width > 0) setSize(width, preferredSize.height)
			Ui.layoutTree(this)
			return this
		}
	}

}


/** Opens (and shows) a reader window. */
fun openReaderWindow(
	owner: JFrame,
	repo: AppRepository,
	manga: MangaWithChapters,
	chapterIndex: Int,
	page: Int,
	navigator: Navigator,
): ReaderFrame {
	val frame = ReaderFrame(owner, repo, manga, navigator, chapterIndex, page)
	frame.isVisible = true
	return frame
}
