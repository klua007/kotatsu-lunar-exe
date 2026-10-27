/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.ui

import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.core.i18n.I18n
import org.koitharu.kotatsu.desktop.core.i18n.K
import org.koitharu.kotatsu.desktop.core.i18n.tr
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.Border
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

/**
 * Border that draws a resting outline and a high-contrast keyboard focus ring.
 *
 * `isFocusPainted` is turned off on every themed control because the Look and Feel paints its own
 * ring in a colour we do not control; this border replaces it with one that is always at least
 * 3:1 against the surface around the control (verified by `ThemeTests`).
 */
/** Swing defaults of the filled primary button, published by `Theme.applyUiDefaults()`. */
const val ACCENT_BUTTON_BACKGROUND = "Kotatsu.accentButton.background"
const val ACCENT_BUTTON_FOREGROUND = "Kotatsu.accentButton.foreground"

class FocusBorder(
	private val resting: () -> Color? = { null },
	private val focused: () -> Color = { Theme.focus },
	private val padding: Insets = Insets(4, 12, 4, 12),
	private val thickness: Int = 1,
	private val focusThickness: Int = 2,
	private val arc: Int = 8,
) : Border {

	private val gap: Int = maxOf(thickness, focusThickness)

	override fun paintBorder(
		component: Component,
		graphics: Graphics,
		x: Int,
		y: Int,
		width: Int,
		height: Int,
	) {
		val hasFocus = component.hasFocus() || component is JComponent &&
			component.getClientProperty(FORCE_FOCUS_PROPERTY) == true
		val color = if (hasFocus) focused() else resting() ?: return
		val size = if (hasFocus) focusThickness else thickness
		val g = graphics.create()
		if (g is Graphics2D) {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
			g.stroke = BasicStroke(size.toFloat())
		}
		g.color = color
		val inset = size / 2
		if (g is Graphics2D && arc > 0) {
			g.drawRoundRect(x + inset, y + inset, width - size, height - size, arc, arc)
		} else {
			g.drawRect(x + inset, y + inset, width - size, height - size)
		}
		g.dispose()
	}

	override fun getBorderInsets(component: Component?): Insets = Insets(
		padding.top + gap,
		padding.left + gap,
		padding.bottom + gap,
		padding.right + gap,
	)

	override fun isBorderOpaque(): Boolean = false

	companion object {

		/** Set to `true` to paint the focus ring of a control that does not own the focus yet. */
		const val FORCE_FOCUS_PROPERTY = "kotatsu.focusRing"
	}
}

object Ui {

	/**
	 * Repaints a control when it gains or loses the keyboard focus, so [FocusBorder] is redrawn.
	 */
	fun focusRing(component: JComponent): JComponent = component.apply {
		addFocusListener(object : FocusAdapter() {
			override fun focusGained(e: FocusEvent) = component.repaint()
			override fun focusLost(e: FocusEvent) = component.repaint()
		})
	}

	/** Outline + focus ring used by every themed control. */
	fun controlBorder(
		resting: () -> Color? = { Theme.outline },
		padding: Insets = Insets(4, 10, 4, 10),
	): Border = FocusBorder(resting = resting, focused = { Theme.focus }, padding = padding)

	/**
	 * Colour of a control, taken from the Swing defaults when the theme is installed so that a
	 * live theme change repaints it, and from the palette otherwise (headless tests, first paint
	 * before `Theme.install()`).
	 */
	fun themedColor(key: String, fallback: Color): Color = UIManager.getColor(key) ?: fallback

	/**
	 * Lays out a component and its whole subtree.
	 *
	 * List cell renderers are painted by `CellRendererPane` without being validated, and
	 * `Container.doLayout()` is not recursive, so a renderer built from nested panels keeps zero
	 * bounds for its children and paints an empty (or half empty) row. Call this from the renderer.
	 */
	fun layoutTree(component: Component) {
		if (component is Container) {
			component.doLayout()
			component.components.forEach(::layoutTree)
		}
	}

	fun panel(layout: LayoutManager? = null, background: java.awt.Color = Theme.background): JPanel =
		JPanel(layout).apply { this.background = background }

	fun label(text: String, font: java.awt.Font = Theme.baseFont, color: java.awt.Color = Theme.onBackground): JLabel =
		JLabel(text).apply {
			this.font = font
			foreground = color
		}

	fun title(text: String): JLabel = label(text, Theme.fontH1)

		fun subtitle(text: String): JLabel = label(text, Theme.fontH2)

	/**
	 * A themed button.
	 *
	 * The foreground is derived from the background with [Theme.readableOn], and the control gets
	 * an explicit border: the surface of a button is only ~1.3:1 against the panel behind it, so
	 * without the 3:1 outline the control would be invisible even with perfectly readable text.
	 */
	fun button(text: String, tooltip: String? = null, action: () -> Unit = {}): JButton =
		JButton(text).apply {
			font = Theme.baseFont
			background = themedColor("Button.background", Theme.surfaceVariant)
			foreground = themedColor("Button.foreground", Theme.readableOn(background))
			isOpaque = true
			isContentAreaFilled = true
			// The Look and Feel ring is not themeable; FocusBorder paints ours instead.
			isFocusPainted = false
			border = controlBorder()
			focusRing(this)
			if (tooltip != null) toolTipText = tooltip
			addActionListener { action() }
		}

	/**
	 * Filled primary button. Uses [Theme.accentSolid] and not the brighter [Theme.accent]: white on
	 * the bright accent is only ~3:1, white on `accentSolid` is > 5:1.
	 */
	fun accentButton(text: String, action: () -> Unit = {}): JButton = button(text, action = action).apply {
		background = themedColor(ACCENT_BUTTON_BACKGROUND, Theme.accentSolid)
		foreground = themedColor(
			ACCENT_BUTTON_FOREGROUND,
			Theme.readableOn(themedColor(ACCENT_BUTTON_BACKGROUND, Theme.accentSolid)),
		)
		border = controlBorder(resting = { null })
	}

	fun textField(placeholder: String = "", onEnter: ((String) -> Unit)? = null): JTextField =
		JTextField().apply {
			font = Theme.baseFont
			background = themedColor("TextField.background", Theme.surfaceVariant)
			foreground = themedColor("TextField.foreground", Theme.readableOn(background))
			caretColor = Theme.onBackground
			disabledTextColor = themedColor("TextField.inactiveForeground", Theme.onSurfaceDisabled)
			selectionColor = Theme.selectionBackground
			selectedTextColor = Theme.selectionForeground
			toolTipText = placeholder
			border = controlBorder(padding = Insets(4, 6, 4, 6))
			focusRing(this)
			if (onEnter != null) addActionListener { onEnter(text) }
		}

	fun searchField(placeholder: String, onSearch: (String) -> Unit): JTextField =
		textField(placeholder) { onSearch(it) }.apply {
			document.addDocumentListener(
				object : javax.swing.event.DocumentListener {
					override fun insertUpdate(e: javax.swing.event.DocumentEvent) = onSearch(text)
					override fun removeUpdate(e: javax.swing.event.DocumentEvent) = onSearch(text)
					override fun changedUpdate(e: javax.swing.event.DocumentEvent) = onSearch(text)
				},
			)
		}

	fun checkBox(text: String, selected: Boolean = false, onChange: (Boolean) -> Unit = {}): JCheckBox =
		JCheckBox(text, selected).apply {
			font = Theme.baseFont
			background = themedColor("CheckBox.background", Theme.background)
			foreground = themedColor("CheckBox.foreground", Theme.onBackground)
			isOpaque = false
			isFocusPainted = false
			border = controlBorder(resting = { null }, padding = Insets(2, 2, 2, 2))
			focusRing(this)
			addActionListener { onChange(isSelected) }
		}

	fun <E> comboBox(model: javax.swing.ComboBoxModel<E>): JComboBox<E> = JComboBox(model).apply {
		themeComboBox(this)
	}

	fun <E> comboBox(items: List<E>): JComboBox<E> {
		val model = DefaultComboBoxModel<E>()
		items.forEach { model.addElement(it) }
		return comboBox(model)
	}

	fun spinner(model: javax.swing.SpinnerModel): JSpinner = JSpinner(model).apply {
		font = Theme.baseFont
		background = themedColor("Spinner.background", Theme.surfaceVariant)
		foreground = themedColor("Spinner.foreground", Theme.readableOn(background))
		border = controlBorder(padding = Insets(2, 4, 2, 4))
		focusRing(this)
		(editor as? JSpinner.DefaultEditor)?.textField?.apply {
			background = themedColor("TextField.background", Theme.surfaceVariant)
			foreground = themedColor("TextField.foreground", Theme.readableOn(background))
			caretColor = Theme.onBackground
			disabledTextColor = Theme.onSurfaceDisabled
			selectionColor = Theme.selectionBackground
			selectedTextColor = Theme.selectionForeground
			border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
		}
		editor?.apply {
			background = themedColor("Spinner.background", Theme.surfaceVariant)
			foreground = themedColor("Spinner.foreground", Theme.onBackground)
		}
	}

	fun textArea(text: String = ""): JTextArea = JTextArea(text).apply {
		font = Theme.baseFont
		background = themedColor("TextArea.background", Theme.surfaceVariant)
		foreground = themedColor("TextArea.foreground", Theme.readableOn(background))
		caretColor = Theme.onBackground
		disabledTextColor = Theme.onSurfaceDisabled
		selectionColor = Theme.selectionBackground
		selectedTextColor = Theme.selectionForeground
		border = controlBorder(padding = Insets(4, 4, 4, 4))
		focusRing(this)
	}

	private fun <E> themeComboBox(box: JComboBox<E>) {
		box.apply {
			font = Theme.baseFont
			background = themedColor("ComboBox.background", Theme.surfaceVariant)
			foreground = themedColor("ComboBox.foreground", Theme.readableOn(background))
			isOpaque = true
			border = controlBorder(padding = Insets(3, 6, 3, 4))
			focusRing(this)
			setRenderer(object : DefaultListCellRenderer() {
				override fun getListCellRendererComponent(
					list: JList<*>?,
					value: Any?,
					index: Int,
					isSelected: Boolean,
					cellHasFocus: Boolean,
				): Component = super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus,
				).apply {
					font = Theme.baseFont
					background = if (isSelected || cellHasFocus) Theme.selectionBackground else Theme.surfaceVariant
					foreground = if (isSelected || cellHasFocus) Theme.selectionForeground else Theme.onBackground
					border = BorderFactory.createEmptyBorder(3, 6, 3, 6)
				}
			})
			(editor.editorComponent as? JTextField)?.apply {
				background = themedColor("ComboBox.background", Theme.surfaceVariant)
				foreground = themedColor("ComboBox.foreground", Theme.readableOn(background))
				caretColor = Theme.onBackground
				disabledTextColor = Theme.onSurfaceDisabled
				selectionColor = Theme.selectionBackground
				selectedTextColor = Theme.selectionForeground
				border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
			}
		}
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
