/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.data.store

import org.koitharu.kotatsu.desktop.core.AtomicFiles
import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.core.json.Json
import org.koitharu.kotatsu.desktop.core.json.JsonValue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Base class for the JSON documents that make up the local store.
 *
 * Writes are atomic (temp file + move) and are coalesced: reading a chapter touched the history
 * entry on every page turn, so writing the whole document each time would hammer the disk. A
 * pending write is flushed when [flush] is called (on window close) or after [debounceMillis].
 *
 * Corrupted documents are never silently discarded: the broken file is renamed to `.corrupt-<time>`
 * and a warning is logged, so the user still has the data on disk.
 */
abstract class PersistentStore(
	private val file: Path,
	private val debounceMillis: Long = 400L,
) {

	private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
		Thread(runnable, "store-writer-${file.fileName}").apply { isDaemon = true }
	}
	private val savePending = AtomicBoolean(false)
	private val lock = Any()

	/** Loads the document from disk. Safe to call once at startup. */
	fun load() {
		synchronized(lock) {
			val text = try {
				AtomicFiles.readString(file)
			} catch (e: Exception) {
				Log.e(TAG, "Cannot read $file", e)
				null
			}
			val root = if (text.isNullOrBlank()) null else runCatching { Json.parse(text) }.getOrElse { error ->
				Log.e(TAG, "Corrupted store $file, keeping a copy", error)
				backupCorrupted()
				null
			}
			onLoad(root)
		}
	}

	/** Called with the parsed document (or null when the file is missing/unusable). */
	protected abstract fun onLoad(root: JsonValue?)

	/** Builds the document to persist. */
	protected abstract fun buildDocument(): JsonValue

	protected open fun onLoaded() = Unit

	/** Marks the document as dirty; the actual write happens shortly after or on [flush]. */
	protected fun markDirty() {
		if (savePending.compareAndSet(false, true)) {
			executor.schedule({ saveNow() }, debounceMillis, TimeUnit.MILLISECONDS)
		}
	}

	fun saveNow() {
		savePending.set(false)
		val document = synchronized(lock) {
			runCatching { buildDocument() }.getOrElse { error ->
				Log.e(TAG, "Cannot serialise $file", error)
				return
			}
		}
		try {
			AtomicFiles.writeString(file, Json.stringify(document, pretty = true))
		} catch (e: Exception) {
			Log.e(TAG, "Cannot write $file", e)
		}
	}

	fun flush() {
		if (savePending.getAndSet(false)) {
			saveNow()
		}
	}

	protected fun <T> locked(block: () -> T): T = synchronized(lock) { block() }

	private fun backupCorrupted() {
		runCatching {
			val backup = file.resolveSibling("${file.fileName}.corrupt-${System.currentTimeMillis()}")
			Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING)
		}
	}

	companion object {
		private const val TAG = "Store"
	}
}
