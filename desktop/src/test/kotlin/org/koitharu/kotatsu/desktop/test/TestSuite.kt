/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: entry point of the test suite. Run with `./gradlew desktopTest` or, without Gradle,
 * `scripts/run-tests.sh` (see docs/BUILD.md).
 * Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.test

fun main() {
	System.setProperty("kotatsu.test", "1")
	System.setProperty("java.awt.headless", "true")
	println("Kotatsu Lunar — desktop test suite")
	CoreTests.run()
	ContentTests.run()
	StorageTests.run()
	BackupTests.run()
	UiTests.run()
	val exitCode = TestRunner.finish()
	Fixtures.cleanup()
	kotlin.system.exitProcess(exitCode)
}
