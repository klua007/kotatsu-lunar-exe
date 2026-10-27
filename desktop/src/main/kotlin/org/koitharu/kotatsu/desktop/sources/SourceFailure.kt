/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Failure model of the source layer.
 *
 * Every network call can fail in a handful of ways that need *different* messages for the user,
 * so the source layer never throws raw IOExceptions at the UI: it maps them to [SourceFailure] and
 * the UI asks for a translated message (see docs/SOURCES.md).
 */
package org.koitharu.kotatsu.desktop.sources

import org.koitharu.kotatsu.desktop.core.i18n.K
import org.koitharu.kotatsu.desktop.core.i18n.tr

enum class FailureKind {
	OFFLINE,
	TIMEOUT,
	HTTP_ERROR,
	RATE_LIMITED,
	NOT_FOUND,
	INVALID_RESPONSE,
	SOURCE_UNAVAILABLE,
	CANCELLED,
	IO,
}

/**
 * @param serverMessage human readable explanation returned by the remote API in the response body
 *   (for example the `errors[].detail` of a MangaDex validation error). It is shown to the user, so
 *   it must never contain a token, a cookie or any other credential.
 */
data class SourceFailure(
	val kind: FailureKind,
	val detail: String = "",
	val httpCode: Int = 0,
	val retryAfterSeconds: Int = 0,
	val url: String = "",
	val serverMessage: String? = null,
) {

	/** Translation key describing the failure (never a raw stack trace). */
	val messageKey: String
		get() = when (kind) {
			FailureKind.OFFLINE -> K.SOURCES_ERROR_OFFLINE
			FailureKind.TIMEOUT -> K.SOURCES_ERROR_TIMEOUT
			FailureKind.HTTP_ERROR -> K.SOURCES_ERROR_HTTP
			FailureKind.RATE_LIMITED -> K.SOURCES_ERROR_RATE_LIMITED
			FailureKind.NOT_FOUND -> K.SOURCES_ERROR_NOT_FOUND
			FailureKind.INVALID_RESPONSE -> K.SOURCES_ERROR_INVALID
			FailureKind.SOURCE_UNAVAILABLE -> K.SOURCES_ERROR_UNAVAILABLE
			FailureKind.CANCELLED -> K.SOURCES_ERROR_CANCELLED
			FailureKind.IO -> K.SOURCES_ERROR_IO
		}

	/**
	 * Ready-to-show text. [detail] (the local classification, e.g. "HTTP 400") and [serverMessage]
	 * (what the API answered, e.g. "Error validating /contentRating/3: …") are appended when they
	 * add information, so a 400 is never reported as a bare, unexplained failure.
	 */
	fun message(): String {
		val base = when (kind) {
			FailureKind.HTTP_ERROR -> tr(messageKey).format(httpCode)
			FailureKind.RATE_LIMITED -> if (retryAfterSeconds > 0) {
				tr(messageKey).format(retryAfterSeconds)
			} else {
				tr(K.SOURCES_ERROR_RATE_LIMITED_GENERIC)
			}
			else -> tr(messageKey)
		}
		val suffix = buildList {
			if (detail.isNotBlank()) add(detail)
			serverMessage?.takeIf { it.isNotBlank() }?.let(::add)
		}.joinToString(" · ")
		return if (suffix.isEmpty()) base else "$base ($suffix)"
	}

	companion object {

		fun offline(url: String = "", detail: String = "network unreachable") =
			SourceFailure(FailureKind.OFFLINE, detail, url = url)

		fun timeout(url: String, millis: Long) =
			SourceFailure(FailureKind.TIMEOUT, "after ${millis} ms", url = url)

		fun http(code: Int, url: String, retryAfterSeconds: Int = 0, serverMessage: String? = null) =
			if (code == 429) {
				SourceFailure(
					kind = FailureKind.RATE_LIMITED,
					detail = "HTTP 429",
					httpCode = code,
					retryAfterSeconds = retryAfterSeconds,
					url = url,
					serverMessage = serverMessage,
				)
			} else if (code == 404) {
				SourceFailure(FailureKind.NOT_FOUND, "HTTP 404", code, url = url, serverMessage = serverMessage)
			} else {
				SourceFailure(FailureKind.HTTP_ERROR, "HTTP $code", code, url = url, serverMessage = serverMessage)
			}

		fun invalidResponse(detail: String, url: String = "") =
			SourceFailure(FailureKind.INVALID_RESPONSE, detail, url = url)

		fun unavailable(detail: String) = SourceFailure(FailureKind.SOURCE_UNAVAILABLE, detail)

		fun io(detail: String, url: String = "") = SourceFailure(FailureKind.IO, detail, url = url)
	}
}

/** Result of a source call: either a value or a [SourceFailure] the UI can render. */
sealed class SourceOutcome<out T> {

	data class Success<out T>(val value: T) : SourceOutcome<T>()

	data class Failure(val failure: SourceFailure) : SourceOutcome<Nothing>()

	val isSuccess: Boolean get() = this is Success

	val isFailure: Boolean get() = this is Failure

	fun getOrNull(): T? = (this as? Success)?.value

	fun failureOrNull(): SourceFailure? = (this as? Failure)?.failure

	fun <R> map(transform: (T) -> R): SourceOutcome<R> = when (this) {
		is Success -> Success(transform(value))
		is Failure -> this
	}

	fun <R> flatMap(transform: (T) -> SourceOutcome<R>): SourceOutcome<R> = when (this) {
		is Success -> transform(value)
		is Failure -> this
	}

	companion object {

		inline fun <T> catching(url: String = "", block: () -> T): SourceOutcome<T> = try {
			Success(block())
		} catch (e: SourceException) {
			Failure(e.failure)
		} catch (e: java.net.SocketTimeoutException) {
			Failure(SourceFailure.timeout(url, 0))
		} catch (e: java.io.IOException) {
			Failure(SourceFailure.offline(url, e.message ?: e.javaClass.simpleName))
		} catch (e: Exception) {
			Failure(SourceFailure.invalidResponse(e.message ?: e.javaClass.simpleName, url))
		}
	}
}

/** Thrown internally by HTTP helpers; carries the already classified failure. */
class SourceException(val failure: SourceFailure) : RuntimeException(failure.message())
