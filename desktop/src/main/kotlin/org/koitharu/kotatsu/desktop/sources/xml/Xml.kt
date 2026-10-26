/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * A small, dependency-free XML parser, used for OPDS/Atom feeds (see sources/opds). It only does
 * what a feed needs: elements, attributes, text, CDATA and the five predefined entities. It is
 * deliberately forgiving — a malformed feed produces an empty tree instead of an exception, and the
 * caller reports "invalid response" with a useful message.
 */
package org.koitharu.kotatsu.desktop.sources.xml

class XmlNode(
	val name: String,
	val attributes: Map<String, String> = emptyMap(),
	val children: MutableList<XmlNode> = mutableListOf(),
) {

	val text = StringBuilder()

	/** Element name without namespace prefix. */
	val localName: String get() = name.substringAfter(':')

	fun attr(name: String): String? = attributes[name] ?: attributes.entries
		.firstOrNull { it.key.substringAfter(':').equals(name, ignoreCase = true) }?.value

	fun child(elementName: String): XmlNode? = children.firstOrNull { it.localName == elementName }

	fun childrenNamed(elementName: String): List<XmlNode> = children.filter { it.localName == elementName }

	fun descendants(elementName: String): List<XmlNode> {
		val result = ArrayList<XmlNode>()
		fun walk(node: XmlNode) {
			node.children.forEach { child ->
				if (child.localName == elementName) result.add(child)
				walk(child)
			}
		}
		walk(this)
		return result
	}

	/** Concatenated text of this element (text can be split by nested elements). */
	fun value(): String = buildString {
		append(text.toString().trim())
		children.forEach { child ->
			if (child.text.isNotBlank()) {
				if (isNotEmpty()) append(' ')
				append(child.text.toString().trim())
			}
		}
	}.trim()

	/** Text of the first descendant named [elementName]. */
	fun childText(elementName: String): String? = child(elementName)?.value()?.takeIf { it.isNotEmpty() }

	override fun toString(): String = "<$name ${attributes.size} attrs, ${children.size} children>"
}

object Xml {

	private val ENTITIES = mapOf(
		"amp" to "&",
		"lt" to "<",
		"gt" to ">",
		"quot" to "\"",
		"apos" to "'",
	)

	fun parse(xml: String): XmlNode = Parser(xml).parseDocument()

	/** Decodes the predefined entities plus numeric character references. */
	fun decodeEntities(value: String): String {
		if (!value.contains('&')) return value
		val output = StringBuilder(value.length)
		var index = 0
		while (index < value.length) {
			val char = value[index]
			if (char == '&') {
				val end = value.indexOf(';', index + 1)
				if (end in (index + 2) until minOf(index + 12, value.length)) {
					val entity = value.substring(index + 1, end)
					val decoded = ENTITIES[entity] ?: decodeNumeric(entity)
					if (decoded != null) {
						output.append(decoded)
						index = end + 1
						continue
					}
				}
			}
			output.append(char)
			index++
		}
		return output.toString()
	}

	private fun decodeNumeric(entity: String): String? {
		if (!entity.startsWith("#")) return null
		val code = when {
			entity.startsWith("#x", ignoreCase = true) -> entity.substring(2).toIntOrNull(16)
			else -> entity.substring(1).toIntOrNull()
		} ?: return null
		return runCatching { String(Character.toChars(code)) }.getOrNull()
	}

	private class Parser(private val input: String) {

		private var position = 0

		fun parseDocument(): XmlNode {
			val root = XmlNode("#document")
			skipProlog()
			while (position < input.length) {
				if (input[position] == '<') {
					if (input.startsWith("<!--", position)) {
						skipUntil("-->")
						continue
					}
					if (input.startsWith("<?", position)) {
						skipUntil("?>")
						continue
					}
					if (input.startsWith("<!", position)) {
						// DOCTYPE and other declarations: skip to the matching '>'.
						skipUntil(">")
						continue
					}
					val node = parseElement() ?: break
					root.children.add(node)
				} else {
					position++
				}
			}
			return root
		}

		private fun skipProlog() {
			// An XML declaration may contain '>' inside quotes; the simple scan is enough here.
			skipWhitespace()
			if (input.startsWith("<?xml", position)) skipUntil("?>")
			skipWhitespace()
			if (input.startsWith("<!DOCTYPE", position)) skipUntil(">")
		}

		private fun parseElement(): XmlNode? {
			if (position >= input.length || input[position] != '<') return null
			position++
			val name = readName() ?: return null
			val attributes = LinkedHashMap<String, String>()
			while (true) {
				skipWhitespace()
				if (position >= input.length) return XmlNode(name, attributes)
				val char = input[position]
				if (char == '/') {
					// Self-closing element.
					position++
					if (position < input.length && input[position] == '>') position++
					return XmlNode(name, attributes)
				}
				if (char == '>') {
					position++
					break
				}
				val attributeName = readName()
				if (attributeName == null) {
					position++
					continue
				}
				skipWhitespace()
				if (position < input.length && input[position] == '=') {
					position++
					skipWhitespace()
					attributes[attributeName] = readQuoted() ?: ""
				} else {
					attributes[attributeName] = ""
				}
			}
			val node = XmlNode(name, attributes)
			// Content
			while (position < input.length) {
				when {
					input.startsWith("</", position) -> {
						skipUntil(">")
						return node
					}
					input.startsWith("<!--", position) -> skipUntil("-->")
					input.startsWith("<![CDATA[", position) -> {
						val start = position + 9
						val end = input.indexOf("]]>", start)
						if (end < 0) {
							node.text.append(input.substring(start))
							position = input.length
						} else {
							node.text.append(input, start, end)
							position = end + 3
						}
					}
					input.startsWith("<?", position) -> skipUntil("?>")
					input[position] == '<' -> {
						val child = parseElement() ?: return node
						node.children.add(child)
					}
					else -> {
						val nextTag = input.indexOf('<', position)
						val end = if (nextTag < 0) input.length else nextTag
						node.text.append(decodeEntities(input.substring(position, end)))
						position = end
					}
				}
			}
			return node
		}

		private fun readName(): String? {
			if (position >= input.length) return null
			val start = position
			while (position < input.length) {
				val char = input[position]
				if (char.isLetterOrDigit() || char == ':' || char == '_' || char == '-' || char == '.') position++
				else break
			}
			return if (position > start) input.substring(start, position) else null
		}

		private fun readQuoted(): String? {
			if (position >= input.length) return null
			val quote = input[position]
			if (quote != '"' && quote != '\'') return null
			position++
			val end = input.indexOf(quote, position)
			if (end < 0) {
				val rest = input.substring(position)
				position = input.length
				return decodeEntities(rest)
			}
			val value = input.substring(position, end)
			position = end + 1
			return decodeEntities(value)
		}

		private fun skipWhitespace() {
			while (position < input.length && input[position].isWhitespace()) position++
		}

		private fun skipUntil(marker: String) {
			val end = input.indexOf(marker, position)
			position = if (end < 0) input.length else end + marker.length
		}
	}
}
