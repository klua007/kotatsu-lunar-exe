/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: a minimal, dependency-free test runner. The desktop build deliberately has no
 * third-party runtime dependencies, and this keeps `./gradlew desktopTest` (and the offline
 * `scripts/build.sh`) working without downloading a test framework.
 * Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.test

import java.nio.file.Files
import java.nio.file.Path

class AssertionFailure(message: String) : RuntimeException(message)

object TestRunner {

	private val failures = ArrayList<Pair<String, Throwable>>()
	private var executed = 0
	private var currentSuite = ""

	fun suite(name: String, block: () -> Unit) {
		currentSuite = name
		println("\n$name")
		block()
	}

	fun test(name: String, block: () -> Unit) {
		executed++
		try {
			block()
			println("  ok    $name")
		} catch (e: Throwable) {
			failures.add("$currentSuite / $name" to e)
			println("  FAIL  $name → ${e.message ?: e.javaClass.simpleName}")
			if (e !is AssertionFailure) {
				e.printStackTrace(System.out)
			}
		}
	}

	fun finish(): Int {
		println("\n${executed - failures.size}/$executed tests passed")
		if (failures.isNotEmpty()) {
			println("\nFailures:")
			failures.forEach { (name, error) ->
				println("  - $name: ${error.message}")
			}
			return 1
		}
		return 0
	}

	fun fail(message: String): Nothing = throw AssertionFailure(message)

	fun assertTrue(message: String, condition: Boolean) {
		if (!condition) fail(message)
	}

	fun assertEquals(message: String, expected: Any?, actual: Any?) {
		if (expected != actual) fail("$message (expected=<$expected>, actual=<$actual>)")
	}

	fun <T : Any> assertNotNull(message: String, value: T?): T {
		if (value == null) fail(message)
		return value
	}

	fun assertFails(message: String, block: () -> Unit) {
		val thrown = runCatching(block).exceptionOrNull()
		if (thrown == null) fail("$message (no exception was thrown)")
	}

	fun <T : Throwable> assertThrows(message: String, type: Class<T>, block: () -> Unit): T {
		val thrown = runCatching(block).exceptionOrNull()
			?: fail("$message (no exception was thrown)")
		if (!type.isInstance(thrown)) fail("$message (unexpected ${thrown.javaClass.simpleName}: ${thrown.message})")
		@Suppress("UNCHECKED_CAST")
		return thrown as T
	}
}

object Fixtures {

	private val tempDirs = ArrayList<Path>()

	fun tempDir(prefix: String = "kotatsu-test"): Path {
		val dir = Files.createTempDirectory(prefix)
		tempDirs.add(dir)
		return dir
	}

	/** Switches the application home (data directory) to a fresh temporary directory. */
	fun useFreshHome(): Path {
		val home = tempDir("kotatsu-home")
		System.setProperty("kotatsu.home", home.toString())
		return home
	}

	fun cleanup() {
		System.clearProperty("kotatsu.home")
		tempDirs.forEach { dir ->
			runCatching {
				if (Files.exists(dir)) {
					Files.walk(dir).use { stream ->
						stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
					}
				}
			}
		}
		tempDirs.clear()
	}

	fun pngBytes(width: Int = 60, height: Int = 90, shade: Int = 120): ByteArray {
		val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
		val graphics = image.createGraphics()
		graphics.color = java.awt.Color(shade, 80, 40)
		graphics.fillRect(0, 0, width, height)
		graphics.dispose()
		val buffer = java.io.ByteArrayOutputStream()
		javax.imageio.ImageIO.write(image, "png", buffer)
		return buffer.toByteArray()
	}

	fun writeImage(path: Path, shade: Int = 120) {
		Files.createDirectories(path.parent)
		Files.write(path, pngBytes(shade = shade))
	}
}
