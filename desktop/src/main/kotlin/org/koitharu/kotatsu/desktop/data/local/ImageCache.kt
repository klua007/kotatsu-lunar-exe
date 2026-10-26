/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * The Android app loads pages through Coil (with the SSIV view for zooming). On the desktop we
 * decode with ImageIO, keep recently used pages in a bounded in-memory LRU and write cover
 * thumbnails to the cache directory.
 */
package org.koitharu.kotatsu.desktop.data.local

import org.koitharu.kotatsu.desktop.core.AtomicFiles
import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.core.AppDirs
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

object ImageDecoder {

	private const val TAG = "ImageDecoder"

	fun decode(bytes: ByteArray): BufferedImage? = try {
		ImageIO.read(ByteArrayInputStream(bytes))
	} catch (e: Exception) {
		Log.w(TAG, "Cannot decode image (${bytes.size} bytes)", e)
		null
	}

	fun read(path: Path): BufferedImage? = try {
		if (!Files.isRegularFile(path)) null else ImageIO.read(path.toFile())
	} catch (e: Exception) {
		Log.w(TAG, "Cannot read image $path", e)
		null
	}

	/** Scales an image down to [targetWidth], keeping the aspect ratio. Never upscales. */
	fun scaleToWidth(source: BufferedImage, targetWidth: Int): BufferedImage {
		if (source.width <= targetWidth) return source
		val ratio = targetWidth.toDouble() / source.width
		val targetHeight = (source.height * ratio).toInt().coerceAtLeast(1)
		val target = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
		val graphics = target.createGraphics()
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
			graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null)
		} finally {
			graphics.dispose()
		}
		return target
	}

	fun writePng(image: BufferedImage, target: Path) {
		val buffer = java.io.ByteArrayOutputStream()
		ImageIO.write(image, "png", buffer)
		AtomicFiles.writeBytes(target, buffer.toByteArray())
	}
}

/**
 * Bounded LRU of decoded pages, shared by the reader and the UI. Keys are `chapterId:pageIndex`.
 * Memory is bounded by entry count rather than bytes, which is predictable enough for a reader
 * that keeps a handful of pages around.
 */
sealed class PageLoad {

	data class Ok(val image: BufferedImage) : PageLoad()

	/** The page bytes could not be obtained (offline, timeout, HTTP error, not downloaded yet). */
	data object Missing : PageLoad()

	/** The bytes arrived but the image format is not supported by ImageIO (WebP/AVIF). */
	data object Undecodable : PageLoad()
}

object PageImageCache {

	private const val MAX_ENTRIES = 12

	private val cache = object : LinkedHashMap<String, BufferedImage>(MAX_ENTRIES, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BufferedImage>): Boolean =
			size > MAX_ENTRIES
	}

	private val executor = Executors.newFixedThreadPool(2) { runnable ->
		Thread(runnable, "page-decoder").apply { isDaemon = true }
	}
	private val inFlight = AtomicInteger(0)

	fun key(chapterId: Long, page: Int): String = "$chapterId:$page"

	fun get(chapterId: Long, page: Int): BufferedImage? = synchronized(cache) { cache[key(chapterId, page)] }

	fun put(chapterId: Long, page: Int, image: BufferedImage) = synchronized(cache) {
		cache[key(chapterId, page)] = image
	}

	/** Loads a page on the calling thread (used by the EDT for the visible page). */
	fun load(chapterId: Long, page: Int, content: ChapterContent): BufferedImage? =
		(loadDetailed(chapterId, page, content) as? PageLoad.Ok)?.image

	/**
	 * Like [load] but keeps the reason of a failure so the reader can show "network" instead of
	 * "still loading" for a remote page, or "unsupported format" for a WebP/AVIF page.
	 */
	fun loadDetailed(chapterId: Long, page: Int, content: ChapterContent): PageLoad {
		get(chapterId, page)?.let { return PageLoad.Ok(it) }
		val bytes = content.readPage(page) ?: return PageLoad.Missing
		val image = ImageDecoder.decode(bytes) ?: return PageLoad.Undecodable
		put(chapterId, page, image)
		return PageLoad.Ok(image)
	}

	/** Decodes in the background so that scrolling feels immediate. */
	fun preload(chapterId: Long, pages: List<Int>, content: ChapterContent) {
		if (inFlight.get() > 6) return
		for (page in pages) {
			if (get(chapterId, page) != null) continue
			inFlight.incrementAndGet()
			executor.execute {
				try {
					load(chapterId, page, content)
				} finally {
					inFlight.decrementAndGet()
				}
			}
		}
	}

	fun clear() = synchronized(cache) { cache.clear() }

	fun size(): Int = synchronized(cache) { cache.size }
}

/**
 * Cover thumbnails for the library grid: the first page of the first chapter, scaled to
 * [COVER_WIDTH] and cached as a PNG under `<cache>/covers/<mangaId>.png`.
 */
object CoverCache {

	const val COVER_WIDTH = 240

	private fun coverFile(mangaId: Long): Path = AppDirs.coversCacheDir.resolve("$mangaId.png")

	fun existing(mangaId: Long): Path? = coverFile(mangaId).takeIf { Files.isRegularFile(it) }

	/** Returns the cover path, generating it with [producer] when it is not cached yet. */
	fun getOrCreate(mangaId: Long, producer: () -> BufferedImage?): Path? {
		existing(mangaId)?.let { return it }
		val image = producer() ?: return null
		return try {
			val target = coverFile(mangaId)
			ImageDecoder.writePng(ImageDecoder.scaleToWidth(image, COVER_WIDTH), target)
			target
		} catch (e: Exception) {
			Log.w("CoverCache", "Cannot write cover for $mangaId", e)
			null
		}
	}

	fun evict(mangaId: Long) {
		runCatching { Files.deleteIfExists(coverFile(mangaId)) }
	}

	fun cachedCoverCount(): Int = runCatching {
		Files.list(AppDirs.coversCacheDir).use { it.count().toInt() }
	}.getOrDefault(0)
}
