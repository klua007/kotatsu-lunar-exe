/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.ui

import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.core.i18n.I18n
import org.koitharu.kotatsu.desktop.core.i18n.K
import org.koitharu.kotatsu.desktop.core.i18n.tr
import java.awt.Component
import java.awt.Container
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.LayoutManager
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/** Runs blocking work off the EDT and returns results on it. */
object Async {

	private val pool = Executors.newFixedThreadPool(3) { runnable ->
		Thread(runnable, "app-worker").apply { isDaemon = true }
	}

	fun run(work: () -> Unit) {
		pool.execute {
			try {
				work()
			} catch (e: Exception) {
				Log.e("Async", "Background task failed", e)
			}
		}
	}

	fun <T> compute(work: () -> T, onError: (Throwable) -> Unit = {}, onDone: (T) -> Unit) {
		pool.execute {
			val result = runCatching(work)
			SwingUtilities.invokeLater {
				result.onSuccess(onDone).onFailure { error ->
					Log.e("Async", "Task failed", error)
					onError(error)
				}
			}
		}
	}

	fun ui(block: () -> Unit) {
		if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
	}

	fun shutdown() {
		pool.shutdownNow()
		runCatching { pool.awaitTermination(1, TimeUnit.SECONDS) }
	}
}

/** A FlowLayout that wraps its children, used for the responsive cover grid. */
class WrapLayout(
	align: Int = LEFT,
	hgap: Int = 8,
	vgap: Int = 8,
) : FlowLayout(align, hgap, vgap) {

	override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, true)

	override fun minimumLayoutSize(target: Container): Dimension {
		val size = layoutSize(target, false)
		size.width -= hgap + 1
		return size
	}

	private fun layoutSize(target: Container, preferred: Boolean): Dimension {
		synchronized(target.treeLock) {
			var targetWidth = target.size.width
			if (targetWidth == 0) targetWidth = Int.MAX_VALUE
			val insets = target.insets
			if (targetWidth == Int.MAX_VALUE) {
				var maxWidth = 0
				for (component in target.components) {
					val size = if (preferred) component.preferredSize else component.minimumSize
					maxWidth += size.width + hgap
				}
				targetWidth = maxWidth
			}
			val horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2)
			val maxWidth = targetWidth - horizontalInsetsAndGap

			val dimension = Dimension(0, 0)
			var rowWidth = 0
			var rowHeight = 0
			for (component in target.components) {
				if (!component.isVisible) continue
				val size = if (preferred) component.preferredSize else component.minimumSize
				if (rowWidth + size.width > maxWidth && rowWidth > 0) {
					addRow(dimension, rowWidth, rowHeight)
					rowWidth = 0
					rowHeight = 0
				}
				if (rowWidth != 0) rowWidth += hgap
				rowWidth += size.width
				rowHeight = rowHeight.coerceAtLeast(size.height)
			}
			addRow(dimension, rowWidth, rowHeight)
			dimension.width += horizontalInsetsAndGap
			dimension.height += insets.top + insets.bottom + vgap * 2
			return dimension
		}
	}

	private fun addRow(dimension: Dimension, rowWidth: Int, rowHeight: Int) {
		dimension.width = dimension.width.coerceAtLeast(rowWidth)
		dimension.height += rowHeight + vgap
	}
}

object Ui {

	fun panel(layout: LayoutManager? = null, background: java.awt.Color = Theme.background): JPanel =
		JPanel(layout).apply { this.background = background }

	fun label(text: String, font: java.awt.Font = Theme.baseFont, color: java.awt.Color = Theme.onBackground): JLabel =
		JLabel(text).apply {
			this.font = font
			foreground = color
		}

	fun title(text: String): JLabel = label(text, Theme.fontH1)

		fun subtitle(text: String): JLabel = label(text, Theme.fontH2)

	fun button(text: String, tooltip: String? = null, action: () -> Unit = {}): JButton =
		JButton(text).apply {
			font = Theme.baseFont
			background = Theme.surfaceVariant
			foreground = Theme.onBackground
			isFocusPainted = false
			if (tooltip != null) toolTipText = tooltip
			addActionListener { action() }
		}

	fun accentButton(text: String, action: () -> Unit = {}): JButton = button(text, action = action).apply {
		background = Theme.accent
		foreground = java.awt.Color.WHITE
	}

	fun searchField(placeholder: String, onSearch: (String) -> Unit): JTextField =
		JTextField().apply {
			font = Theme.baseFont
			background = Theme.surfaceVariant
			foreground = Theme.onBackground
			caretColor = Theme.onBackground
			toolTipText = placeholder
			border = BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(Theme.border),
				BorderFactory.createEmptyBorder(6, 8, 6, 8),
			)
			addActionListener { onSearch(text) }
			document.addDocumentListener(
				object : javax.swing.event.DocumentListener {
					override fun insertUpdate(e: javax.swing.event.DocumentEvent) = onSearch(text)
					override fun removeUpdate(e: javax.swing.event.DocumentEvent) = onSearch(text)
					override fun changedUpdate(e: javax.swing.event.DocumentEvent) = onSearch(text)
				},
			)
		}

	fun hbox(vararg components: Component): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 6)).apply {
		background = Theme.background
		components.forEach(::add)
	}

	fun vgap(height: Int): JComponent = JPanel().apply {
		background = Theme.background
		preferredSize = Dimension(1, height)
	}

	fun registerShortcut(component: JComponent, keyCode: Int, modifiers: Int, action: () -> Unit) {
		val key = "shortcut-$keyCode-$modifiers"
		component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
			.put(KeyStroke.getKeyStroke(keyCode, modifiers), key)
		component.actionMap.put(key, object : AbstractAction() {
			override fun actionPerformed(e: ActionEvent) = action()
		})
	}

	fun showInfo(parent: Component?, message: String, title: String = I18n[K.APP_TITLE]) {
		JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE)
	}

	fun showError(parent: Component?, message: String, title: String = tr(K.ERROR_TITLE)) {
		JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE)
	}

	fun confirm(parent: Component?, message: String, title: String = tr(K.CONFIRM_TITLE)): Boolean =
		JOptionPane.showConfirmDialog(
			parent,
			message,
			title,
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.WARNING_MESSAGE,
		) == JOptionPane.OK_OPTION

	fun openFolder(parent: Component?, path: Path) {
		if (Desktop.isDesktopSupported()) {
			runCatching { Desktop.getDesktop().open(path.toFile()) }
				.onFailure { showError(parent, it.message ?: it.javaClass.simpleName) }
		}
	}

	fun chooseFiles(parent: Component?, startDirectory: Path?): List<Path> {
		val chooser = JFileChooser().apply {
			isMultiSelectionEnabled = true
			fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
			dialogTitle = tr(K.MENU_FILE_OPEN_ARCHIVE)
			fileFilter = FileNameExtensionFilter("CBZ / ZIP (*.cbz, *.zip)", "cbz", "zip", "CBZ", "ZIP")
			startDirectory?.takeIf { java.nio.file.Files.isDirectory(it) }?.let { currentDirectory = it.toFile() }
		}
		val result = chooser.showOpenDialog(parent)
		if (result != JFileChooser.APPROVE_OPTION) return emptyList()
		return chooser.selectedFiles.map { it.toPath() }
	}

	fun chooseDirectory(parent: Component?, startDirectory: Path?): Path? {
		val chooser = JFileChooser().apply {
			fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
			dialogTitle = tr(K.ACTION_CHOOSE_FOLDER)
			startDirectory?.takeIf { java.nio.file.Files.isDirectory(it) }?.let { currentDirectory = it.toFile() }
		}
		return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath()
		else null
	}

	fun chooseSaveFile(parent: Component?, defaultName: String, extension: String): Path? {
		val chooser = JFileChooser().apply {
			dialogTitle = tr(K.BACKUP_EXPORT)
			selectedFile = java.io.File(defaultName)
			fileFilter = FileNameExtensionFilter("*.$extension", extension)
		}
		return if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
			val file = chooser.selectedFile
			if (file.name.lowercase().endsWith(".$extension")) file.toPath()
			else file.toPath().resolveSibling("${file.name}.$extension")
		} else {
			null
		}
	}

	fun key(keyCode: Int, modifiers: Int = 0): KeyStroke = KeyStroke.getKeyStroke(keyCode, modifiers)

	fun keyEnter(): KeyStroke = key(KeyEvent.VK_ENTER)
}
