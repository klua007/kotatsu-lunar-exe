# Online sources — what works, what does not, and why

This document is the reference for the source layer added to the desktop build. It is deliberately
explicit about limits: a source listed as **implemented** below has a real client, tested against a
local HTTP server that reproduces its protocol; a source listed as **unavailable** says why, and the
Hub shows the same reason next to it.

## 1. Three kinds of sources

| Kind | Meaning | Examples |
| --- | --- | --- |
| `LOCAL` | Files on this computer (CBZ/ZIP, folders of images). Always available, no network. | your own library |
| `NATIVE` | Implemented directly on the JVM in this repository, using `java.net.http` and the JSON/XML parsers in `core/json` and `sources/xml`. | **MangaDex**, **OPDS** (user-added catalogues) |
| `PARSER_PACK` | Would need `kotatsu-parsers-redo`, the parser library used by the Android app. Not bundled with this build. | MangaPark, Bato.to, MangaFire, Comick |

The Hub (`Explore`) shows all three, so the catalogue never pretends to be bigger than it is.

## 2. Why the upstream parsers are not bundled

`kotatsu-parsers-redo` **is** a Kotlin/JVM project (not an Android library): Kotlin 2.2.10, OkHttp
5.x, Okio, kotlinx-coroutines, jsoup, `androidx.collection` and KSP code generation. Its sources are
GPL-3.0, so bundling would be licence-compatible. Two concrete blockers, both verified while working
on this port:

1. **No published JVM artifact.** The repository publishes neither Maven coordinates nor a release
   jar, and the ~900 parser classes plus the `MangaParserSource` enum only exist after its KSP code
   generation runs. Using it therefore requires building it with Gradle and its dependencies
   (OkHttp, Okio, jsoup, coroutines, `androidx.collection`, KSP). This environment has no access to
   Maven Central/JitPack and the desktop build is intentionally dependency-free (see
   `docs/ARCHITECTURE.md`), so the pack cannot be produced or linked here. Shipping a jar built
   elsewhere without being able to test it would be exactly the "pretend it works" this project
   avoids.
2. **Android-shaped seams.** `MangaParser` extends an OkHttp `Interceptor` and asks its
   `MangaLoaderContext` for a HttpClient, a cookie jar, `evaluateJs(...)`, `createBitmap(...)` and
   WebView hooks. A desktop implementation is possible (and is described in section 8), but many
   parsers additionally depend on JavaScript execution or on passing Cloudflare challenges, which a
   Swing/JVM application cannot do without embedding a browser.

The two implemented sources cover the gap that matters for a reader: one large catalogue with a
public API (MangaDex) and the open protocol used by self-hosted servers (OPDS). Everything else is
reported as unavailable, with the reason, in the Hub.

## 3. MangaDex (implemented)

* Protocol: documented public REST API — `GET /manga`, `GET /manga/{id}`, `GET /manga/{id}/feed`,
  `GET /at-home/server/{chapterId}`; images from the host returned by `/at-home/server`, covers from
  the public CDN.
* Works without an account, cookies or JavaScript. No API key.
* Search by title, adult-content switch, chapter languages (default `en` + `pt-br`), tag filter.
* `Explore` uses MangaDex's "most followed" ordering, since the API has no popularity endpoint.
* Chapter feed pages are followed up to 20 × 500 chapters (a self-imposed bound); titles with more
  chapters get the first 10 000 and the details screen says nothing about the rest — refresh adds the
  same ordered set again (no duplicates).
* Alternative base URL: `Hub → MangaDex → Mirror`. Two default bases are tried in order; a base that
  answers 5xx or refuses connections falls through to the next one and stays switched for the
  session. A `404` from the first base is treated as a real answer (no second request).
* Failure mapping: offline/timeout/HTTP 4xx-5xx/429 (`Retry-After` honoured)/invalid JSON/invalid
  payload are separate messages in the UI, never a stack trace.

Tested against a local server that serves the documented response shapes, including 429, 500, 404,
delays (timeouts), malformed JSON and a dead port.

## 4. OPDS 1.2 (implemented, catalogues added by the user)

* Open catalogue protocol (Atom XML) used by Komga, Kavita, Calibre and several public book/comic
  catalogues. Add a catalogue URL in the Hub: for Komga the usual entry point is
  `http://<host>:25600/opds/v1.2/catalog`.
* Navigation feeds are followed one level (library → series); a series feed's acquisition links
  become chapters. OpenSearch (`rel="search"`) templates are used when the catalogue advertises one;
  otherwise the shelf is filtered locally, which is documented here and logged.
* Only archives this build can open are offered: `CBZ/ZIP` (`application/vnd.comicbook+zip`,
  `application/zip`, `.cbz`, `.zip`). **EPUB and PDF acquisition links are skipped** — there is no
  renderer for them, and listing them would create a button that cannot work.
* A chapter is an archive, so reading it means "download once into the page cache, then open it like
  a local file". "Download for offline" stores the same archive under
  `downloads/<mangaId>/<chapterId>.cbz`.
* A body that is not an Atom feed, an empty feed, or a broken entry produces the "invalid response"
  message instead of an empty screen.

## 5. Sources listed as unavailable (and the reason shown in the Hub)

| Source | Reason |
| --- | --- |
| MangaPark | parser pack missing |
| Bato.to | Cloudflare / browser challenge; no WebView in a desktop JVM app |
| MangaFire | catalogue rendered by JavaScript |
| Comick | parser pack missing |

The list is `SourceRegistry.UNAVAILABLE_DESCRIPTORS`; adding a new one is a two-line change and it
immediately appears (disabled, with the reason) in the Hub.

## 6. Image formats

`ImageIO` (JDK) decodes JPEG, PNG, GIF and BMP. **WebP and AVIF are not decodable** without a
third-party decoder, and the desktop build has no third-party runtime dependencies, so a page in
those formats shows "this page uses an image format this build cannot display" instead of a blank
screen. MangaDex serves JPEG/PNG for the pages this build requests (`data`, not `dataSaver`), and the
OPDS archives that were tested contain PNG. This limitation is also listed in
`docs/FEATURE_MATRIX.md`.

## 7. How the layer is organised

```
sources/
  SourceTypes.kt          descriptors, remote entities, capabilities
  SourceFailure.kt        failure taxonomy + SourceOutcome (Success/Failure)
  MangaSource.kt          the contract every source implements (synchronous, no Android types)
  SourceRegistry.kt       catalogue, enable/pin, availability, OPDS catalogue management
  RemoteContentService.kt page cache, archive downloads, CBZ writer
  net/Http.kt             JDK HttpClient transport, endpoint failover, URL helpers
  mangadex/…              MangaDex client
  opds/…                  OPDS client
  xml/Xml.kt              small XML/Atom parser
data/store/SourcesStore.kt  sources.json (enabled, pinned, mirror, availability, catalogues)
ui/hub/HubPanel.kt          the Explore screen
```

Threading: all network calls are blocking and are always executed off the Swing event thread
(`ui/Async.kt`). Page images are cached per chapter under `<cache>/remote/pages/<chapterId>/` and
archives under `<cache>/remote/archives/`; a page is downloaded at most once, and the cache is shared
by the reader and the download manager. `Settings → Clear page cache` removes it.

Failure surfaces:

| Failure | Where it appears |
| --- | --- |
| search/browse | row under the results, per source, with the translated message |
| details/add | modal error, nothing is added to the library |
| chapter opening | message in the page area + status bar |
| single page | "could not load this page" instead of an endless spinner |
| download | error dialog; a partial archive is deleted, the chapter is not marked offline |

## 8. Adding a source

1. Implement `MangaSource` (see `MangaDexSource`): `popular`, `search`, `details`, `pages`,
   optionally `imageHeaders`/`checkAvailability`.
2. Register the instance in `SourceRegistry.rebuild()` with a stable `id` (it is written to
   `library.json`, the backup and `sources.json`).
3. Add tests in `desktop/src/test/.../SourceTests.kt` that run against `FakeServer` — no real site.
4. Update this document and `docs/FEATURE_MATRIX.md`.

For the parser pack, the missing piece is a JVM `MangaLoaderContext`:

```kotlin
class DesktopMangaLoaderContext(transport: HttpTransport) : MangaLoaderContext() {
    override val httpClient: OkHttpClient      // OkHttp 5 with the cookie jar below
    override val cookieJar: CookieJar          // cookies persisted in <data>/sources/cookies.json
    override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? = null
    override fun getConfig(source: MangaParserSource): MangaSourceConfig = …
    override fun getDefaultUserAgent(): String = HttpRequestSpec.DEFAULT_USER_AGENT
}
```

Sources whose `evaluateJs` is essential must then be marked unavailable with the "JavaScript/Cloudflare"
reason, exactly as done today — the difference would be that the other parsers would start working.
The pack would ship as an optional plugin jar, with its own GPL-3.0 notices, so the base build keeps
working without it.

## 9. Legal / content

This project ships **no content and no scraping bypass**. It talks to public, documented endpoints
(MangaDex API, OPDS catalogues) whose operators define what may be read. It does not bypass DRM, paywalls,
Cloudflare challenges or logins, and it does not include copyrighted material. Whatever a user reads
through it remains subject to the terms of the source they configure.
