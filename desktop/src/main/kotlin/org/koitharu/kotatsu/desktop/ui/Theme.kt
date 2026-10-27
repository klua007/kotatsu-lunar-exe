/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Palette, typography and Swing integration of the two themes.
 *
 * The Android app uses Material 3 (plus a Compose surface for some screens); reproducing that
 * exactly would require Compose Multiplatform — see docs/ARCHITECTURE.md for why this build stays
 * on Swing. The palette below follows the same idea (dark-first, one accent colour, flat surfaces)
 * with one hard rule: **every colour that carries text is checked against the surface it is drawn
 * on**, and `ThemeTests` fails the build when a pair drops below WCAG AA (4.5:1 for text, 3:1 for
 * the borders of interactive controls).
 *
 * Two details matter on Windows and are easy to get wrong:
 *
 *  1. `UIManager.put(...)` only survives while the Look and Feel that owns the defaults table is
 *     installed. The theme therefore **installs a Look and Feel and then** writes its colours; the
 *     previous order (defaults first, `setLookAndFeel` afterwards) silently threw every colour
 *     away.
 *  2. The Windows Look and Feel paints buttons, check boxes and combo boxes with the native XP
 *     skin and *ignores* `Button.background`. Our light foregrounds were still honoured, which is
 *     exactly how the app ended up with white text on a light native button. The application now
 *     installs a cross-platform (Metal) Look and Feel driven by [KotatsuMetalTheme], so every
 *     control is painted with the palette below.
 */
package org.koitharu.kotatsu.desktop.ui

import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.data.store.ReaderBackground
import org.koitharu.kotatsu.desktop.data.store.ThemeMode
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import kotlin.math.roundToInt
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource
import javax.swing.plaf.InsetsUIResource
import javax.swing.plaf.metal.MetalLookAndFeel

object Theme {

	var mode: ThemeMode = ThemeMode.DARK
		private set

	/** Font scale (1.0 = 100 %). Driven by the "interface scale" setting. */
	var uiScale: Float = 1f
		private set

	val isDark: Boolean get() = mode == ThemeMode.DARK

	// --- Surfaces ------------------------------------------------------------------------------

	val background: Color get() = if (isDark) color(0x12, 0x12, 0x12) else color(0xFA, 0xFA, 0xFA)

	val surface: Color get() = if (isDark) color(0x1E, 0x1E, 0x1E) else color(0xFF, 0xFF, 0xFF)

	val surfaceVariant: Color get() = if (isDark) color(0x2A, 0x2A, 0x2A) else color(0xEE, 0xEE, 0xEE)

	/** Raised surface: cards, selected rows, hover. */
	val surfaceHigh: Color get() = if (isDark) color(0x33, 0x33, 0x33) else color(0xE2, 0xE2, 0xE2)

	/** Decorative separators and card outlines (no WCAG requirement, kept subtle). */
	val border: Color get() = if (isDark) color(0x3A, 0x3A, 0x3A) else color(0xD8, 0xD8, 0xD8)

	/**
	 * Border of an interactive control (button, text field, check box). WCAG 1.4.11 asks for 3:1
	 * against the adjacent surface, which is why this is much stronger than [border].
	 */
	val outline: Color get() = if (isDark) color(0x75, 0x75, 0x75) else color(0x8C, 0x8C, 0x8C)

	/** Bevel colours used by the Look and Feel for the pressed/enabled state of a control. */
	val controlShadow: Color get() = if (isDark) color(0x4A, 0x4A, 0x4A) else color(0xC7, 0xC7, 0xC7)

	val controlDarkShadow: Color get() = if (isDark) color(0x75, 0x75, 0x75) else color(0x8C, 0x8C, 0x8C)

	val controlHighlight: Color get() = if (isDark) color(0x3A, 0x3A, 0x3A) else color(0xFF, 0xFF, 0xFF)

	// --- Content -------------------------------------------------------------------------------

	val onBackground: Color get() = if (isDark) color(0xEE, 0xEE, 0xEE) else color(0x1B, 0x1B, 0x1B)

	val onSurfaceVariant: Color get() = if (isDark) color(0xB0, 0xB0, 0xB0) else color(0x5A, 0x5A, 0x5A)

	/** Disabled text. Still readable on purpose: it says *what* is unavailable. */
	val onSurfaceDisabled: Color get() = if (isDark) color(0x94, 0x94, 0x94) else color(0x6A, 0x6A, 0x6A)

	/** Accent used as text or as an icon colour, always on [background]/[surface]. */
	val accent: Color get() = if (isDark) color(0xF2, 0x61, 0x7A) else color(0xC2, 0x18, 0x3B)

	/**
	 * Accent used as a *filled* background (primary button, selection). It is darker than [accent]
	 * so that [onAccent] keeps a AA contrast; the bright [accent] on top of it would only reach
	 * ~3:1, which is the "almost unreadable button" the 0.2.0 screenshots showed.
	 */
	val accentSolid: Color get() = if (isDark) color(0xC1, 0x40, 0x5B) else color(0xC2, 0x18, 0x3B)

	val onAccent: Color get() = Color.WHITE

	val error: Color get() = if (isDark) color(0xFF, 0x8A, 0x80) else color(0xB3, 0x26, 0x1E)

	val success: Color get() = if (isDark) color(0x7B, 0xD8, 0x8F) else color(0x14, 0x6C, 0x43)

	val focus: Color get() = accent

	val selectionBackground: Color get() = accentSolid

	val selectionForeground: Color get() = onAccent

	val tooltipBackground: Color get() = surfaceVariant

	val tooltipForeground: Color get() = onBackground

	val readerBlack: Color get() = Color.BLACK

	// --- Typography ----------------------------------------------------------------------------

	val fontFamily: String by lazy {
		val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
		listOf("Segoe UI", "Inter", "Noto Sans", "DejaVu Sans", "SansSerif")
			.firstOrNull { it in available } ?: "SansSerif"
	}

	val baseFont: Font get() = Font(fontFamily, Font.PLAIN, size(BASE_FONT_SIZE).roundToInt())
	

	val fontH1: Font get() = baseFont.deriveFont(Font.BOLD, size(20f))
	val fontH2: Font get() = baseFont.deriveFont(Font.BOLD, size(15f))
	val fontSmall: Font get() = baseFont.deriveFont(Font.PLAIN, size(11f))
	val fontMono: Font get() = Font(Font.MONOSPACED, Font.PLAIN, size(12f).roundToInt())

	// --- Contrast ------------------------------------------------------------------------------

	/** WCAG 2.1 relative luminance. */
	fun luminance(color: Color): Double {
		fun channel(value: Int): Double {
			val c = value / 255.0
			return if (c <= 0.04045) c / 12.92 else java.lang.Math.pow((c + 0.055) / 1.055, 2.4)
		}
		return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
	}

	/** WCAG 2.1 contrast ratio, from 1:1 to 21:1. */
	fun contrastRatio(first: Color, second: Color): Double {
		val a = luminance(first)
		val b = luminance(second)
		val lighter = maxOf(a, b)
		val darker = minOf(a, b)
		return (lighter + 0.05) / (darker + 0.05)
	}

	/** True when [foreground] on [background] is legible (4.5:1 by default, 3:1 for large text). */
	fun isLegible(foreground: Color, background: Color, minimum: Double = AA_TEXT): Boolean =
		contrastRatio(foreground, background) >= minimum

	/**
	 * Foreground that is guaranteed legible on [background]: the theme's normal text colour when it
	 * already passes, otherwise the best of the palette's candidates. Used by every themed control
	 * so a component can never paint white on white, whatever the Look and Feel does.
	 */
	fun readableOn(background: Color, minimum: Double = AA_TEXT): Color {
		if (isLegible(onBackground, background, minimum)) return onBackground
		return listOf(onBackground, onSurfaceVariant, onAccent, Color.WHITE, Color.BLACK)
			.maxByOrNull { contrastRatio(it, background) } ?: onBackground
	}

	// --- Applying ------------------------------------------------------------------------------

	/**
	 * Switches the theme. Installing the Look and Feel is part of it: `UIManager.put` values are
	 * owned by the installed Look and Feel, so they have to be written *after* it is set.
	 */
	fun set(mode: ThemeMode, uiScale: Float = this.uiScale) {
		val changed = this.mode != mode || this.uiScale != uiScale
		this.mode = mode
		this.uiScale = uiScale
		if (changed || !installed) install()
	}

	/** (Re)installs the Look and Feel and republishes the palette. */
	fun install() {
		installed = true
		val override = System.getProperty(LAF_PROPERTY)
		when {
			override.equals("system", ignoreCase = true) -> {
				Log.w(TAG, "Look and feel forced to 'system' ($LAF_PROPERTY=system): control colours " +
					"may not follow the ${mode.name.lowercase()} theme.")
				runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
					.onFailure { error -> Log.w(TAG, "System look and feel unavailable", error) }
			}

			override.equals("nimbus", ignoreCase = true) ->
				runCatching { UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel") }
					.onFailure { error -> Log.w(TAG, "Nimbus unavailable, using the themed one", error); installMetal() }

			else -> installMetal()
		}
		applyUiDefaults()
	}

	private fun installMetal() {
		runCatching {
			MetalLookAndFeel.setCurrentTheme(KotatsuMetalTheme())
			UIManager.setLookAndFeel(MetalLookAndFeel())
		}.onFailure { error ->
			Log.w(TAG, "Themed look and feel unavailable", error)
			runCatching { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()) }
		}
	}

	/**
	 * Publishes the palette into the Swing defaults so built-in components (menus, dialogs, tables,
	 * scroll bars, tooltips, focus rings) pick it up without per-component styling.
	 */
	fun applyUiDefaults() {
		UIManager.put("Panel.background", ui(background))
		UIManager.put("Panel.foreground", ui(onBackground))
		UIManager.put("RootPane.background", ui(background))
		UIManager.put("Desktop.background", ui(background))
		UIManager.put("Viewport.background", ui(background))
		UIManager.put("Viewport.foreground", ui(onBackground))
		UIManager.put("ScrollPane.background", ui(background))
		UIManager.put("ScrollPane.foreground", ui(onBackground))

		UIManager.put("Label.background", ui(background))
		UIManager.put("Label.foreground", ui(onBackground))
		UIManager.put("Label.disabledForeground", ui(onSurfaceDisabled))
		UIManager.put("Label.disabledText", ui(onSurfaceDisabled))
		UIManager.put("Label.font", baseFont)

		UIManager.put("Button.background", ui(surfaceVariant))
		UIManager.put("Button.foreground", ui(onBackground))
		UIManager.put("Button.disabledText", ui(onSurfaceDisabled))
		UIManager.put("Button.disabledForeground", ui(onSurfaceDisabled))
		UIManager.put("Button.select", ui(selectionBackground))
		UIManager.put("Button.focus", ui(focus))
		UIManager.put("Button.margin", InsetsUIResource(5, 12, 5, 12))
		UIManager.put("Button.font", baseFont)
		// Filled primary button: a dedicated pair so `updateComponentTreeUI` after a theme change
		// repaints it from the new palette instead of keeping a stale colour.
		UIManager.put(ACCENT_BUTTON_BACKGROUND, ui(accentSolid))
		UIManager.put(ACCENT_BUTTON_FOREGROUND, ui(readableOn(accentSolid)))

		UIManager.put("ToggleButton.background", ui(surfaceVariant))
		UIManager.put("ToggleButton.foreground", ui(onBackground))
		UIManager.put("ToggleButton.disabledText", ui(onSurfaceDisabled))
		UIManager.put("ToggleButton.select", ui(selectionBackground))
		UIManager.put("ToggleButton.focus", ui(focus))

		UIManager.put("CheckBox.background", ui(background))
		UIManager.put("CheckBox.foreground", ui(onBackground))
		UIManager.put("CheckBox.disabledText", ui(onSurfaceDisabled))
		UIManager.put("CheckBox.focus", ui(focus))
		UIManager.put("CheckBox.select", ui(selectionBackground))
		UIManager.put("CheckBox.font", baseFont)

		UIManager.put("RadioButton.background", ui(background))
		UIManager.put("RadioButton.foreground", ui(onBackground))
		UIManager.put("RadioButton.disabledText", ui(onSurfaceDisabled))
		UIManager.put("RadioButton.focus", ui(focus))

		UIManager.put("ComboBox.background", ui(surfaceVariant))
		UIManager.put("ComboBox.foreground", ui(onBackground))
		UIManager.put("ComboBox.buttonBackground", ui(surfaceVariant))
		UIManager.put("ComboBox.buttonForeground", ui(onBackground))
		UIManager.put("ComboBox.buttonShadow", ui(outline))
		UIManager.put("ComboBox.buttonDarkShadow", ui(outline))
		UIManager.put("ComboBox.buttonHighlight", ui(outline))
		UIManager.put("ComboBox.disabledForeground", ui(onSurfaceDisabled))
		UIManager.put("ComboBox.disabledBackground", ui(surfaceVariant))
		UIManager.put("ComboBox.selectionBackground", ui(selectionBackground))
		UIManager.put("ComboBox.selectionForeground", ui(selectionForeground))
		UIManager.put("ComboBox.font", baseFont)

		UIManager.put("TextField.background", ui(surfaceVariant))
		UIManager.put("TextField.foreground", ui(onBackground))
		UIManager.put("TextField.caretForeground", ui(onBackground))
		UIManager.put("TextField.inactiveForeground", ui(onSurfaceDisabled))
		UIManager.put("TextField.disabledBackground", ui(surfaceVariant))
		UIManager.put("TextField.selectionBackground", ui(selectionBackground))
		UIManager.put("TextField.selectionForeground", ui(selectionForeground))
		UIManager.put("TextField.font", baseFont)

		UIManager.put("PasswordField.background", ui(surfaceVariant))
		UIManager.put("PasswordField.foreground", ui(onBackground))
		UIManager.put("PasswordField.caretForeground", ui(onBackground))
		UIManager.put("PasswordField.inactiveForeground", ui(onSurfaceDisabled))
		UIManager.put("PasswordField.selectionBackground", ui(selectionBackground))
		UIManager.put("PasswordField.selectionForeground", ui(selectionForeground))

		UIManager.put("FormattedTextField.background", ui(surfaceVariant))
		UIManager.put("FormattedTextField.foreground", ui(onBackground))
		UIManager.put("FormattedTextField.caretForeground", ui(onBackground))
		UIManager.put("FormattedTextField.inactiveForeground", ui(onSurfaceDisabled))

		UIManager.put("TextArea.background", ui(surfaceVariant))
		UIManager.put("TextArea.foreground", ui(onBackground))
		UIManager.put("TextArea.caretForeground", ui(onBackground))
		UIManager.put("TextArea.inactiveForeground", ui(onSurfaceDisabled))
		UIManager.put("TextArea.selectionBackground", ui(selectionBackground))
		UIManager.put("TextArea.selectionForeground", ui(selectionForeground))
		UIManager.put("TextArea.font", baseFont)

		UIManager.put("TextPane.background", ui(surface))
		UIManager.put("TextPane.foreground", ui(onBackground))
		UIManager.put("TextPane.inactiveForeground", ui(onSurfaceDisabled))
		UIManager.put("TextPane.selectionBackground", ui(selectionBackground))
		UIManager.put("TextPane.selectionForeground", ui(selectionForeground))

		UIManager.put("EditorPane.background", ui(surface))
		UIManager.put("EditorPane.foreground", ui(onBackground))
		UIManager.put("EditorPane.inactiveForeground", ui(onSurfaceDisabled))
		UIManager.put("EditorPane.selectionBackground", ui(selectionBackground))
		UIManager.put("EditorPane.selectionForeground", ui(selectionForeground))

		UIManager.put("List.background", ui(background))
		UIManager.put("List.foreground", ui(onBackground))
		UIManager.put("List.selectionBackground", ui(selectionBackground))
		UIManager.put("List.selectionForeground", ui(selectionForeground))
		UIManager.put("List.focusCellHighlightBorder", null)
		UIManager.put("List.font", baseFont)

		UIManager.put("Table.background", ui(surface))
		UIManager.put("Table.foreground", ui(onBackground))
		UIManager.put("Table.gridColor", ui(border))
		UIManager.put("Table.selectionBackground", ui(selectionBackground))
		UIManager.put("Table.selectionForeground", ui(selectionForeground))
		UIManager.put("Table.font", baseFont)
		UIManager.put("TableHeader.background", ui(surfaceVariant))
		UIManager.put("TableHeader.foreground", ui(onBackground))
		UIManager.put("TableHeader.font", fontH2)

		UIManager.put("Tree.background", ui(background))
		UIManager.put("Tree.foreground", ui(onBackground))
		UIManager.put("Tree.textBackground", ui(background))
		UIManager.put("Tree.textForeground", ui(onBackground))
		UIManager.put("Tree.selectionBackground", ui(selectionBackground))
		UIManager.put("Tree.selectionForeground", ui(selectionForeground))
		UIManager.put("Tree.hash", ui(outline))

		UIManager.put("MenuBar.background", ui(surface))
		UIManager.put("MenuBar.foreground", ui(onBackground))
		UIManager.put("MenuBar.font", baseFont)
		UIManager.put("Menu.background", ui(surface))
		UIManager.put("Menu.foreground", ui(onBackground))
		UIManager.put("Menu.disabledForeground", ui(onSurfaceDisabled))
		UIManager.put("Menu.selectionBackground", ui(selectionBackground))
		UIManager.put("Menu.selectionForeground", ui(selectionForeground))
		UIManager.put("Menu.acceleratorForeground", ui(onSurfaceVariant))
		UIManager.put("Menu.acceleratorSelectionForeground", ui(selectionForeground))
		UIManager.put("PopupMenu.background", ui(surface))
		UIManager.put("PopupMenu.foreground", ui(onBackground))
		UIManager.put("MenuItem.background", ui(surface))
		UIManager.put("MenuItem.foreground", ui(onBackground))
		UIManager.put("MenuItem.disabledForeground", ui(onSurfaceDisabled))
		UIManager.put("MenuItem.selectionBackground", ui(selectionBackground))
		UIManager.put("MenuItem.selectionForeground", ui(selectionForeground))
		UIManager.put("MenuItem.acceleratorForeground", ui(onSurfaceVariant))
		UIManager.put("MenuItem.acceleratorSelectionForeground", ui(selectionForeground))
		UIManager.put("MenuItem.font", baseFont)
		UIManager.put("CheckBoxMenuItem.background", ui(surface))
		UIManager.put("CheckBoxMenuItem.foreground", ui(onBackground))
		UIManager.put("CheckBoxMenuItem.disabledForeground", ui(onSurfaceDisabled))
		UIManager.put("CheckBoxMenuItem.selectionBackground", ui(selectionBackground))
		UIManager.put("CheckBoxMenuItem.selectionForeground", ui(selectionForeground))
		UIManager.put("CheckBoxMenuItem.acceleratorForeground", ui(onSurfaceVariant))
		UIManager.put("RadioButtonMenuItem.background", ui(surface))
		UIManager.put("RadioButtonMenuItem.foreground", ui(onBackground))
		UIManager.put("RadioButtonMenuItem.selectionBackground", ui(selectionBackground))
		UIManager.put("RadioButtonMenuItem.selectionForeground", ui(selectionForeground))

		UIManager.put("Separator.background", ui(surface))
		UIManager.put("Separator.foreground", ui(border))

		UIManager.put("ToolTip.background", ui(tooltipBackground))
		UIManager.put("ToolTip.foreground", ui(tooltipForeground))
		UIManager.put("ToolTip.backgroundInactive", ui(tooltipBackground))
		UIManager.put("ToolTip.foregroundInactive", ui(onSurfaceDisabled))
		UIManager.put("ToolTip.font", fontSmall)

		UIManager.put("ProgressBar.background", ui(surfaceVariant))
		// The filled part of the bar carries no text, but 3:1 is still required (WCAG 1.4.11); the
		// darker accentSolid only reaches 2.8:1 on surfaceVariant, the accent does 4.6:1.
		UIManager.put("ProgressBar.foreground", ui(accent))
		UIManager.put("ProgressBar.selectionBackground", ui(accentSolid))
		UIManager.put("ProgressBar.selectionForeground", ui(onAccent))

		UIManager.put("SplitPane.background", ui(background))
		UIManager.put("SplitPane.foreground", ui(onBackground))
		UIManager.put("SplitPaneDivider.draggingColor", ui(focus))

		UIManager.put("ScrollBar.background", ui(background))
		UIManager.put("ScrollBar.foreground", ui(onBackground))
		UIManager.put("ScrollBar.track", ui(background))
		UIManager.put("ScrollBar.trackHighlight", ui(surfaceHigh))
		UIManager.put("ScrollBar.thumb", ui(if (isDark) color(0x5A, 0x5A, 0x5A) else color(0xB0, 0xB0, 0xB0)))
		UIManager.put("ScrollBar.thumbHighlight", ui(outline))
		UIManager.put("ScrollBar.thumbDarkShadow", ui(controlDarkShadow))
		UIManager.put("ScrollBar.thumbShadow", ui(controlShadow))

		UIManager.put("Slider.background", ui(background))
		UIManager.put("Slider.foreground", ui(onBackground))
		UIManager.put("Slider.track", ui(surfaceVariant))
		UIManager.put("Slider.focus", ui(focus))

		UIManager.put("Spinner.background", ui(surfaceVariant))
		UIManager.put("Spinner.foreground", ui(onBackground))
		UIManager.put("Spinner.font", baseFont)

		UIManager.put("TabbedPane.background", ui(background))
		UIManager.put("TabbedPane.foreground", ui(onBackground))
		UIManager.put("TabbedPane.contentAreaColor", ui(background))
		UIManager.put("TabbedPane.selected", ui(surface))
		UIManager.put("TabbedPane.selectHighlight", ui(outline))
		UIManager.put("TabbedPane.focus", ui(focus))
		UIManager.put("TabbedPane.darkShadow", ui(controlDarkShadow))

		UIManager.put("ToolBar.background", ui(surface))
		UIManager.put("ToolBar.foreground", ui(onBackground))
		UIManager.put("ToolBar.dockingBackground", ui(surface))
		UIManager.put("ToolBar.floatingBackground", ui(surface))

		UIManager.put("OptionPane.background", ui(surface))
		UIManager.put("OptionPane.foreground", ui(onBackground))
		UIManager.put("OptionPane.messageForeground", ui(onBackground))
		UIManager.put("OptionPane.messageFont", baseFont)
		UIManager.put("OptionPane.buttonFont", baseFont)
		UIManager.put("OptionPane.errorDialog.border.background", ui(surface))

		UIManager.put("FileChooser.background", ui(background))
		UIManager.put("FileChooser.foreground", ui(onBackground))
		UIManager.put("FileChooser.listFont", baseFont)

		UIManager.put("TitledBorder.titleColor", ui(onSurfaceVariant))

		UIManager.put("InternalFrame.background", ui(background))
		UIManager.put("InternalFrame.foreground", ui(onBackground))
		UIManager.put("InternalFrame.titleFont", fontH2)
		UIManager.put("InternalFrame.activeTitleBackground", ui(surface))
		UIManager.put("InternalFrame.activeTitleForeground", ui(onBackground))
		UIManager.put("InternalFrame.inactiveTitleBackground", ui(surface))
		UIManager.put("InternalFrame.inactiveTitleForeground", ui(onSurfaceVariant))

		// The Look and Feel reads the focus colour from its theme; publish it for the ones that
		// look it up here (and for our own focus ring, see Ui.focusBorder).
		UIManager.put("Focus.color", ui(focus))
		UIManager.put("focus", ui(focus))
	}

	/** Background used by the reader, from the stored preference. */
	fun readerBackground(kind: ReaderBackground): Color = when (kind) {
		ReaderBackground.BLACK -> Color.BLACK
		ReaderBackground.GRAY -> Color(0x33, 0x33, 0x33)
		ReaderBackground.WHITE -> Color.WHITE
	}

	// --- Internals -----------------------------------------------------------------------------

	private var installed = false

	private fun color(red: Int, green: Int, blue: Int): Color = Color(red, green, blue)

	private fun size(value: Float): Float = (value * uiScale).coerceIn(9f, 40f)

	private fun ui(color: Color): ColorUIResource = ColorUIResource(color)

	private const val BASE_FONT_SIZE = 13f
	private const val TAG = "Theme"

	/** Escape hatch for debugging: `-Dkotatsu.laf=system|nimbus`. */
	const val LAF_PROPERTY = "kotatsu.laf"

	const val AA_TEXT = 4.5
	const val AA_LARGE_TEXT = 3.0
	const val AA_UI_COMPONENT = 3.0
}
