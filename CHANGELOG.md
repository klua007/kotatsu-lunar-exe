# Changelog

The version is declared once, in `desktop/src/main/kotlin/org/koitharu/kotatsu/desktop/core/BuildInfo.kt`
(`APP_VERSION`). `scripts/build.sh`, `scripts/build-windows.ps1`, `desktop/build.gradle.kts`, the
HTTP `User-Agent` and the CI workflow all read it from there, so the jar, the `.exe`, the installer
name and this file cannot disagree.

## 0.2.1 — bugfix release (the MangaDex HTTP 400)

### Fixed

* **MangaDex answered HTTP 400 for every chapter list.** The source sent
  `contentRating[]=porn`; the API enumerates `safe|suggestive|erotica|pornographic` and rejects the
  whole request with
  `Error validating /contentRating/3: Does not have a value in the enumeration [...]`. Because the
  chapter feed always asks for every content rating, this broke **open details**, **refresh
  chapters** and **add to library** — browse and title search kept working (they only send
  `safe`+`suggestive`), which is why results appeared and only the following click failed.
  Reproduced against the live API, then fixed at the source and pinned by a test that asserts the
  exact query string.
* **A publication-status filter produced a second HTTP 400**: `status=ongoing` (scalar) instead of
  `status[]=ongoing`. The API requires an array
  (`Error validating /status: String value found, but an array is required`). The Hub filter now
  sends the array, maps the UI aliases (ongoing / completed / hiatus / cancelled) through
  `MangaDexSource.STATES` and drops values the API does not know.
* **Pagination could leave the API's result window.** `limit` is clamped to 100 (`offset + limit` to
  10 000) and the chapter feed stops inside the window instead of collecting an HTTP 400
  ("Result window is too large").
* **Errors now say what the source said.** The API's own `errors[].detail` is parsed, scrubbed and
  shown next to the message, and URLs are redacted before they reach the log. Nothing is hidden: the
  dialog still reports the HTTP status, it just also explains it.
* **Dark theme: near-illegible controls.** The palette was published to `UIManager` *before* the
  Look and Feel was installed, which discarded it, and the Windows Look and Feel paints buttons
  natively, ignoring the application colours — the result was light native buttons with light text.
  The theme now installs a Look and Feel driven by `KotatsuMetalTheme` and only then publishes the
  palette: buttons, fields, combo boxes, check boxes, lists, tables, menus, tooltips, disabled text
  and focus indicators are painted by the application with pairs that meet WCAG AA.

### Changed

* Every themed control has a visible **keyboard focus ring** (`FocusBorder`).
* **Interface scale** (100–200 %) is a setting (`Settings → Interface`) and is applied to all fonts;
  theme and scale changes take effect immediately.
* The progress bar used an accent that only reached 2.84:1 against its track; it now uses the one
  that reaches 4.6:1 (found by the new contrast tests).

### Verified

* 142 test cases (was 112): the source layer, the MangaDex request contract and pagination, error
  bodies, both themes' contrast and rendering, storage, CBZ, backups, reader geometry and UI panels.
* `java -jar kotatsu-lunar.jar --smoke-test`: 22 checks, 0 failures.
* Windows CI (`.github/workflows/build-windows.yml`): compile, tests, jar, `jpackage` app-image,
  `KotatsuLunar.exe --version` / `--smoke-test` (with a version-match assertion), portable `.zip`
  and `.msi`.
* `scripts/check_mangadex.py` against the live API (GET only): browse, title search,
  `contentRating[]=pornographic`, `status[]`, details, chapter feed, `/at-home/server` and one page
  image — plus regression guards asserting the old requests are still rejected.

## 0.2.0 — previous release

Online sources (MangaDex, OPDS), the Hub/Explore screen, offline downloads of online chapters,
Android backup import/export, four reading modes, library/history and the Windows packages. Known
issues at that point: the MangaDex chapter-feed request was rejected (fixed above) and the dark
theme was not applied to the native controls (fixed above).
