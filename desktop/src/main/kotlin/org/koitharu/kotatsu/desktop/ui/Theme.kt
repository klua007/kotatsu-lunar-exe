/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Small colour/typography table. The Android app uses Material 3 (plus a Compose surface for some
 * screens); reproducing that exactly would require Compose Multiplatform — see
 * docs/ARCHITECTURE.md for why this build stays on Swing. The palette below follows the same idea
 * (dark-first, one accent colour, flat surfaces).
 */
package org.koitharu.kotatsu.desktop.ui

import org.koitharu.kotatsu.desktop.data.store.ThemeMode
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.UIManager

object Theme {

	private const val ACCENT = 0xE2455E
	private const val ACCENT_DARK = 0xF2617A

	var mode: ThemeMode = ThemeMode.DARK
		private set

	val isDark: Boolean get() = mode == ThemeMode.DARK

	val background: Color get() = if (isDark) Color(0x12, 0x12, 0x12) else Color(0xFA, 0xFA, 0xFA)
	val surface: Color get() = if (isDark) Color(0x1E, 0x1E, 0x1E) else Color(0xFF, 0xFF, 0xFF)
	val surfaceVariant: Color get() = if (isDark) Color(0x2A, 0x2A, 0x2A) else Color(0xEE, 0xEE, 0xEE)
	val border: Color get() = if (isDark) Color(0x3A, 0x3A, 0x3A) else Color(0xD8, 0xD8, 0xD8)
	val onBackground: Color get() = if (isDark) Color(0xEE, 0xEE, 0xEE) else Color(0x1B, 0x1B, 0x1B)
	val onSurfaceVariant: Color get() = if (isDark) Color(0xB0, 0xB0, 0xB0) else Color(0x55, 0x55, 0x55)
	val accent: Color get() = if (isDark) Color(ACCENT_DARK) else Color(ACCENT)
	val error: Color get() = Color(0xCF6679)
	val readerBlack: Color get() = Color(0x00, 0x00, 0x00)

	val baseFont: Font by lazy {
		val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
		val family = listOf("Segoe UI", "Inter", "Noto Sans", "DejaVu Sans", "SansSerif")
			.firstOrNull { it in available } ?: "SansSerif"
		Font(family, Font.PLAIN, 13)
	}

	val fontH1: Font get() = baseFont.deriveFont(Font.BOLD, 20f)
	val fontH2: Font get() = baseFont.deriveFont(Font.BOLD, 15f)
	val fontSmall: Font get() = baseFont.deriveFont(11f)
	val fontMono: Font get() = Font(Font.MONOSPACED, Font.PLAIN, 12)

	fun set(mode: ThemeMode) {
		this.mode = mode
		applyUiDefaults()
	}

	/**
	 * Applies the theme to the Swing defaults so that built-in components (menus, dialogs, tables,
	 * scroll bars) pick up the palette without per-component styling.
	 */
	fun applyUiDefaults() {
		UIManager.put("Panel.background", background)
		UIManager.put("OptionPane.background", surface)
		UIManager.put("Viewport.background", background)
		UIManager.put("ScrollPane.background", background)
		UIManager.put("Label.foreground", onBackground)
		UIManager.put("Button.background", surfaceVariant)
		UIManager.put("Button.foreground", onBackground)
		UIManager.put("ComboBox.background", surfaceVariant)
		UIManager.put("ComboBox.foreground", onBackground)
		UIManager.put("TextField.background", surfaceVariant)
		UIManager.put("TextField.foreground", onBackground)
		UIManager.put("TextField.caretForeground", onBackground)
		UIManager.put("TextArea.background", surface)
		UIManager.put("TextArea.foreground", onBackground)
		UIManager.put("TextPane.background", surface)
		UIManager.put("TextPane.foreground", onBackground)
		UIManager.put("List.background", background)
		UIManager.put("List.foreground", onBackground)
		UIManager.put("List.selectionBackground", accent)
		UIManager.put("List.selectionForeground", Color.WHITE)
		UIManager.put("Table.background", surface)
		UIManager.put("Table.foreground", onBackground)
		UIManager.put("Table.gridColor", border)
		UIManager.put("Table.selectionBackground", accent)
		UIManager.put("Table.selectionForeground", Color.WHITE)
		UIManager.put("TableHeader.background", surfaceVariant)
		UIManager.put("TableHeader.foreground", onBackground)
		UIManager.put("MenuBar.background", surface)
		UIManager.put("MenuBar.foreground", onBackground)
		UIManager.put("Menu.background", surface)
		UIManager.put("Menu.foreground", onBackground)
		UIManager.put("MenuItem.background", surface)
		UIManager.put("MenuItem.foreground", onBackground)
		UIManager.put("Separator.foreground", border)
		UIManager.put("ToolTip.background", surfaceVariant)
		UIManager.put("ToolTip.foreground", onBackground)
		UIManager.put("ProgressBar.background", surfaceVariant)
		UIManager.put("ProgressBar.foreground", accent)
		UIManager.put("SplitPane.background", background)
		UIManager.put("SplitPaneDivider.draggingColor", accent)
		UIManager.put("ScrollBar.thumb", if (isDark) Color(0x55, 0x55, 0x55) else Color(0xBB, 0xBB, 0xBB))
		UIManager.put("ScrollBar.track", background)
		UIManager.put("ScrollBar.background", background)
	}

	/** Background used by the reader, from the stored preference. */
	fun readerBackground(kind: org.koitharu.kotatsu.desktop.data.store.ReaderBackground): Color = when (kind) {
		org.koitharu.kotatsu.desktop.data.store.ReaderBackground.BLACK -> Color.BLACK
		org.koitharu.kotatsu.desktop.data.store.ReaderBackground.GRAY -> Color(0x33, 0x33, 0x33)
		org.koitharu.kotatsu.desktop.data.store.ReaderBackground.WHITE -> Color.WHITE
	}
}
