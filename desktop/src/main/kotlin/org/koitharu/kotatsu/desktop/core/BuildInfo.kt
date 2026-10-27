/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.core

/**
 * Single source of truth for the version of the application.
 *
 * Everything that needs it reads this constant or extracts it from this file: the jar manifest
 * (`scripts/build.sh`, `scripts/build-windows.ps1`, `desktop/build.gradle.kts`), the Windows CI
 * workflow (`.github/workflows/build-windows.yml`, which also asserts that the packaged .exe
 * reports the very same string), the HTTP User-Agent and the documentation. Nothing else may
 * carry a hard-coded version.
 */
object BuildInfo {

	const val APP_NAME = "Kotatsu Lunar"

	/**
	 * 0.2.1 — fixes the MangaDex "HTTP 400" (the source sent `contentRating[]=porn` instead of the
	 * enumerated `pornographic`, and `status=` instead of `status[]=`), surfaces the API's own
	 * error message, and rewrites the dark/light themes so every control reaches WCAG AA.
	 */
	const val APP_VERSION = "0.2.1"

	/** Upstream commit this port was started from (see NOTICE.md). */
	const val UPSTREAM_COMMIT = "d45b7c13de260b6c78de78ccbf968daf2187f1cf"
	const val UPSTREAM_URL = "https://github.com/Kotatsu-Redo/Kotatsu-Redo"
}
