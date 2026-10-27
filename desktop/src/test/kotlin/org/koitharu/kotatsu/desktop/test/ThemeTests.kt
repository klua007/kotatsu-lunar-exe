/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Accessibility tests of the two themes. They run headless: contrast is arithmetic, and the Look
 * and Feel can be installed and painted into a BufferedImage without a display, which is exactly
 * what the Windows CI runner does.
 *
 * What they protect:
 *  * every colour pair that carries text stays at or above WCAG AA (4.5:1), in both themes;
 *  * the borders of interactive controls stay at or above 3:1 against the surface behind them;
 *  * the controls the application builds are painted with the theme and not with a native (light)
 *    Look and Feel — the 0.2.0 screenshots showed dark panels with light native buttons and white
 *    text on them, which is precisely what "the dominant colour of a button is the theme surface"
 *    catches;
 *  * a keyboard focus ring exists and is visible.
 */
package org.koitharu.kotatsu.desktop.test

import org.koitharu.kotatsu.desktop.data.store.ThemeMode
import org.koitharu.kotatsu.desktop.test.TestRunner.assertTrue
import org.koitharu.kotatsu.desktop.ui.FocusBorder
import org.koitharu.kotatsu.desktop.ui.Theme
import org.koitharu.kotatsu.desktop.ui.Ui
import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.SpinnerNumberModel
import javax.swing.UIManager

object ThemeTests {

	private const val AA = 4.5
	private const val AA_UI = 3.0

	fun run() {
		listOf(ThemeMode.DARK, ThemeMode.LIGHT).forEach { mode ->
			Theme.set(mode)
			paletteTests(mode)
			defaultsTests(mode)
			controlTests(mode)
			focusRingTests(mode)
		}
		Theme.set(ThemeMode.DARK)
	}

	// --- 1. The palette itself -----------------------------------------------------------------

	private fun paletteTests(mode: ThemeMode) = TestRunner.suite("Theme · ${mode.name.lowercase()} palette") {
		TestRunner.test("every colour that carries text reaches WCAG AA") {
		fun text(name: String, foreground: Color, background: Color) {
			val ratio = Theme.contrastRatio(foreground, background)
			assertTrue(
				"$name: ${ratio.format(2)}:1 (needs $AA:1) — ${foreground.hex()} on ${background.hex()}",
				ratio >= AA,
			)
		}

		fun boundary(name: String, foreground: Color, background: Color) {
			val ratio = Theme.contrastRatio(foreground, background)
			assertTrue(
				"$name: ${ratio.format(2)}:1 (needs $AA_UI:1) — ${foreground.hex()} on ${background.hex()}",
				ratio >= AA_UI,
			)
		}

		text("body text on the window background", Theme.onBackground, Theme.background)
		text("body text on a card", Theme.onBackground, Theme.surface)
		text("body text on a raised surface", Theme.onBackground, Theme.surfaceVariant)
		text("body text on a selected row", Theme.onBackground, Theme.surfaceHigh)
		text("secondary text on the window background", Theme.onSurfaceVariant, Theme.background)
		text("secondary text on a card", Theme.onSurfaceVariant, Theme.surface)
		text("secondary text on a raised surface", Theme.onSurfaceVariant, Theme.surfaceVariant)
		text("accent text on the window background", Theme.accent, Theme.background)
		text("accent text on a card", Theme.accent, Theme.surface)
		text("accent text on a raised surface", Theme.accent, Theme.surfaceVariant)
		text("error text on a card", Theme.error, Theme.surface)
		text("error text on a raised surface", Theme.error, Theme.surfaceVariant)
		text("success text on a card", Theme.success, Theme.surface)
		text("text on a filled accent button", Theme.onAccent, Theme.accentSolid)
		text("selected text on the selection colour", Theme.selectionForeground, Theme.selectionBackground)
		text("tooltip text", Theme.tooltipForeground, Theme.tooltipBackground)
		// Disabled text is exempt from WCAG, but it must still say *what* is unavailable.
		boundary("disabled text on a card", Theme.onSurfaceDisabled, Theme.surface)
		boundary("disabled text on a raised surface", Theme.onSurfaceDisabled, Theme.surfaceVariant)
		boundary("focus ring against the window background", Theme.focus, Theme.background)
		boundary("focus ring against a card", Theme.focus, Theme.surface)
		boundary("control outline against a card", Theme.outline, Theme.surface)
		boundary("control outline against the window background", Theme.outline, Theme.background)

		}
		TestRunner.test("readableOn() and the contrast maths agree with WCAG") {
			TestRunner.assertTrue(
				"readableOn() never returns a colour below AA",
				listOf(
					Theme.background, Theme.surface, Theme.surfaceVariant,
					Theme.accentSolid, Color.WHITE, Color.BLACK,
				).all { Theme.contrastRatio(Theme.readableOn(it), it) >= AA },
			)
			TestRunner.assertEquals(
				"luminance of white",
				1.0,
				Theme.luminance(Color.WHITE).let { kotlin.math.round(it * 1000) / 1000.0 },
			)
			TestRunner.assertEquals(
				"black on white is 21:1",
				21.0,
				Theme.contrastRatio(Color.BLACK, Color.WHITE).let { kotlin.math.round(it * 10) / 10.0 },
			)
		}
	}

	// --- 2. The Swing defaults ------------------------------------------------------------------

	private fun defaultsTests(mode: ThemeMode) = TestRunner.suite("Theme · ${mode.name.lowercase()} Swing defaults") {
		Theme.install()
		TestRunner.test("the Swing defaults published by the theme are legible") {
		fun pair(name: String, backgroundKey: String, foregroundKey: String, minimum: Double = AA) {
			val background = UIManager.getColor(backgroundKey)
			val foreground = UIManager.getColor(foregroundKey)
			if (background == null || foreground == null) {
				TestRunner.fail("$name: missing default ($backgroundKey=$background, $foregroundKey=$foreground)")
			}
			val ratio = Theme.contrastRatio(foreground, background)
			assertTrue(
				"$name ($foregroundKey on $backgroundKey): ${ratio.format(2)}:1 (needs $minimum:1)",
				ratio >= minimum,
			)
		}

		pair("button", "Button.background", "Button.foreground")
		pair("disabled button", "Button.background", "Button.disabledText", AA_UI)
		pair("toggle button", "ToggleButton.background", "ToggleButton.foreground")
		pair("text field", "TextField.background", "TextField.foreground")
		pair("password field", "PasswordField.background", "PasswordField.foreground")
		pair("text area", "TextArea.background", "TextArea.foreground")
		pair("combo box", "ComboBox.background", "ComboBox.foreground")
		pair("check box", "CheckBox.background", "CheckBox.foreground")
		pair("disabled check box", "CheckBox.background", "CheckBox.disabledText", AA_UI)
		pair("radio button", "RadioButton.background", "RadioButton.foreground")
		pair("label", "Label.background", "Label.foreground")
		pair("disabled label", "Label.background", "Label.disabledForeground", AA_UI)
		pair("list", "List.background", "List.foreground")
		pair("selection", "List.selectionBackground", "List.selectionForeground")
		pair("table", "Table.background", "Table.foreground")
		pair("table selection", "Table.selectionBackground", "Table.selectionForeground")
		pair("table header", "TableHeader.background", "TableHeader.foreground")
		pair("tree", "Tree.background", "Tree.foreground")
		pair("menu bar", "MenuBar.background", "MenuBar.foreground")
		pair("menu", "Menu.background", "Menu.foreground")
		pair("menu item", "MenuItem.background", "MenuItem.foreground")
		pair("disabled menu item", "MenuItem.background", "MenuItem.disabledForeground", AA_UI)
		pair("selected menu item", "MenuItem.selectionBackground", "MenuItem.selectionForeground")
		pair("check box menu item", "CheckBoxMenuItem.background", "CheckBoxMenuItem.foreground")
		pair("tooltip", "ToolTip.background", "ToolTip.foreground")
		// A progress bar carries no text: 3:1 is the applicable threshold.
		pair("progress bar", "ProgressBar.background", "ProgressBar.foreground", AA_UI)
		pair("option pane", "OptionPane.background", "OptionPane.messageForeground")
		pair("spinner", "Spinner.background", "Spinner.foreground")
		pair("filled button", ACCENT_BACKGROUND_KEY, ACCENT_FOREGROUND_KEY)

		}
		TestRunner.test("the Look and Feel is installed and follows the theme") {
			TestRunner.assertTrue(
				"a Look and Feel is installed",
				UIManager.getLookAndFeel().name.isNotBlank(),
			)
			TestRunner.assertEquals(
				"the panel background follows the theme",
				Theme.background,
				UIManager.getColor("Panel.background"),
			)
		}
	}

	// --- 3. The controls the application builds --------------------------------------------------

	private fun controlTests(mode: ThemeMode) = TestRunner.suite("Theme · ${mode.name.lowercase()} controls") {
		TestRunner.test("every themed control pairs a legible foreground with its background") {
		fun legible(name: String, component: javax.swing.JComponent) {
			val ratio = Theme.contrastRatio(component.foreground, component.background)
			assertTrue(
				"$name: ${ratio.format(2)}:1 (${component.foreground.hex()} on ${component.background.hex()})",
				ratio >= AA,
			)
		}

		legible("Ui.button", Ui.button("Save"))
		legible("Ui.accentButton", Ui.accentButton("Search"))
		legible("Ui.textField", Ui.textField())
		legible("Ui.searchField", Ui.searchField("Search") {})
		legible("Ui.checkBox", Ui.checkBox("Enabled"))
		legible("Ui.comboBox", Ui.comboBox(listOf("one", "two")))
		legible("Ui.textArea", Ui.textArea("text"))
		legible("Ui.spinner", Ui.spinner(SpinnerNumberModel(8, 0, 96, 4)))

		// The rendered result, not just the properties: a Look and Feel that paints its own (light)
		// face over a light foreground must fail here.
		val button = Ui.button("Save").apply { setSize(140, 40) }
		val image = render(button, 140, 40)
		val dominant = dominantColour(image)
		TestRunner.assertTrue(
			"a button is painted with the theme surface (dominant ${dominant.hex()}, expected ${Theme.surfaceVariant.hex()})",
			distance(dominant, Theme.surfaceVariant) <= 12.0,
		)
		TestRunner.assertTrue(
			"the button text is legible on what is really painted: " +
				Theme.contrastRatio(Theme.readableOn(dominant), dominant).format(2) + ":1",
			Theme.contrastRatio(Theme.readableOn(dominant), dominant) >= AA,
		)
			TestRunner.assertTrue(
				"the button is not white-on-white",
				Theme.contrastRatio(button.foreground, dominant) >= AA,
			)
		}
	}

	// --- 4. Keyboard focus ---------------------------------------------------------------------

	private fun focusRingTests(mode: ThemeMode) = TestRunner.suite("Theme · ${mode.name.lowercase()} focus") {
		TestRunner.test("themed controls draw a visible keyboard focus ring") {
		val button = Ui.button("Save").apply { setSize(140, 40) }
		TestRunner.assertTrue(
			"themed controls draw their own focus ring",
			button.border is FocusBorder,
		)
		val withoutFocus = render(button, 140, 40)
		button.putClientProperty(FocusBorder.FORCE_FOCUS_PROPERTY, true)
		val withFocus = render(button, 140, 40)
		button.putClientProperty(FocusBorder.FORCE_FOCUS_PROPERTY, null)
		val changed = (0 until 140).sumOf { x ->
			(0 until 40).count { y -> withoutFocus.getRGB(x, y) != withFocus.getRGB(x, y) }
		}
		TestRunner.assertTrue(
			"the focus ring repaints the border ($changed pixels changed)",
			changed > 100,
		)
		TestRunner.assertTrue(
			"the focus ring is visible against the panel: " +
				Theme.contrastRatio(Theme.focus, Theme.background).format(2) + ":1",
			Theme.contrastRatio(Theme.focus, Theme.background) >= AA_UI,
		)
		}
	}

	// --- Helpers --------------------------------------------------------------------------------

	private fun render(component: javax.swing.JComponent, width: Int, height: Int): BufferedImage {
		val panel = JPanel(java.awt.BorderLayout()).apply {
			background = Theme.background
			add(component)
		}
		panel.setSize(width, height)
		panel.doLayout()
		component.setSize(width, height)
		component.doLayout()
		val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
		val graphics = image.createGraphics()
		try {
			panel.paint(graphics)
		} finally {
			graphics.dispose()
		}
		return image
	}

	private fun dominantColour(image: BufferedImage): Color {
		val counts = HashMap<Int, Int>()
		for (y in 0 until image.height) {
			for (x in 0 until image.width) {
				val rgb = image.getRGB(x, y) and 0xFFFFFF
				counts[rgb] = (counts[rgb] ?: 0) + 1
			}
		}
		val rgb = counts.maxByOrNull { it.value }?.key ?: 0
		return Color(rgb)
	}

	private fun distance(a: Color, b: Color): Double = kotlin.math.sqrt(
		((a.red - b.red) * (a.red - b.red) +
			(a.green - b.green) * (a.green - b.green) +
			(a.blue - b.blue) * (a.blue - b.blue)).toDouble(),
	)

	private fun Color.hex(): String = "#%02X%02X%02X".format(red, green, blue)

	private fun Double.format(digits: Int): String = "%.${digits}f".format(this)

	private val ACCENT_BACKGROUND_KEY: String = org.koitharu.kotatsu.desktop.ui.ACCENT_BUTTON_BACKGROUND
	private val ACCENT_FOREGROUND_KEY: String = org.koitharu.kotatsu.desktop.ui.ACCENT_BUTTON_FOREGROUND
}
