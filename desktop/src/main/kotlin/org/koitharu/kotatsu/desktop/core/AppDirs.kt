/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.core

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Resolves the on-disk locations used by the desktop build.
 *
 * Windows conventions are used when running on Windows:
 *
 *  - data:  `%APPDATA%\KotatsuLunar`  (roaming: library, history, settings, backups)
 *  - cache: `%LOCALAPPDATA%\KotatsuLunar\cache` (page cache, thumbnails; safe to delete)
 *  - downloads: `<data>\downloads` (offline chapters, CBZ)
 *
 * On other systems XDG-like paths are used so the same build can be smoke-tested on Linux/macOS.
 *
 * Portable mode: if a file named `portable.marker` exists next to the working directory (or in the
 * application directory), everything is stored in `./KotatsuLunar-data` instead. This is what the
 * "portable .zip" artifact uses. The system property `kotatsu.home` (or `KOTATSU_LUNAR_HOME`) always
 * wins, which is also what the tests use.
 */
object AppDirs {

	private const val APP_DIR_NAME = "KotatsuLunar"

	/**
	 * Resolved on every access instead of once: the tests switch `kotatsu.home` between cases, and a
	 * cached value silently pointed them at the real user profile (which made tests share downloads
	 * and page caches). The resolution is a few string operations, so this costs nothing.
	 */
	val dataDir: Path get() = resolveDataDir()

	val cacheDir: Path get() = resolveCacheDir()

	val downloadsDir: Path get() = dataDir.resolve("downloads")

	val backupsDir: Path get() = dataDir.resolve("backups")

	val logsDir: Path get() = dataDir.resolve("logs")

	val coversCacheDir: Path get() = cacheDir.resolve("covers")

	val pagesCacheDir: Path get() = cacheDir.resolve("pages")

	fun ensureCreated() {
		listOf(dataDir, cacheDir, downloadsDir, backupsDir, logsDir, coversCacheDir, pagesCacheDir)
			.forEach(::createDirectories)
	}

	private fun resolveDataDir(): Path {
		System.getProperty("kotatsu.home")?.takeIf { it.isNotBlank() }?.let { return Paths.get(it) }
		System.getenv("KOTATSU_LUNAR_HOME")?.takeIf { it.isNotBlank() }?.let { return Paths.get(it) }
		detectPortableDir()?.let { return it }
		val os = System.getProperty("os.name").orEmpty().lowercase()
		return if (os.contains("win")) {
			val appData = System.getenv("APPDATA")
			if (!appData.isNullOrBlank()) {
				Paths.get(appData, APP_DIR_NAME)
			} else {
				Paths.get(System.getProperty("user.home"), "AppData", "Roaming", APP_DIR_NAME)
			}
		} else if (os.contains("mac")) {
			Paths.get(System.getProperty("user.home"), "Library", "Application Support", APP_DIR_NAME)
		} else {
			val xdg = System.getenv("XDG_DATA_HOME")
			if (!xdg.isNullOrBlank()) Paths.get(xdg, APP_DIR_NAME)
			else Paths.get(System.getProperty("user.home"), ".local", "share", APP_DIR_NAME)
		}
	}

	private fun resolveCacheDir(): Path {
		val os = System.getProperty("os.name").orEmpty().lowercase()
		if (os.contains("win")) {
			val localAppData = System.getenv("LOCALAPPDATA")
			if (!localAppData.isNullOrBlank()) {
				return Paths.get(localAppData, APP_DIR_NAME, "cache")
			}
			return dataDir.resolve("cache")
		}
		if (System.getProperty("kotatsu.home") != null || detectPortableDir() != null) {
			return dataDir.resolve("cache")
		}
		val xdg = System.getenv("XDG_CACHE_HOME")
		return if (!xdg.isNullOrBlank()) Paths.get(xdg, APP_DIR_NAME)
		else Paths.get(System.getProperty("user.home"), ".cache", APP_DIR_NAME)
	}

	private fun detectPortableDir(): Path? {
		val candidates = buildList {
			System.getProperty("jpackage.app-path")?.let { appPath ->
				runCatching { Paths.get(appPath).parent }.getOrNull()?.let(::add)
			}
			add(Paths.get("").toAbsolutePath())
		}
		for (dir in candidates) {
			if (Files.exists(dir.resolve("portable.marker"))) {
				return dir.resolve("KotatsuLunar-data")
			}
		}
		return null
	}
}

fun createDirectories(path: Path) {
	if (Files.isDirectory(path)) return
	try {
		Files.createDirectories(path)
	} catch (e: IOException) {
		throw IOException("Cannot create directory $path", e)
	}
}

/**
 * Writes a text file atomically: a sibling temporary file is written first and then moved over the
 * target. A crash never leaves a half-written library/settings file behind.
 */
object AtomicFiles {

	fun writeString(target: Path, content: String) {
		createDirectories(target.parent)
		val temp = target.resolveSibling(target.fileName.toString() + ".tmp")
		Files.write(
			temp,
			content.toByteArray(StandardCharsets.UTF_8),
			StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING,
			StandardOpenOption.WRITE,
		)
		try {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
		} catch (_: Exception) {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
		}
	}

	fun readString(source: Path): String? {
		if (!Files.exists(source)) return null
		return String(Files.readAllBytes(source), StandardCharsets.UTF_8)
	}

	fun writeBytes(target: Path, content: ByteArray) {
		createDirectories(target.parent)
		val temp = target.resolveSibling(target.fileName.toString() + ".tmp")
		Files.write(temp, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
		try {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
		} catch (_: Exception) {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
		}
	}
}
