/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * A tiny dependency-free JSON implementation. The desktop build intentionally has no third-party
 * runtime dependencies (see docs/ARCHITECTURE.md), and the backup format shared with the Android
 * app is plain JSON, so we need a parser/writer that is fully under our control.
 *
 * The model is deliberately close to kotlinx.serialization's JsonElement so that the backup code
 * ported from `org.koitharu.kotatsu.backups.data` reads almost identically to upstream.
 */
package org.koitharu.kotatsu.desktop.core.json

sealed class JsonValue {

	data object Null : JsonValue()

	data class Bool(val value: Boolean) : JsonValue()

	/** Numbers are kept as [Double]; ids are also kept in [raw] so 64-bit manga ids stay exact. */
	data class Number(val value: Double, val raw: String) : JsonValue() {
		val isIntegral: Boolean get() = raw.isNotEmpty() && raw.none { it == '.' || it == 'e' || it == 'E' }
	}

	data class Str(val value: String) : JsonValue()

	data class Arr(val items: MutableList<JsonValue> = mutableListOf()) : JsonValue() {
		val size: Int get() = items.size
		operator fun get(index: Int): JsonValue = items[index]
	}

	data class Obj(val fields: MutableMap<String, JsonValue> = LinkedHashMap()) : JsonValue() {
		val size: Int get() = fields.size
		operator fun get(key: String): JsonValue? = fields[key]
		operator fun set(key: String, value: JsonValue) {
			fields[key] = value
		}

		fun put(key: String, value: JsonValue): Obj {
			fields[key] = value
			return this
		}

		fun has(key: String): Boolean = fields.containsKey(key)

		fun keys(): Set<String> = fields.keys
	}
}

// ---------------------------------------------------------------------------------------------
// Accessors (tolerant: a wrong type reads as null instead of throwing, like `ignoreUnknownKeys`)
// ---------------------------------------------------------------------------------------------

fun JsonValue?.asObj(): JsonValue.Obj? = this as? JsonValue.Obj

fun JsonValue?.asArr(): JsonValue.Arr? = this as? JsonValue.Arr

fun JsonValue?.asStringOrNull(): String? = when (this) {
	is JsonValue.Str -> value
	is JsonValue.Number -> raw
	is JsonValue.Bool -> value.toString()
	else -> null
}

fun JsonValue?.asLongOrNull(): Long? = when (this) {
	is JsonValue.Number -> raw.toLongOrNull() ?: value.toLong()
	is JsonValue.Str -> value.trim().toLongOrNull()
	else -> null
}

fun JsonValue?.asIntOrNull(): Int? = asLongOrNull()?.toInt()

fun JsonValue?.asFloatOrNull(): Float? = when (this) {
	is JsonValue.Number -> value.toFloat()
	is JsonValue.Str -> value.trim().toFloatOrNull()
	else -> null
}

fun JsonValue?.asDoubleOrNull(): Double? = when (this) {
	is JsonValue.Number -> value
	is JsonValue.Str -> value.trim().toDoubleOrNull()
	else -> null
}

fun JsonValue?.asBooleanOrNull(): Boolean? = when (this) {
	is JsonValue.Bool -> value
	is JsonValue.Number -> value != 0.0
	is JsonValue.Str -> when (value.trim().lowercase()) {
		"true", "1" -> true
		"false", "0" -> false
		else -> null
	}
	else -> null
}

fun JsonValue?.string(key: String): String? = asObj()?.get(key).asStringOrNull()

fun JsonValue?.long(key: String): Long? = asObj()?.get(key).asLongOrNull()

fun JsonValue?.int(key: String): Int? = asObj()?.get(key).asIntOrNull()

fun JsonValue?.float(key: String): Float? = asObj()?.get(key).asFloatOrNull()

fun JsonValue?.boolean(key: String): Boolean? = asObj()?.get(key).asBooleanOrNull()

fun JsonValue?.obj(key: String): JsonValue.Obj? = asObj()?.get(key).asObj()

fun JsonValue?.arr(key: String): JsonValue.Arr? = asObj()?.get(key).asArr()

// ---------------------------------------------------------------------------------------------
// Builders
// ---------------------------------------------------------------------------------------------

fun jsonObject(block: JsonValue.Obj.() -> Unit): JsonValue.Obj = JsonValue.Obj().apply(block)

fun jsonArray(block: JsonValue.Arr.() -> Unit = {}): JsonValue.Arr = JsonValue.Arr().apply(block)

fun JsonValue.Arr.add(value: JsonValue): JsonValue.Arr {
	items.add(value)
	return this
}

fun JsonValue.Arr.add(value: String): JsonValue.Arr = add(JsonValue.Str(value))

fun JsonValue.Arr.add(value: Long): JsonValue.Arr = add(JsonValue.Number(value.toDouble(), value.toString()))

fun JsonValue.Arr.add(value: Int): JsonValue.Arr = add(JsonValue.Number(value.toDouble(), value.toString()))

fun JsonValue.Arr.add(value: Boolean): JsonValue.Arr = add(JsonValue.Bool(value))

fun stringJson(value: String?): JsonValue = value?.let(JsonValue::Str) ?: JsonValue.Null

fun longJson(value: Long?): JsonValue =
	value?.let { JsonValue.Number(it.toDouble(), it.toString()) } ?: JsonValue.Null

fun intJson(value: Int?): JsonValue =
	value?.let { JsonValue.Number(it.toDouble(), it.toString()) } ?: JsonValue.Null

fun floatJson(value: Float?): JsonValue =
	value?.let { JsonValue.Number(it.toDouble(), formatDouble(it.toDouble())) } ?: JsonValue.Null

fun doubleJson(value: Double?): JsonValue =
	value?.let { JsonValue.Number(it, formatDouble(it)) } ?: JsonValue.Null

fun boolJson(value: Boolean?): JsonValue = value?.let(JsonValue::Bool) ?: JsonValue.Null

internal fun formatDouble(value: Double): String = when {
	value.isNaN() -> "NaN"
	value == Double.POSITIVE_INFINITY -> "Infinity"
	value == Double.NEGATIVE_INFINITY -> "-Infinity"
	value == value.toLong().toDouble() && kotlin.math.abs(value) < 9.0E15 -> value.toLong().toString()
	else -> {
		val text = value.toString()
		text
	}
}

// ---------------------------------------------------------------------------------------------
// Parser
// ---------------------------------------------------------------------------------------------

object Json {

	fun parse(text: String): JsonValue = Parser(text).parseDocument()

	fun parseOrNull(text: String): JsonValue? = runCatching { parse(text) }.getOrNull()

	fun stringify(value: JsonValue, pretty: Boolean = false): String = buildString {
		Writer(this, pretty).write(value)
	}

	private class Parser(private val text: String) {
		private var index = 0

		fun parseDocument(): JsonValue {
			skipWhitespace()
			val value = parseValue()
			skipWhitespace()
			if (index < text.length) fail("trailing characters at $index")
			return value
		}

		private fun parseValue(): JsonValue {
			skipWhitespace()
			if (index >= text.length) fail("unexpected end of input")
			return when (val c = text[index]) {
				'{' -> parseObject()
				'[' -> parseArray()
				'"' -> JsonValue.Str(parseString())
				't' -> parseLiteral("true", JsonValue.Bool(true))
				'f' -> parseLiteral("false", JsonValue.Bool(false))
				'n' -> parseLiteral("null", JsonValue.Null)
				'N' -> parseSpecial("NaN") { JsonValue.Number(Double.NaN, "NaN") }
				'I' -> parseSpecial("Infinity") { JsonValue.Number(Double.POSITIVE_INFINITY, "Infinity") }
				'-' -> if (text.startsWith("-Infinity", index)) {
					parseSpecial("-Infinity") { JsonValue.Number(Double.NEGATIVE_INFINITY, "-Infinity") }
				} else {
					parseNumber()
				}
				else -> if (c.isDigit()) parseNumber() else fail("unexpected character '$c' at $index")
			}
		}

		private fun parseLiteral(literal: String, value: JsonValue): JsonValue {
			if (!text.startsWith(literal, index)) fail("expected '$literal' at $index")
			index += literal.length
			return value
		}

		private fun parseSpecial(literal: String, producer: () -> JsonValue): JsonValue {
			parseLiteral(literal, JsonValue.Null)
			return producer()
		}

		private fun parseObject(): JsonValue.Obj {
			expect('{')
			val result = JsonValue.Obj()
			skipWhitespace()
			if (peek() == '}') {
				index++
				return result
			}
			while (true) {
				skipWhitespace()
				val key = parseString()
				skipWhitespace()
				expect(':')
				val value = parseValue()
				result.fields[key] = value
				skipWhitespace()
				when (peek()) {
					',' -> index++
					'}' -> {
						index++
						return result
					}
					else -> fail("expected ',' or '}' at $index")
				}
			}
		}

		private fun parseArray(): JsonValue.Arr {
			expect('[')
			val result = JsonValue.Arr()
			skipWhitespace()
			if (peek() == ']') {
				index++
				return result
			}
			while (true) {
				result.items.add(parseValue())
				skipWhitespace()
				when (peek()) {
					',' -> index++
					']' -> {
						index++
						return result
					}
					else -> fail("expected ',' or ']' at $index")
				}
			}
		}

		private fun parseString(): String {
			expect('"')
			val builder = StringBuilder()
			while (true) {
				if (index >= text.length) fail("unterminated string")
				val c = text[index++]
				when (c) {
					'"' -> return builder.toString()
					'\\' -> {
						if (index >= text.length) fail("unterminated escape")
						when (val escape = text[index++]) {
							'"' -> builder.append('"')
							'\\' -> builder.append('\\')
							'/' -> builder.append('/')
							'b' -> builder.append('\b')
							'f' -> builder.append('\u000C')
							'n' -> builder.append('\n')
							'r' -> builder.append('\r')
							't' -> builder.append('\t')
							'u' -> {
								if (index + 4 > text.length) fail("truncated unicode escape")
								val hex = text.substring(index, index + 4)
								index += 4
								builder.append(hex.toIntOrNull(16)?.toChar() ?: fail("bad unicode escape"))
							}
							else -> fail("bad escape '\\$escape'")
						}
					}
					else -> builder.append(c)
				}
			}
		}

		private fun parseNumber(): JsonValue {
			val start = index
			if (peek() == '-' || peek() == '+') index++
			while (index < text.length && (text[index].isDigit() || text[index] in ".eE+-")) {
				// a sign is only valid right after an exponent marker
				if (text[index] in "+-" && text[index - 1] !in "eE") break
				index++
			}
			val raw = text.substring(start, index)
			val value = raw.toDoubleOrNull() ?: fail("bad number '$raw'")
			return JsonValue.Number(value, raw)
		}

		private fun skipWhitespace() {
			while (index < text.length && text[index].isWhitespace()) index++
		}

		private fun peek(): Char = if (index < text.length) text[index] else '\u0000'

		private fun expect(c: Char) {
			skipWhitespace()
			if (peek() != c) fail("expected '$c' at $index")
			index++
		}

		private fun fail(message: String): Nothing = throw JsonParseException("$message (input length ${text.length})")
	}

	private class Writer(private val out: StringBuilder, private val pretty: Boolean) {
		private var level = 0

		fun write(value: JsonValue) {
			when (value) {
				is JsonValue.Obj -> writeObject(value)
				is JsonValue.Arr -> writeArray(value)
				is JsonValue.Str -> writeString(value.value)
				is JsonValue.Number -> out.append(value.raw.ifEmpty { formatDouble(value.value) })
				is JsonValue.Bool -> out.append(if (value.value) "true" else "false")
				JsonValue.Null -> out.append("null")
			}
		}

		private fun writeObject(value: JsonValue.Obj) {
			if (value.fields.isEmpty()) {
				out.append("{}")
				return
			}
			out.append('{')
			level++
			var first = true
			for ((key, child) in value.fields) {
				if (!first) out.append(',')
				first = false
				newline()
				writeString(key)
				out.append(':')
				if (pretty) out.append(' ')
				write(child)
			}
			level--
			newline()
			out.append('}')
		}

		private fun writeArray(value: JsonValue.Arr) {
			if (value.items.isEmpty()) {
				out.append("[]")
				return
			}
			out.append('[')
			level++
			value.items.forEachIndexed { i, child ->
				if (i > 0) out.append(',')
				newline()
				write(child)
			}
			level--
			newline()
			out.append(']')
		}

		private fun newline() {
			if (!pretty) return
			out.append('\n')
			repeat(level) { out.append('\t') }
		}

		private fun writeString(value: String) {
			out.append('"')
			for (c in value) {
				when (c) {
					'"' -> out.append("\\\"")
					'\\' -> out.append("\\\\")
					'\n' -> out.append("\\n")
					'\r' -> out.append("\\r")
					'\t' -> out.append("\\t")
					'\b' -> out.append("\\b")
					'\u000C' -> out.append("\\f")
					else -> if (c < ' ') {
						out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
					} else {
						out.append(c)
					}
				}
			}
			out.append('"')
		}
	}
}

class JsonParseException(message: String) : IllegalArgumentException(message)
