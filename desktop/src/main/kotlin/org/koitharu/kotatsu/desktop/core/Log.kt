/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.core

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level

/**
 * Minimal logger: writes to `logs/kotatsu.log` and mirrors the last lines in memory so the UI can
 * show them in a "diagnostics" dialog. Avoids pulling a logging framework into a dependency-free
 * build.
 */
object Log {

	private const val MAX_MEMORY_LINES = 500

	private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
	private val memory = CopyOnWriteArrayList<String>()
	private val lock = Any()

	val memoryLines: List<String> get() = memory.toList()

	fun d(tag: String, message: String) = write("DEBUG", tag, message, null)

	fun i(tag: String, message: String) = write("INFO", tag, message, null)

	fun w(tag: String, message: String, error: Throwable? = null) = write("WARN", tag, message, error)

	fun e(tag: String, message: String, error: Throwable? = null) = write("ERROR", tag, message, error)

	fun logFile(): java.nio.file.Path = AppDirs.logsDir.resolve("kotatsu.log")

	private fun write(level: String, tag: String, message: String, error: Throwable?) {
		val line = buildString {
			append(LocalDateTime.now().format(formatter))
			append(' ')
			append(level)
			append(' ')
			append('[').append(tag).append("] ")
			append(message)
			if (error != null) {
				append(" — ").append(error.javaClass.simpleName).append(": ").append(error.message)
			}
		}
		memory.add(line)
		while (memory.size > MAX_MEMORY_LINES) memory.removeAt(0)
		if (isTestMode()) return
		System.err.println(line)
		if (error != null && level == "ERROR") {
			val writer = StringWriter()
			error.printStackTrace(PrintWriter(writer))
			memory.add(writer.toString())
		}
		synchronized(lock) {
			runCatching {
				createDirectories(AppDirs.logsDir)
				Files.write(
					logFile(),
					(line + System.lineSeparator()).toByteArray(),
					java.nio.file.StandardOpenOption.CREATE,
					java.nio.file.StandardOpenOption.APPEND,
				)
			}
		}
		if (Level.SEVERE.getName() == level) { // keeps j.u.l happy if a bridge is added later
			return
		}
	}

	private fun isTestMode(): Boolean = System.getProperty("kotatsu.test") == "1"
}
