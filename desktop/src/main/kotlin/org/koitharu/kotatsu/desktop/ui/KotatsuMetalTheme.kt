/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * A `MetalTheme` built from [Theme]'s palette.
 *
 * Why this file exists: the Windows Look and Feel paints buttons, check boxes, combo boxes and
 * scroll bars with the native XP skin and ignores `Button.background`/`Button.foreground`. Setting
 * a light foreground on a button whose face is still painted by Windows is what produced the
 * "white text on a light button" of the 0.2.0 screenshots.
 *
 * Metal is the only Look and Feel shipped with the JDK that paints every control from an
 * application supplied colour table, so the application installs Metal plus this theme: whatever
 * `Theme.applyUiDefaults()` does not override explicitly still lands on a palette colour instead of
 * a hard-coded grey or blue.
 */
package org.koitharu.kotatsu.desktop.ui

import javax.swing.plaf.ColorUIResource
import javax.swing.plaf.metal.MetalTheme

class KotatsuMetalTheme : MetalTheme() {

	override fun getName(): String = "Kotatsu Lunar ${Theme.mode.name.lowercase()}"

	// --- Metal's six "primary" colours ----------------------------------------------------------
	//
	// primary1/2/3 are the accent ramp (selection, focus, slider/scroll-bar furniture),
	// secondary1/2/3 are the neutral ramp (borders, shadows and the control face itself).

	override fun getPrimary1(): ColorUIResource = ui(Theme.accentSolid)

	override fun getPrimary2(): ColorUIResource = ui(Theme.accent)

	override fun getPrimary3(): ColorUIResource = ui(Theme.accent)

	override fun getSecondary1(): ColorUIResource = ui(Theme.border)

	override fun getSecondary2(): ColorUIResource = ui(Theme.outline)

	override fun getSecondary3(): ColorUIResource = ui(Theme.surfaceVariant)

	// --- Text ------------------------------------------------------------------------------------

	override fun getControlTextColor(): ColorUIResource = ui(Theme.onBackground)

	override fun getSystemTextColor(): ColorUIResource = ui(Theme.onBackground)

	override fun getUserTextColor(): ColorUIResource = ui(Theme.onBackground)

	override fun getInactiveControlTextColor(): ColorUIResource = ui(Theme.onSurfaceDisabled)

	override fun getInactiveSystemTextColor(): ColorUIResource = ui(Theme.onSurfaceDisabled)

	// --- Fonts ------------------------------------------------------------------------------------

	override fun getControlTextFont(): javax.swing.plaf.FontUIResource = font { Theme.baseFont }

	override fun getSystemTextFont(): javax.swing.plaf.FontUIResource = font { Theme.baseFont }

	override fun getUserTextFont(): javax.swing.plaf.FontUIResource = font { Theme.baseFont }

	override fun getMenuTextFont(): javax.swing.plaf.FontUIResource = font { Theme.baseFont }

	override fun getWindowTitleFont(): javax.swing.plaf.FontUIResource = font { Theme.fontH2 }

	override fun getSubTextFont(): javax.swing.plaf.FontUIResource = font { Theme.fontSmall }

	// --- Controls --------------------------------------------------------------------------------

	/** Face of buttons, menu items, combo boxes, scroll bars: the raised surface. */
	override fun getControl(): ColorUIResource = ui(Theme.surfaceVariant)

	override fun getControlShadow(): ColorUIResource = ui(Theme.controlShadow)

	override fun getControlDarkShadow(): ColorUIResource = ui(Theme.controlDarkShadow)

	override fun getControlInfo(): ColorUIResource = ui(Theme.outline)

	override fun getControlHighlight(): ColorUIResource = ui(Theme.controlHighlight)

	override fun getControlDisabled(): ColorUIResource = ui(Theme.surface)

	override fun getFocusColor(): ColorUIResource = ui(Theme.focus)

	override fun getPrimaryControl(): ColorUIResource = ui(Theme.selectionBackground)

	override fun getPrimaryControlShadow(): ColorUIResource = ui(Theme.accentSolid)

	override fun getPrimaryControlDarkShadow(): ColorUIResource = ui(Theme.accentSolid)

	override fun getPrimaryControlInfo(): ColorUIResource = ui(Theme.accentSolid)

	override fun getPrimaryControlHighlight(): ColorUIResource = ui(Theme.accent)

	// --- Menus, windows, separators --------------------------------------------------------------

	override fun getMenuBackground(): ColorUIResource = ui(Theme.surface)

	override fun getMenuSelectedBackground(): ColorUIResource = ui(Theme.selectionBackground)

	override fun getMenuSelectedForeground(): ColorUIResource = ui(Theme.selectionForeground)

	override fun getMenuDisabledForeground(): ColorUIResource = ui(Theme.onSurfaceDisabled)

	override fun getSeparatorBackground(): ColorUIResource = ui(Theme.surface)

	override fun getSeparatorForeground(): ColorUIResource = ui(Theme.border)

	override fun getAcceleratorForeground(): ColorUIResource = ui(Theme.onSurfaceVariant)

	override fun getAcceleratorSelectedForeground(): ColorUIResource = ui(Theme.selectionForeground)

	override fun getDesktopColor(): ColorUIResource = ui(Theme.background)

	override fun getWindowTitleBackground(): ColorUIResource = ui(Theme.surface)

	override fun getWindowTitleForeground(): ColorUIResource = ui(Theme.onBackground)

	override fun getWindowTitleInactiveBackground(): ColorUIResource = ui(Theme.surface)

	override fun getWindowTitleInactiveForeground(): ColorUIResource = ui(Theme.onSurfaceVariant)

	private fun ui(color: java.awt.Color): ColorUIResource = ColorUIResource(color)

	private fun font(block: () -> java.awt.Font): javax.swing.plaf.FontUIResource =
		javax.swing.plaf.FontUIResource(block())
}
