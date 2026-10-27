/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: export/import dialogs for the backup format shared with the Android app.
 * Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.ui.dialogs

import org.koitharu.kotatsu.desktop.core.AppDirs
import org.koitharu.kotatsu.desktop.core.i18n.K
import org.koitharu.kotatsu.desktop.core.i18n.tr
import org.koitharu.kotatsu.desktop.data.AppRepository
import org.koitharu.kotatsu.desktop.data.backup.BackupReport
import org.koitharu.kotatsu.desktop.ui.Async
import org.koitharu.kotatsu.desktop.ui.Theme
import org.koitharu.kotatsu.desktop.ui.Ui
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.filechooser.FileNameExtensionFilter

object BackupDialog {

	fun showExport(parent: Component, repo: AppRepository) {
		val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
		val chooser = JFileChooser().apply {
			dialogTitle = tr(K.BACKUP_EXPORT)
			currentDirectory = AppDirs.backupsDir.toFile()
			selectedFile = java.io.File("kotatsu-backup-$stamp.zip")
			fileFilter = FileNameExtensionFilter("Kotatsu backup (*.zip)", "zip")
		}
		if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return
		var file = chooser.selectedFile.toPath()
		if (!file.fileName.toString().lowercase().endsWith(".zip")) {
			file = file.resolveSibling("${file.fileName}.zip")
		}
		val target = file
		Async.compute({ repo.exportBackup(target) }) { path ->
			Ui.showInfo(parent, tr(K.BACKUP_EXPORTED).format(path))
		}.also { }
	}

	fun showImport(parent: Component, repo: AppRepository, onDone: () -> Unit = {}) {
		val chooser = JFileChooser().apply {
			dialogTitle = tr(K.BACKUP_IMPORT)
			currentDirectory = AppDirs.backupsDir.toFile()
			fileFilter = FileNameExtensionFilter("Kotatsu backup (*.zip)", "zip")
		}
		if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return
		val source = chooser.selectedFile.toPath()
		if (!Ui.confirm(parent, tr(K.BACKUP_WARNING) + "\n\n" + source.fileName, tr(K.BACKUP_IMPORT))) return
		Async.compute({ repo.importBackup(source) }, onDone = { report ->
			repo.flush()
			showReport(parent, report)
			onDone()
		}, onError = { error ->
			Ui.showError(parent, tr(K.ERROR_IO).format(error.message ?: error.javaClass.simpleName))
		})
	}

	private fun showReport(parent: Component, report: BackupReport) {
		val text = buildString {
			append(tr(K.BACKUP_REPORT)).append("\n")
			append("──────────────\n")
			report.backupAppId?.let { append("Backup: ").append(it).append(" v").append(report.backupAppVersion).append("\n") }
			append(tr(K.BACKUP_REPORT_FAVOURITES).format(report.favourites)).append("\n")
			append(tr(K.BACKUP_REPORT_HISTORY).format(report.history)).append("\n")
			append(tr(K.BACKUP_REPORT_CATEGORIES).format(report.categories)).append("\n")
			append(tr(K.BACKUP_REPORT_BOOKMARKS).format(report.bookmarks)).append("\n")
			append(tr(K.BACKUP_REPORT_SOURCES).format(report.sources)).append("\n")
			append(tr(K.BACKUP_REPORT_SETTINGS).format(report.settingsApplied)).append("\n")
			append(tr(K.BACKUP_REPORT_SKIPPED).format(report.skipped + report.invalid)).append("\n")
			append("\n")
			if (report.mangaFromUnsupportedSources > 0) {
				append(
					"${report.mangaFromUnsupportedSources} title(s) came from Android online sources. " +
						"They are stored in the library file but cannot be opened until online sources " +
						"exist in the desktop build.\n\n",
				)
			}
			if (report.preservedSections.isNotEmpty()) {
				append("Preserved sections (kept verbatim in settings.json): ")
				append(report.preservedSections.joinToString(", "))
				append("\n\n")
			}
			append(tr(K.BACKUP_WARNING)).append("\n")
			append(tr(K.BACKUP_DOWNLOADS_NOTE))
		}
		val area = Ui.textArea(text).apply {
			isEditable = false
			font = Theme.fontMono
			lineWrap = true
			wrapStyleWord = true
		}
		val scroll = JScrollPane(area).apply {
			preferredSize = Dimension(560, 340)
			background = Theme.surface
		}
		JOptionPane.showMessageDialog(
			parent,
			scroll,
			tr(K.BACKUP_IMPORTED),
			JOptionPane.INFORMATION_MESSAGE,
		)
	}

	/** Used by the CLI smoke test. */
	fun defaultBackupName(): String =
		"kotatsu-backup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.zip"

	fun resolveTarget(raw: String): java.nio.file.Path {
		val path = Paths.get(raw)
		return if (path.fileName.toString().lowercase().endsWith(".zip")) path
		else path.resolveSibling("${path.fileName}.zip")
	}

	@Suppress("unused")
	private fun JPanel.noop() = Unit
}
