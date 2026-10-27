/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * HTTP access for the source layer.
 *
 * The desktop build has no third-party runtime dependencies (docs/ARCHITECTURE.md), so this uses
 * `java.net.http.HttpClient` from the JDK instead of OkHttp. The interface exists so tests can
 * replace the network with a local server or a scripted transport: every source test runs offline.
 */
package org.koitharu.kotatsu.desktop.sources.net

import org.koitharu.kotatsu.desktop.core.Log
import org.koitharu.kotatsu.desktop.core.json.Json
import org.koitharu.kotatsu.desktop.core.json.arr
import org.koitharu.kotatsu.desktop.core.json.asObj
import org.koitharu.kotatsu.desktop.core.json.asStringOrNull
import org.koitharu.kotatsu.desktop.core.json.obj
import org.koitharu.kotatsu.desktop.core.json.string
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.Executors
import javax.net.ssl.SSLException

/** A GET request. Sources never need anything else (MangaDex and OPDS are read-only APIs). */
data class HttpRequestSpec(
	val url: String,
	val headers: Map<String, String> = emptyMap(),
	val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
	/** Some CDNs refuse requests without a UA; sources pass the desktop UA explicitly. */
	val userAgent: String = DEFAULT_USER_AGENT,
) {

	companion object {

		const val DEFAULT_TIMEOUT_MS = 12_000L

		val DEFAULT_USER_AGENT: String =
			"KotatsuLunar/${org.koitharu.kotatsu.desktop.core.BuildInfo.APP_VERSION} " +
				"(desktop; +https://github.com/klua007/kotatsu-lunar-exe)"
	}
}

data class HttpResult(
	val code: Int,
	val body: ByteArray,
	val headers: Map<String, List<String>>,
	val url: String,
) {

	val isSuccessful: Boolean get() = code in 200..299

	fun header(name: String): String? =
		headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

	fun text(): String = body.toString(Charsets.UTF_8)

	fun retryAfterSeconds(): Int {
		val raw = header("Retry-After") ?: return 0
		return raw.trim().toIntOrNull() ?: 0
	}
}

/** Injectable HTTP transport; [JdkHttpTransport] is the production implementation. */
interface HttpTransport {

	fun execute(request: HttpRequestSpec): HttpResult
}

class JdkHttpTransport(
	private val defaultTimeoutMs: Long = HttpRequestSpec.DEFAULT_TIMEOUT_MS,
	private val followRedirects: Boolean = true,
) : HttpTransport {

	private val executor = Executors.newFixedThreadPool(4) { runnable ->
		Thread(runnable, "source-http").apply { isDaemon = true }
	}

	private val client: HttpClient = HttpClient.newBuilder()
		.followRedirects(if (followRedirects) HttpClient.Redirect.NORMAL else HttpClient.Redirect.NEVER)
		.connectTimeout(Duration.ofMillis(defaultTimeoutMs))
		.executor(executor)
		.build()

	override fun execute(request: HttpRequestSpec): HttpResult {
		val builder = HttpRequest.newBuilder()
			.uri(URI.create(request.url))
			.timeout(Duration.ofMillis(request.timeoutMs.takeIf { it > 0 } ?: defaultTimeoutMs))
			.GET()
			.header("User-Agent", request.userAgent)
			.header("Accept-Language", "en,pt-BR;q=0.8")
		request.headers.forEach { (name, value) -> builder.header(name, value) }
		val response = try {
			client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
		} catch (e: HttpTimeoutException) {
			throw org.koitharu.kotatsu.desktop.sources.SourceException(
				org.koitharu.kotatsu.desktop.sources.SourceFailure.timeout(request.url, request.timeoutMs),
			)
		} catch (e: IllegalArgumentException) {
			throw org.koitharu.kotatsu.desktop.sources.SourceException(
				org.koitharu.kotatsu.desktop.sources.SourceFailure.invalidResponse(
					"invalid URL: ${request.url}",
					request.url,
				),
			)
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
			throw org.koitharu.kotatsu.desktop.sources.SourceException(
				org.koitharu.kotatsu.desktop.sources.SourceFailure(
					org.koitharu.kotatsu.desktop.sources.FailureKind.CANCELLED,
					"interrupted",
					url = request.url,
				),
			)
		} catch (e: IOException) {
			throw org.koitharu.kotatsu.desktop.sources.SourceException(classify(e, request.url))
		}
		return HttpResult(
			code = response.statusCode(),
			body = response.body() ?: ByteArray(0),
			headers = response.headers().map(),
			url = response.uri().toString(),
		)
	}

	companion object {

		/**
		 * Splits "the network is gone" (offline, worth showing as such) from "the server answered
		 * with something broken" (typically TLS interception or a proxy).
		 */
		fun classify(error: IOException, url: String): org.koitharu.kotatsu.desktop.sources.SourceFailure {
			val message = error.message ?: error.javaClass.simpleName
			return when (error) {
				is UnknownHostException, is ConnectException ->
					org.koitharu.kotatsu.desktop.sources.SourceFailure.offline(url, message)

				is SSLException ->
					org.koitharu.kotatsu.desktop.sources.SourceFailure.offline(url, "TLS: $message")

				else -> org.koitharu.kotatsu.desktop.sources.SourceFailure.offline(url, message)
			}
		}
	}
}

/**
 * Ordered list of base URLs for one source ("mirrors"), with sticky failover.
 *
 * A request is retried on the next base only when the failure is *not* an HTTP answer: a 404 from
 * the first host is a legitimate answer and must not be hidden by a second request. 5xx and 429 do
 * fall through, because a broken mirror should not block a working one.
 */
class EndpointChooser(
	val bases: List<String>,
	initialIndex: Int = 0,
	private val onSwitch: (index: Int, failedBase: String, reason: String) -> Unit = { _, _, _ -> },
) {

	private var index: Int = initialIndex.coerceIn(0, (bases.size - 1).coerceAtLeast(0))

	val current: String get() = bases.getOrElse(index) { bases.firstOrNull().orEmpty() }

	val currentIndex: Int get() = index

	/** Runs [block] against each base until one produces a non-retryable outcome. */
	fun <T> withFallback(block: (base: String) -> T): T {
		var lastError: Throwable? = null
		for (attempt in 0 until bases.size.coerceAtLeast(1)) {
			val base = current
			try {
				val result = block(base)
				return result
			} catch (e: org.koitharu.kotatsu.desktop.sources.SourceException) {
				lastError = e
				if (!isRetryable(e.failure)) throw e
				if (bases.size > 1) {
					val failed = base
					index = (index + 1) % bases.size
					onSwitch(index, failed, e.failure.kind.name)
					Log.w(TAG, "Switching $failed -> $current (${e.failure.kind})")
				}
			}
		}
		throw lastError ?: IllegalStateException("No endpoint configured")
	}

	private fun isRetryable(failure: org.koitharu.kotatsu.desktop.sources.SourceFailure): Boolean =
		when (failure.kind) {
			org.koitharu.kotatsu.desktop.sources.FailureKind.OFFLINE,
			org.koitharu.kotatsu.desktop.sources.FailureKind.TIMEOUT,
			-> true

			org.koitharu.kotatsu.desktop.sources.FailureKind.HTTP_ERROR -> failure.httpCode >= 500
			org.koitharu.kotatsu.desktop.sources.FailureKind.RATE_LIMITED -> true
			else -> false
		}

	private companion object {
		const val TAG = "Endpoints"
	}
}

/**
 * Removes credentials from a URL before it is written to a log.
 *
 * The sources only ever issue unauthenticated GETs, but a mirror or an OPDS catalogue can be
 * configured with a URL that carries a token in its query string (and a redirect can add one), so
 * every URL that reaches [Log] goes through here first.
 */
fun redactUrl(url: String): String {
	val queryStart = url.indexOf('?')
	if (queryStart < 0) return url
	val query = url.substring(queryStart + 1)
	val safe = query.split('&').joinToString("&") { pair ->
		val separator = pair.indexOf('=')
		if (separator <= 0) return@joinToString pair
		val key = pair.substring(0, separator).lowercase()
		val value = pair.substring(separator + 1)
		if (SENSITIVE_KEYS.any { key.contains(it) }) "$key=***" else "$key=$value"
	}
	return url.substring(0, queryStart + 1) + safe
}

/**
 * Best-effort extraction of a human readable message from an error response body.
 *
 * MangaDex answers a rejected request with
 * `{"result":"error","errors":[{"id":"…","status":400,"title":"validation_exception","detail":"…"}]}`,
 * which says *which parameter* was wrong. Showing that instead of a bare "HTTP 400" is the whole
 * point, so every source shares this parser. Nothing that looks like a credential is ever kept.
 */
fun apiErrorMessage(result: HttpResult, maxLength: Int = 240): String? {
	val body = result.text()
	if (body.isBlank()) return null
	val json = Json.parseOrNull(body) ?: return null
	val error = json.obj("error")?.let { node ->
		node.asStringOrNull() ?: node.string("message") ?: node.string("detail")
	}
	if (error != null) return sanitizeMessage(error, maxLength)
	val fromArray = json.arr("errors")?.items.orEmpty().firstNotNullOfOrNull { node ->
		node.asObj()?.let { it["detail"].asStringOrNull() ?: it["title"].asStringOrNull() }
			?: node.asStringOrNull()
	}
	if (fromArray != null) return sanitizeMessage(fromArray, maxLength)
	val message = json.string("message") ?: json.string("detail")
	return message?.let { sanitizeMessage(it, maxLength) }
}

/**
 * Shortens and scrubs a server provided message: it is untrusted input that ends up in a dialog
 * and in the log, so it is trimmed, collapsed to one line and stripped of anything that looks like
 * a bearer token or a cookie.
 */
private fun sanitizeMessage(raw: String, maxLength: Int): String {
	var text = raw.replace(Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]+"), "bearer ***")
		.replace(Regex("(?i)(token|key|secret|password|session|cookie)=[^&\\s]+"), "$1=***")
		.replace(Regex("\\s+"), " ")
		.trim()
	if (text.length > maxLength) text = text.take(maxLength - 1).trimEnd() + '…'
	return text
}

private val SENSITIVE_KEYS = listOf(
	"token", "key", "secret", "password", "passwd", "session", "cookie", "auth", "credential",
)

/** Small helper: absolute URL from a base and a path, tolerant to trailing slashes. */
fun joinUrl(base: String, path: String): String {
	if (path.startsWith("http://") || path.startsWith("https://")) return path
	val cleanBase = base.trimEnd('/')
	val cleanPath = if (path.startsWith('/')) path else "/$path"
	return cleanBase + cleanPath
}

/** Percent-encodes a query parameter value (RFC 3986, spaces as %20). */
fun encodeQueryValue(value: String): String = buildString {
	value.forEach { char ->
		when {
			char.isLetterOrDigit() || char == '-' || char == '_' || char == '.' || char == '~' -> append(char)
			char == ' ' -> append("%20")
			else -> {
				val bytes = char.toString().toByteArray(Charsets.UTF_8)
				bytes.forEach { byte -> append('%').append("%02X".format(byte)) }
			}
		}
	}
}

/** Appends query parameters, skipping null/empty values. */
fun urlWithQuery(base: String, params: List<Pair<String, String?>>): String {
	val query = params
		.filter { (_, value) -> !value.isNullOrBlank() }
		// Array parameters (`contentRating[]`) are sent percent-encoded; every API used here
		// (MangaDex, OPDS) decodes standard query strings.
		.joinToString("&") { (key, value) -> "${encodeQueryValue(key)}=${encodeQueryValue(value!!)}" }
	if (query.isEmpty()) return base
	return base + (if (base.contains('?')) "&" else "?") + query
}
