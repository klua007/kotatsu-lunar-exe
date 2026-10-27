/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file, replacing the Android preferences screens with a Swing form.
 * Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.ui.settings

import org.koitharu.kotatsu.desktop.core.AppDirs
import org.koitharu.kotatsu.desktop.core.BuildInfo
import org.koitharu.kotatsu.desktop.core.i18n.AppLanguage
import org.koitharu.kotatsu.desktop.core.i18n.I18n
import org.koitharu.kotatsu.desktop.core.i18n.K
import org.koitharu.kotatsu.desktop.core.i18n.tr
import org.koitharu.kotatsu.desktop.core.util.humanFileSize
import org.koitharu.kotatsu.desktop.data.AppRepository
import org.koitharu.kotatsu.desktop.data.model.ReaderMode
import org.koitharu.kotatsu.desktop.data.store.ReaderBackground
import org.koitharu.kotatsu.desktop.data.store.ThemeMode
import org.koitharu.kotatsu.desktop.ui.Async
import org.koitharu.kotatsu.desktop.ui.Navigator
import org.koitharu.kotatsu.desktop.ui.Screen
import org.koitharu.kotatsu.desktop.ui.Theme
import org.koitharu.kotatsu.desktop.ui.Ui
import org.koitharu.kotatsu.desktop.ui.dialogs.BackupDialog
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.nio.file.Files
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SpinnerNumberModel

class SettingsPanel(
	private val repo: AppRepository,
	private val navigator: Navigator,
) : JPanel(BorderLayout()), Screen {

	private val content = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		background = Theme.background
		border = BorderFactory.createEmptyBorder(10, 18, 20, 18)
	}
	private val cacheLabel = Ui.label("", Theme.fontSmall, Theme.onSurfaceVariant)
	private val compatibilityLabel = Ui.label("", Theme.fontSmall, Theme.onSurfaceVariant)

	init {
		background = Theme.background
		val scroll = JScrollPane(content)
		scroll.background = Theme.background
		scroll.border = BorderFactory.createEmptyBorder()
		scroll.verticalScrollBar.unitIncrement = 24
		add(scroll, BorderLayout.CENTER)
		buildForm()
	}

	private fun buildForm() {
		content.add(Ui.title(tr(K.SETTINGS_TITLE)))
		content.add(Ui.vgap(10))

		// Appearance
		content.add(section(tr(K.SETTINGS_APPEARANCE)))
		val languageBox = Ui.comboBox(AppLanguage.entries.toList()).apply {
			selectedItem = I18n.language
			renderer = javax.swing.DefaultListCellRenderer().let { renderer -> renderer }
			addActionListener {
				val language = selectedItem as? AppLanguage ?: return@addActionListener
				repo.settings.language = language
				I18n.setLanguage(language)
				Ui.showInfo(this@SettingsPanel, "Restart the application to apply the language.")
			}
		}
		content.add(row(tr(K.SETTINGS_LANGUAGE), languageBox))
		val themeBox = Ui.comboBox(ThemeMode.entries.toList()).apply {
			selectedItem = repo.settings.theme
			addActionListener {
				val mode = selectedItem as? ThemeMode ?: return@addActionListener
				repo.settings.theme = mode
				Theme.set(mode, repo.settings.uiScale)
				navigator.applyTheme()
			}
		}
		content.add(row(tr(K.SETTINGS_THEME), themeBox))
		val scaleBox = Ui.comboBox(UiScaleChoice.entries.toList()).apply {
			selectedItem = UiScaleChoice.closestTo(repo.settings.uiScale)
			renderer = javax.swing.DefaultListCellRenderer()
			addActionListener {
				val choice = selectedItem as? UiScaleChoice ?: return@addActionListener
				repo.settings.uiScale = choice.scale
				Theme.set(repo.settings.theme, choice.scale)
				navigator.applyTheme()
			}
		}
		content.add(row(tr(K.SETTINGS_UI_SCALE), scaleBox))

		// Reader
		content.add(Ui.vgap(8))
		content.add(section(tr(K.SETTINGS_READER)))
		val modeBox = Ui.comboBox(ReaderMode.entries.toList()).apply {
			selectedItem = repo.settings.defaultReaderMode
			renderer = ReaderModeRenderer()
			addActionListener {
				val mode = selectedItem as? ReaderMode ?: return@addActionListener
				repo.settings.defaultReaderMode = mode
			}
		}
		content.add(row(tr(K.SETTINGS_READER_DEFAULT_MODE), modeBox))
		val backgroundBox = Ui.comboBox(ReaderBackground.entries.toList()).apply {
			selectedItem = repo.settings.readerBackground
			addActionListener {
				val value = selectedItem as? ReaderBackground ?: return@addActionListener
				repo.settings.readerBackground = value
			}
		}
		content.add(row(tr(K.READER_BACKGROUND), backgroundBox))
		val gapSpinner = Ui.spinner(SpinnerNumberModel(repo.settings.readerPageGap, 0, 96, 4)).apply {
			preferredSize = Dimension(80, 28)
			addChangeListener { repo.settings.readerPageGap = (value as Number).toInt() }
		}
		content.add(row(tr(K.READER_GAP), gapSpinner))
		content.add(
			checkbox(tr(K.SETTINGS_READER_PRELOAD), repo.settings.readerPreload) {
				repo.settings.readerPreload = it
			},
		)
		content.add(
			checkbox(tr(K.SETTINGS_READER_PROGRESS_SAVE), repo.settings.readerSaveProgress) {
				repo.settings.readerSaveProgress = it
			},
		)
		content.add(
			checkbox(tr(K.SETTINGS_READER_SCROLL_ZOOM), repo.settings.readerScrollZoom) {
				repo.settings.readerScrollZoom = it
			},
		)

		// Storage
		content.add(Ui.vgap(8))
		content.add(section(tr(K.SETTINGS_STORAGE)))
		content.add(pathRow(tr(K.SETTINGS_DATA_FOLDER), AppDirs.dataDir, openDownloads = false))
		content.add(pathRow(tr(K.SETTINGS_CACHE_FOLDER), AppDirs.cacheDir, openDownloads = false))
		content.add(pathRow(tr(K.NAV_DOWNLOADS), AppDirs.downloadsDir, openDownloads = true))
		val storageButtons = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
			background = Theme.background
			add(
				Ui.button(tr(K.SETTINGS_CACHE_CLEAR)) {
					Async.compute({ clearCache() }) { count ->
						refreshCacheLabel()
						Ui.showInfo(this@SettingsPanel, tr(K.SETTINGS_CACHE_CLEARED_COUNT).format(count))
					}
				},
			)
			add(Ui.button(tr(K.ACTION_REFRESH)) { refreshCacheLabel() })
			add(
				Ui.button(tr(K.SETTINGS_CLEAR_PAGE_CACHE)) {
					Async.compute({ repo.clearPageCache() }) {
						refreshCacheLabel()
						Ui.showInfo(this@SettingsPanel, tr(K.SETTINGS_CLEAR_PAGE_CACHE))
					}
				},
			)
		}
		content.add(storageButtons)
		content.add(cacheLabel)

		// Backup
		content.add(Ui.vgap(8))
		content.add(section(tr(K.BACKUP_TITLE)))
		content.add(
			JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
				background = Theme.background
				add(Ui.accentButton(tr(K.BACKUP_EXPORT)) { BackupDialog.showExport(this@SettingsPanel, repo) })
				add(
					Ui.button(tr(K.BACKUP_IMPORT)) {
						BackupDialog.showImport(this@SettingsPanel, repo) { navigator.refreshAll() }
					},
				)
			},
		)
		content.add(Ui.label(tr(K.BACKUP_WARNING), Theme.fontSmall, Theme.onSurfaceVariant))
		content.add(Ui.label(tr(K.BACKUP_DOWNLOADS_NOTE), Theme.fontSmall, Theme.onSurfaceVariant))

		// Compatibility
		content.add(Ui.vgap(8))
		content.add(section(tr(K.SOURCES_TITLE)))
		content.add(compatibilityLabel)
		content.add(Ui.label(tr(K.SOURCES_UNAVAILABLE), Theme.fontSmall, Theme.onSurfaceVariant))
		content.add(
			JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
				background = Theme.background
				add(Ui.button(tr(K.NAV_HUB)) { navigator.showHub() })
			},
		)

		// About
		content.add(Ui.vgap(8))
		content.add(section(tr(K.ABOUT_TITLE)))
		content.add(Ui.label("${BuildInfo.APP_NAME} ${BuildInfo.APP_VERSION}", Theme.fontSmall, Theme.onSurfaceVariant))
		content.add(Ui.label("Upstream: ${BuildInfo.UPSTREAM_URL} @ ${BuildInfo.UPSTREAM_COMMIT.take(10)}", Theme.fontSmall, Theme.onSurfaceVariant))
		content.add(Ui.label(tr(K.ABOUT_LICENSE), Theme.fontSmall, Theme.onSurfaceVariant))
		content.add(
			JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
				background = Theme.background
				add(Ui.button(tr(K.MENU_HELP_DATA_FOLDER)) { Ui.openFolder(this@SettingsPanel, AppDirs.dataDir) })
				add(Ui.button(tr(K.MENU_HELP_LOG_FOLDER)) { Ui.openFolder(this@SettingsPanel, AppDirs.logsDir) })
			},
		)
	}

	private fun section(title: String): JLabel = Ui.label(title, Theme.fontH2).apply {
		border = BorderFactory.createEmptyBorder(6, 0, 4, 0)
		alignmentX = LEFT_ALIGNMENT
	}

	private fun row(label: String, component: java.awt.Component): JPanel = JPanel(BorderLayout()).apply {
		background = Theme.background
		maximumSize = Dimension(640, 34)
		alignmentX = LEFT_ALIGNMENT
		add(Ui.label(label, Theme.baseFont), BorderLayout.WEST)
		add(
			JPanel(FlowLayout(FlowLayout.RIGHT, 0, 2)).apply {
				background = Theme.background
				add(component)
			},
			BorderLayout.EAST,
		)
	}

	private fun checkbox(label: String, selected: Boolean, onChange: (Boolean) -> Unit): JPanel =
		JPanel(BorderLayout()).apply {
			background = Theme.background
			maximumSize = Dimension(640, 30)
			alignmentX = LEFT_ALIGNMENT
			val checkBox = Ui.checkBox(label, selected, onChange)
			add(checkBox, BorderLayout.WEST)
		}

	private fun pathRow(label: String, path: java.nio.file.Path, openDownloads: Boolean): JPanel =
		JPanel(BorderLayout()).apply {
			background = Theme.background
			maximumSize = Dimension(900, 32)
			alignmentX = LEFT_ALIGNMENT
			add(Ui.label(label, Theme.baseFont), BorderLayout.WEST)
			add(
				JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
					background = Theme.background
					add(Ui.label(path.toString(), Theme.fontSmall, Theme.onSurfaceVariant))
					add(Ui.button(tr(K.ACTION_OPEN_FOLDER)) { Ui.openFolder(this@SettingsPanel, path) })
				},
				BorderLayout.EAST,
			)
		}

	override fun onShown() {
		refreshCacheLabel()
		compatibilityLabel.text = buildString {
			val catalog = repo.sourceCatalog()
			val implemented = catalog.count { it.isAvailable }
			val enabled = catalog.count { it.enabled && it.isAvailable }
			append(tr(K.SETTINGS_SOURCES_SUMMARY).format(implemented))
			append(" · ")
			append(tr(K.HUB_ENABLED_COUNT).format(enabled))
			val unsupported = repo.unsupportedMangaCount()
			if (unsupported > 0) {
				append(" · ")
				append(tr(K.SETTINGS_UNSUPPORTED_TITLES).format(unsupported))
			}
		}
	}

	private fun refreshCacheLabel() {
		Async.compute({ cacheSize() to repo.pageCacheSize() }) { (size, pages) ->
			cacheLabel.text = tr(K.SETTINGS_CACHE_SIZE).format(humanFileSize(size)) +
				" · " + tr(K.SETTINGS_PAGE_CACHE) + ": " + humanFileSize(pages)
		}
	}

	private fun cacheSize(): Long = runCatching {
		if (!Files.isDirectory(AppDirs.cacheDir)) return 0L
		Files.walk(AppDirs.cacheDir).use { stream ->
			stream.filter { Files.isRegularFile(it) }.mapToLong { Files.size(it) }.sum()
		}
	}.getOrDefault(0L)

	private fun clearCache(): Int {
		var count = 0
		runCatching {
			if (!Files.isDirectory(AppDirs.cacheDir)) return 0
			Files.walk(AppDirs.cacheDir).use { stream ->
				stream.filter { Files.isRegularFile(it) }.forEach {
					if (runCatching { Files.deleteIfExists(it) }.getOrDefault(false)) count++
				}
			}
		}
		org.koitharu.kotatsu.desktop.data.local.PageImageCache.clear()
		return count
	}

	private inner class ReaderModeRenderer : javax.swing.DefaultListCellRenderer() {
		override fun getListCellRendererComponent(
			list: javax.swing.JList<*>?,
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
}

/**
 * Interface scale steps. The value is the factor applied to every font of the application, which
 * is how the UI stays readable on a 4K screen or on a Windows display with a scaling factor.
 */
enum class UiScaleChoice(val scale: Float, val label: String) {

	SCALE_100(1.0f, "100 %"),
	SCALE_125(1.25f, "125 %"),
	SCALE_150(1.5f, "150 %"),
	SCALE_175(1.75f, "175 %"),
	SCALE_200(2.0f, "200 %"),
	;

	override fun toString(): String = label

	companion object {

		/** Nearest step for a stored (or imported) scale. */
		fun closestTo(scale: Float): UiScaleChoice =
			entries.minByOrNull { kotlin.math.abs(it.scale - scale) } ?: SCALE_100
	}
}
