/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.ui

import org.koitharu.kotatsu.desktop.core.AppDirs
import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.core.i18n.AppLanguage
import org.koitharu.kotatsu.desktop.core.i18n.I18n
import org.koitharu.kotatsu.desktop.core.i18n.K
import org.koitharu.kotatsu.desktop.core.i18n.tr
import org.koitharu.kotatsu.desktop.data.AppRepository
import org.koitharu.kotatsu.desktop.core.BuildInfo
import org.koitharu.kotatsu.desktop.data.store.ThemeMode
import org.koitharu.kotatsu.desktop.ui.details.DetailsPanel
import org.koitharu.kotatsu.desktop.ui.dialogs.BackupDialog
import org.koitharu.kotatsu.desktop.ui.downloads.DownloadsPanel
import org.koitharu.kotatsu.desktop.ui.history.HistoryPanel
import org.koitharu.kotatsu.desktop.ui.hub.HubPanel
import org.koitharu.kotatsu.desktop.ui.library.LibraryPanel
import org.koitharu.kotatsu.desktop.ui.reader.openReaderWindow
import org.koitharu.kotatsu.desktop.ui.settings.SettingsPanel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBoxMenuItem
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.TransferHandler
import javax.swing.WindowConstants

class MainWindow(private val repo: AppRepository) : JFrame(), Navigator {

	private enum class Route(val card: String) {
		LIBRARY("library"),
		HUB("hub"),
		HISTORY("history"),
		DOWNLOADS("downloads"),
		SETTINGS("settings"),
		DETAILS("details"),
	}

	private val cards = CardLayout()
	private val content = JPanel(cards).apply { background = Theme.background }
	private val statusLabel = JLabel(tr(K.APP_READY)).apply {
		font = Theme.fontSmall
		foreground = Theme.onSurfaceVariant
	}
	private val countLabel = JLabel("").apply {
		font = Theme.fontSmall
		foreground = Theme.onSurfaceVariant
	}

	private val libraryPanel = LibraryPanel(repo, this)
	private val hubPanel = HubPanel(repo, this)
	private val historyPanel = HistoryPanel(repo, this)
	private val downloadsPanel = DownloadsPanel(repo, this)
	private val settingsPanel = SettingsPanel(repo, this)
	private val detailsPanel = DetailsPanel(repo, this)

	private val navButtons = LinkedHashMap<Route, JButton>()
	private var currentRoute = Route.LIBRARY
	private var fullScreen = false
	private var savedBounds: java.awt.Rectangle? = null

	init {
		title = tr(K.APP_TITLE)
		defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
		minimumSize = Dimension(940, 620)
		applyWindowGeometry()

		contentPane.background = Theme.background
		layout = BorderLayout()
		add(createSidebar(), BorderLayout.WEST)
		add(createContent(), BorderLayout.CENTER)
		add(createStatusBar(), BorderLayout.SOUTH)
		jMenuBar = createMenuBar()

		addWindowListener(object : WindowAdapter() {
			override fun windowClosing(e: WindowEvent) = shutdown()
		})
		installShortcuts()
		installDropTarget()

		showRoute(Route.LIBRARY)
		refreshAll()
	}

	// --- Layout -------------------------------------------------------------------------------

	private fun applyWindowGeometry() {
		val width = repo.settings.windowWidth.coerceAtLeast(940)
		val height = repo.settings.windowHeight.coerceAtLeast(620)
		val x = repo.settings.windowX
		val y = repo.settings.windowY
		if (x != Int.MIN_VALUE && y != Int.MIN_VALUE) {
			setBounds(x, y, width, height)
		} else {
			setSize(width, height)
			setLocationRelativeTo(null)
		}
		if (repo.settings.windowMaximized) extendedState = JFrame.MAXIMIZED_BOTH
	}

	private fun createSidebar(): JPanel = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		background = Theme.surface
		border = BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.border),
			BorderFactory.createEmptyBorder(14, 10, 14, 10),
		)
		preferredSize = Dimension(196, 100)
		add(
			JLabel(tr(K.APP_TITLE)).apply {
				font = Theme.fontH1
				foreground = Theme.accent
				alignmentX = LEFT_ALIGNMENT
			},
		)
		add(
			JLabel(tr(K.APP_SUBTITLE)).apply {
				font = Theme.fontSmall
				foreground = Theme.onSurfaceVariant
				alignmentX = LEFT_ALIGNMENT
				border = BorderFactory.createEmptyBorder(2, 0, 16, 0)
			},
		)
		add(navButton(Route.LIBRARY, K.NAV_LIBRARY))
		add(navButton(Route.HUB, K.NAV_HUB))
		add(navButton(Route.HISTORY, K.NAV_HISTORY))
		add(navButton(Route.DOWNLOADS, K.NAV_DOWNLOADS))
		add(Box.createVerticalStrut(8))
		add(JSeparator().apply { alignmentX = LEFT_ALIGNMENT; maximumSize = Dimension(180, 1) })
		add(Box.createVerticalStrut(8))
		add(navButton(Route.SETTINGS, K.NAV_SETTINGS))
		add(Box.createVerticalGlue())
		add(
			JLabel("v${BuildInfo.APP_VERSION}").apply {
				font = Theme.fontSmall
				foreground = Theme.onSurfaceVariant
				alignmentX = LEFT_ALIGNMENT
			},
		)
	}

	private fun navButton(route: Route, textKey: String): JButton = JButton(tr(textKey)).apply {
		font = Theme.baseFont
		horizontalAlignment = SwingConstants.LEFT
		maximumSize = Dimension(180, 34)
		alignmentX = LEFT_ALIGNMENT
		isFocusPainted = false
		isContentAreaFilled = false
		foreground = Theme.onBackground
		border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
		addActionListener {
			when (route) {
				Route.LIBRARY -> showLibrary()
				Route.HUB -> showHub()
				Route.HISTORY -> showHistory()
				Route.DOWNLOADS -> showDownloads()
				Route.SETTINGS -> showSettings()
				Route.DETAILS -> showLibrary()
			}
		}
		addMouseListener(object : java.awt.event.MouseAdapter() {
			override fun mouseEntered(e: java.awt.event.MouseEvent) {
				if (!isContentAreaFilled) background = Theme.surfaceVariant
			}

			override fun mouseExited(e: java.awt.event.MouseEvent) {
				if (!isContentAreaFilled) background = Theme.surface
			}
		})
		navButtons[route] = this
	}

	private fun createContent(): JPanel = JPanel(BorderLayout()).apply {
		background = Theme.background
		add(content, BorderLayout.CENTER)
		content.add(libraryPanel, Route.LIBRARY.card)
		content.add(hubPanel, Route.HUB.card)
		content.add(historyPanel, Route.HISTORY.card)
		content.add(downloadsPanel, Route.DOWNLOADS.card)
		content.add(settingsPanel, Route.SETTINGS.card)
		content.add(detailsPanel, Route.DETAILS.card)
	}

	private fun createStatusBar(): JPanel = JPanel(BorderLayout()).apply {
		background = Theme.surface
		border = BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border),
			BorderFactory.createEmptyBorder(4, 10, 4, 10),
		)
		add(statusLabel, BorderLayout.WEST)
		add(countLabel, BorderLayout.EAST)
	}

	private fun createMenuBar(): JMenuBar = JMenuBar().apply {
		background = Theme.surface
		add(
			JMenu(tr(K.MENU_FILE)).apply {
				add(menuItem(K.MENU_FILE_OPEN_ARCHIVE, KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK) { importFiles() })
				add(menuItem(K.MENU_FILE_IMPORT_FOLDER) { importFolder() })
				add(JSeparator())
				add(menuItem(K.MENU_FILE_BACKUP) { createBackup() })
				add(menuItem(K.MENU_FILE_RESTORE) { restoreBackup() })
				add(JSeparator())
				add(menuItem(K.MENU_FILE_EXIT) { shutdown() })
			},
		)
		add(
			JMenu(tr(K.MENU_VIEW)).apply {
				add(menuItem(K.NAV_LIBRARY, KeyEvent.VK_1, KeyEvent.ALT_DOWN_MASK) { showLibrary() })
				add(menuItem(K.NAV_HUB, KeyEvent.VK_5, KeyEvent.ALT_DOWN_MASK) { showHub() })
				add(menuItem(K.NAV_HISTORY, KeyEvent.VK_2, KeyEvent.ALT_DOWN_MASK) { showHistory() })
				add(menuItem(K.NAV_DOWNLOADS, KeyEvent.VK_3, KeyEvent.ALT_DOWN_MASK) { showDownloads() })
				add(menuItem(K.NAV_SETTINGS, KeyEvent.VK_4, KeyEvent.ALT_DOWN_MASK) { showSettings() })
				add(JSeparator())
				add(menuItem(K.MENU_VIEW_RELOAD, KeyEvent.VK_F5, 0) { refreshAll() })
				add(menuItem(K.MENU_VIEW_FULLSCREEN, KeyEvent.VK_F11, 0) { toggleFullScreen() })
				add(JSeparator())
				val themeMenu = JMenu(tr(K.MENU_VIEW_THEME))
				ThemeMode.entries.forEach { mode ->
					themeMenu.add(
						JCheckBoxMenuItem(
							when (mode) {
								ThemeMode.DARK -> tr(K.SETTINGS_THEME_DARK)
								ThemeMode.LIGHT -> tr(K.SETTINGS_THEME_LIGHT)
							},
							repo.settings.theme == mode,
						).apply {
							addActionListener { changeTheme(mode) }
						},
					)
				}
				add(themeMenu)
				val languageMenu = JMenu(tr(K.MENU_VIEW_LANGUAGE))
				AppLanguage.entries.forEach { language ->
					languageMenu.add(
						JCheckBoxMenuItem(language.displayName, I18n.language == language).apply {
							addActionListener { changeLanguage(language) }
						},
					)
				}
				add(languageMenu)
			},
		)
		add(
			JMenu(tr(K.MENU_HELP)).apply {
				add(menuItem(K.MENU_HELP_SHORTCUTS) { Ui.showInfo(this@MainWindow, tr(K.SHORTCUTS_BODY), tr(K.SHORTCUTS_TITLE)) })
				add(menuItem(K.MENU_HELP_DATA_FOLDER) { Ui.openFolder(this@MainWindow, AppDirs.dataDir) })
				add(menuItem(K.MENU_HELP_LOG_FOLDER) { Ui.openFolder(this@MainWindow, AppDirs.logsDir) })
				add(JSeparator())
				add(menuItem(K.MENU_HELP_ABOUT) { showAbout() })
			},
		)
	}

	private fun menuItem(textKey: String, accelerator: Int? = null, modifiers: Int = 0, action: () -> Unit): JMenuItem =
		JMenuItem(tr(textKey)).apply {
			if (accelerator != null) this.accelerator = KeyStroke.getKeyStroke(accelerator, modifiers)
			addActionListener { action() }
		}

	private fun installShortcuts() {
		Ui.registerShortcut(rootPane, KeyEvent.VK_F5, 0) { refreshAll() }
		Ui.registerShortcut(rootPane, KeyEvent.VK_F11, 0) { toggleFullScreen() }
		Ui.registerShortcut(rootPane, KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK) { importFiles() }
		Ui.registerShortcut(rootPane, KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK) {
			if (currentRoute != Route.LIBRARY) showLibrary()
			libraryPanel.focusSearch()
		}
		Ui.registerShortcut(rootPane, KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK) {
			showHub()
			hubPanel.focusSearch()
		}
	}

	private fun installDropTarget() {
		transferHandler = object : TransferHandler() {
			override fun canImport(support: TransferSupport): Boolean =
				support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)

			override fun importData(support: TransferSupport): Boolean {
				val data = support.transferable
				@Suppress("UNCHECKED_CAST")
				val files = data.getTransferData(DataFlavor.javaFileListFlavor) as? List<java.io.File> ?: return false
				importPaths(files.map { it.toPath() })
				return true
			}
		}
	}

	// --- Navigator ----------------------------------------------------------------------------

	override fun showLibrary() {
		showRoute(Route.LIBRARY)
		libraryPanel.onShown()
	}

	override fun showHub() {
		showRoute(Route.HUB)
		hubPanel.onShown()
	}

	override fun showHistory() {
		showRoute(Route.HISTORY)
		historyPanel.onShown()
	}

	override fun showDownloads() {
		showRoute(Route.DOWNLOADS)
		downloadsPanel.onShown()
	}

	override fun showSettings() {
		showRoute(Route.SETTINGS)
		settingsPanel.onShown()
	}

	override fun showDetails(mangaId: Long) {
		detailsPanel.showManga(mangaId)
		showRoute(Route.DETAILS)
	}

	override fun backToLibrary() {
		showLibrary()
	}

	override fun openReader(mangaId: Long, chapterIndex: Int, page: Int) {
		val mangaWithChapters = repo.details(mangaId)
		if (mangaWithChapters == null) {
			Ui.showError(this, tr(K.ERROR_FILE_NOT_FOUND).format(mangaId))
			return
		}
		openReaderWindow(this, repo, mangaWithChapters, chapterIndex, page, this)
	}

	override fun setStatus(text: String) {
		statusLabel.text = text
	}

	override fun refreshAll() {
		Async.compute({ repo.libraryItems() }) { items ->
			countLabel.text = tr(K.LIBRARY_COUNT).format(items.size)
			libraryPanel.onShown()
			historyPanel.onShown()
			downloadsPanel.onShown()
		}
	}

	private fun showRoute(route: Route) {
		currentRoute = route
		cards.show(content, route.card)
		navButtons.forEach { (r, button) ->
			val active = r == route
			button.isContentAreaFilled = active
			button.background = if (active) Theme.surfaceVariant else Theme.surface
		}
		if (route != Route.DETAILS && route != Route.LIBRARY) detailsPanel.onHidden()
	}

	// --- Actions ------------------------------------------------------------------------------

	private fun importFiles() {
		val start = repo.settings.lastImportDirectory?.let { runCatching { Paths.get(it) }.getOrNull() }
		val paths = Ui.chooseFiles(this, start ?: AppDirs.dataDir)
		if (paths.isEmpty()) return
		importPaths(paths)
	}

	private fun importFolder() {
		val start = repo.settings.lastImportDirectory?.let { runCatching { Paths.get(it) }.getOrNull() }
		val path = Ui.chooseDirectory(this, start ?: AppDirs.dataDir) ?: return
		importPaths(listOf(path))
	}

	private fun importPaths(paths: List<Path>) {
		setStatus(tr(K.APP_LOADING))
		paths.firstOrNull()?.parent?.let { repo.settings.lastImportDirectory = it.toString() }
		Async.compute({ repo.importPaths(paths) }) { result ->
			val message = buildString {
				append(tr(K.LOCAL_IMPORT_RESULT).format(result.importedManga + result.updatedManga))
				if (result.importedChapters > 0) append("\n").append(tr(K.DETAILS_CHAPTERS)).append(": ${result.importedChapters}")
				if (result.skipped > 0) append("\n").append(tr(K.LOCAL_IMPORT_SKIPPED).format(result.skipped))
				if (result.messages.isNotEmpty()) {
					append("\n\n")
					append(result.messages.take(15).joinToString("\n"))
				}
			}
			Ui.showInfo(this, message)
			setStatus(tr(K.APP_READY))
			refreshAll()
			showLibrary()
		}.also { setStatus(tr(K.APP_READY)) }
	}

	private fun createBackup() {
		BackupDialog.showExport(this, repo)
	}

	private fun restoreBackup() {
		BackupDialog.showImport(this, repo) { refreshAll() }
	}

	private fun changeTheme(mode: ThemeMode) {
		repo.settings.theme = mode
		Theme.set(mode)
		Ui.showInfo(this, "Theme changed to ${mode.name.lowercase()}. Restart the application to fully apply it everywhere.")
	}

	private fun changeLanguage(language: AppLanguage) {
		repo.settings.language = language
		I18n.setLanguage(language)
		Ui.showInfo(this, "Language: ${language.displayName}. Restart the application to apply the new language.")
	}

	private fun toggleFullScreen() {
		if (fullScreen) {
			savedBounds?.let(::setBounds)
			dispose()
			isUndecorated = false
			fullScreen = false
			val device = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
			if (device.isFullScreenSupported) device.fullScreenWindow = null
			isVisible = true
		} else {
			savedBounds = bounds
			val device = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
			dispose()
			isUndecorated = true
			fullScreen = true
			if (device.isFullScreenSupported) device.fullScreenWindow = this else extendedState = JFrame.MAXIMIZED_BOTH
			isVisible = true
		}
	}

	private fun showAbout() {
		val text = tr(K.ABOUT_BODY) + "\n\n" + tr(K.ABOUT_LICENSE) + "\n\nData: " + AppDirs.dataDir
		Ui.showInfo(this, text, tr(K.ABOUT_TITLE))
	}

	private fun shutdown() {
		runCatching {
			if (extendedState == JFrame.NORMAL) {
				repo.settings.windowX = x
				repo.settings.windowY = y
				repo.settings.windowWidth = width
				repo.settings.windowHeight = height
			}
			repo.settings.windowMaximized = extendedState == JFrame.MAXIMIZED_BOTH
			repo.flush()
		}
		isVisible = false
		dispose()
		Async.shutdown()
		System.exit(0)
	}

	/** Opens the reader window on the currently selected title (used by keyboard shortcuts). */
	fun openSelected() = libraryPanel.openSelected()
}
