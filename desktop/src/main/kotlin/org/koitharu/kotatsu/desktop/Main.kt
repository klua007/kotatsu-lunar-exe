/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: application entry point. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop

import org.koitharu.kotatsu.desktop.core.AppDirs
import org.koitharu.kotatsu.desktop.core.BuildInfo
import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.core.i18n.I18n
import org.koitharu.kotatsu.desktop.core.i18n.K
import org.koitharu.kotatsu.desktop.core.i18n.tr
import org.koitharu.kotatsu.desktop.data.AppRepository
import org.koitharu.kotatsu.desktop.ui.MainWindow
import org.koitharu.kotatsu.desktop.ui.Theme
import org.koitharu.kotatsu.desktop.ui.Ui
import java.awt.GraphicsEnvironment
import java.nio.file.Paths
import javax.swing.SwingUtilities

private const val TAG = "Main"

fun main(args: Array<String>) {
	when {
		args.contains("--version") -> {
			println("${BuildInfo.APP_NAME} ${BuildInfo.APP_VERSION}")
			return
		}

		args.contains("--help") -> {
			println(
				"""
				${BuildInfo.APP_NAME} ${BuildInfo.APP_VERSION} — unofficial desktop port of Kotatsu-Redo

				Usage:
				  kotatsu-lunar                 start the application
				  kotatsu-lunar --version       print version and exit
				  kotatsu-lunar --smoke-test    run the built-in self test (no window) and exit
				  kotatsu-lunar --import <path> import CBZ/ZIP files or folders and exit
				  kotatsu-lunar --data-dir      print the data directory and exit

				Data directory: ${AppDirs.dataDir}
				""".trimIndent(),
			)
			return
		}

		args.contains("--data-dir") -> {
			println(AppDirs.dataDir)
			return
		}

		args.contains("--smoke-test") -> {
			val ok = SmokeTest.run()
			System.exit(if (ok) 0 else 1)
		}
	}

	System.setProperty("kotatsu.test", "0")
	Thread.setDefaultUncaughtExceptionHandler { thread, error ->
		Log.e(TAG, "Uncaught exception in ${thread.name}", error)
		if (!GraphicsEnvironment.isHeadless()) {
			runCatching { Ui.showError(null, tr(K.ERROR_UNEXPECTED).format(error.message ?: error.javaClass.simpleName)) }
		}
	}

	AppDirs.ensureCreated()
	Log.i(TAG, "${BuildInfo.APP_NAME} ${BuildInfo.APP_VERSION} starting (data: ${AppDirs.dataDir})")

	val repo = AppRepository(
		settings = org.koitharu.kotatsu.desktop.data.store.SettingsStore(AppDirs.dataDir.resolve("settings.json")),
		library = org.koitharu.kotatsu.desktop.data.store.LibraryStore(AppDirs.dataDir.resolve("library.json")),
		activity = org.koitharu.kotatsu.desktop.data.store.ActivityStore(AppDirs.dataDir.resolve("activity.json")),
	)
	repo.init()

	// Command-line import (useful for scripting and for the Windows build verification).
	val importIndex = args.indexOf("--import")
	if (importIndex >= 0 && args.size > importIndex + 1) {
		val paths = args.drop(importIndex + 1).map { Paths.get(it) }
		val result = repo.importPaths(paths)
		repo.flush()
		println("Imported: ${result.importedManga} new, ${result.updatedManga} updated, ${result.importedChapters} chapters")
		result.messages.forEach(::println)
		return
	}

	I18n.setLanguage(repo.settings.language)
	// Theme.set() installs the Look and Feel and only then publishes the palette: the Windows Look
	// and Feel ignores the colours of the components it paints natively (buttons, check boxes,
	// combo boxes), which is what produced unreadable dark-theme buttons in 0.2.0.
	Theme.set(repo.settings.theme, repo.settings.uiScale)

	if (GraphicsEnvironment.isHeadless()) {
		System.err.println("No graphical environment available. Use --smoke-test for a headless check.")
		System.exit(2)
	}

	// The Look and Feel is owned by Theme (see Theme.install()); installing anything here would
	// throw the palette away again.

	SwingUtilities.invokeLater {
		runCatching {
			val window = MainWindow(repo)
			window.isVisible = true
			Log.i(TAG, "Main window shown")
		}.onFailure { error ->
			Log.e(TAG, "Cannot start the UI", error)
			Ui.showError(null, tr(K.ERROR_UNEXPECTED).format(error.message ?: error.javaClass.simpleName))
			System.exit(1)
		}
	}
}
