/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 */
package org.koitharu.kotatsu.desktop.test

import org.koitharu.kotatsu.desktop.core.i18n.AppLanguage
import org.koitharu.kotatsu.desktop.core.i18n.I18n
import org.koitharu.kotatsu.desktop.core.i18n.K
import org.koitharu.kotatsu.desktop.core.i18n.Strings
import org.koitharu.kotatsu.desktop.core.json.Json
import org.koitharu.kotatsu.desktop.core.json.JsonParseException
import org.koitharu.kotatsu.desktop.core.json.JsonValue
import org.koitharu.kotatsu.desktop.core.json.add
import org.koitharu.kotatsu.desktop.core.json.asArr
import org.koitharu.kotatsu.desktop.core.json.asFloatOrNull
import org.koitharu.kotatsu.desktop.core.json.asIntOrNull
import org.koitharu.kotatsu.desktop.core.json.asLongOrNull
import org.koitharu.kotatsu.desktop.core.json.asObj
import org.koitharu.kotatsu.desktop.core.json.asStringOrNull
import org.koitharu.kotatsu.desktop.core.json.jsonArray
import org.koitharu.kotatsu.desktop.core.json.jsonObject
import org.koitharu.kotatsu.desktop.core.json.longJson
import org.koitharu.kotatsu.desktop.core.json.stringJson
import org.koitharu.kotatsu.desktop.core.util.NaturalOrder
import org.koitharu.kotatsu.desktop.core.util.parseChapterNumber
import org.koitharu.kotatsu.desktop.data.model.Chapter
import org.koitharu.kotatsu.desktop.data.model.Manga
import org.koitharu.kotatsu.desktop.data.model.ReaderMode
import org.koitharu.kotatsu.desktop.data.model.stableId
import org.koitharu.kotatsu.desktop.data.model.toJson
import org.koitharu.kotatsu.desktop.data.model.chapterFromJson
import org.koitharu.kotatsu.desktop.data.model.mangaFromJson
import org.koitharu.kotatsu.desktop.reader.ReadingProgress
import org.koitharu.kotatsu.desktop.test.TestRunner.assertEquals
import org.koitharu.kotatsu.desktop.test.TestRunner.assertNotNull
import org.koitharu.kotatsu.desktop.test.TestRunner.assertThrows
import org.koitharu.kotatsu.desktop.test.TestRunner.assertTrue

object CoreTests {

	fun run() {
		jsonTests()
		naturalOrderTests()
		readingProgressTests()
		idAndModelTests()
		i18nTests()
	}

	private fun jsonTests() = TestRunner.suite("JSON") {
		TestRunner.test("parses objects, arrays and primitives") {
			val value = Json.parse("""{"a":1,"b":[true,false,null],"c":"x","d":{"e":2.5}}""")
			assertEquals("a", 1L, value.asObj()?.get("a").asLongOrNull())
			assertEquals("array size", 3, value.asObj()?.get("b").asArr()?.size)
			assertEquals("string", "x", value.asObj()?.get("c").asStringOrNull())
			assertEquals("nested float", 2.5f, value.asObj()?.get("d").asObj()?.get("e").asFloatOrNull())
		}

		TestRunner.test("round-trips unicode and escapes") {
			val original = jsonObject {
				put("title", stringJson("Capítulo 1 — \"ação\"\nlinha\t2 \\ fim"))
				put("id", longJson(9007199254740993L))
				put("tags", jsonArray { add(stringJson("ação")); add(stringJson("romance")) })
			}
			val text = Json.stringify(original)
			val parsed = Json.parse(text)
			assertEquals("unicode survives", "Capítulo 1 — \"ação\"\nlinha\t2 \\ fim", parsed.asObj()?.get("title").asStringOrNull())
			assertEquals("big id survives", 9007199254740993L, parsed.asObj()?.get("id").asLongOrNull())
			assertEquals("array element", "romance", parsed.asObj()?.get("tags").asArr()?.get(1).asStringOrNull())
		}

		TestRunner.test("accepts kotlinx.serialization non-finite numbers") {
			val value = Json.parse("""{"rating":NaN,"percent":-1.0}""")
			assertTrue("NaN tolerated", value.asObj()?.get("rating").asFloatOrNull()?.isNaN() == true)
			assertEquals("percent", -1f, value.asObj()?.get("percent").asFloatOrNull())
		}

		TestRunner.test("rejects malformed documents") {
			assertThrows("truncated object", JsonParseException::class.java) { Json.parse("""{"a":""") }
			assertThrows("trailing data", JsonParseException::class.java) { Json.parse("""{"a":1} extra""") }
		}

		TestRunner.test("writes pretty output that parses back") {
			val doc = jsonObject { put("list", jsonArray { add(stringJson("a")); add(stringJson("b")) }) }
			val pretty = Json.stringify(doc, pretty = true)
			assertTrue("has newlines", pretty.contains('\n'))
			assertEquals("still valid", "a", Json.parse(pretty).asObj()?.get("list").asArr()?.get(0).asStringOrNull())
		}
	}

	private fun naturalOrderTests() = TestRunner.suite("Natural ordering") {
		TestRunner.test("sorts numbered pages numerically") {
			val names = listOf("page10.jpg", "page2.jpg", "page1.jpg")
			val sorted = names.sortedWith(NaturalOrder)
			assertEquals("order", listOf("page1.jpg", "page2.jpg", "page10.jpg"), sorted)
		}

		TestRunner.test("handles zero padded and mixed names") {
			val names = listOf("002.png", "10.png", "001.png", "cover.png")
			val sorted = names.sortedWith(NaturalOrder)
			assertEquals("order", listOf("001.png", "002.png", "10.png", "cover.png"), sorted)
		}

		TestRunner.test("parses chapter numbers") {
			assertEquals("chapter 12.5", 12.5f, parseChapterNumber("Chapter 12.5"))
			assertEquals("capitulo 07", 7f, parseChapterNumber("Capítulo 07"))
			assertEquals("no number", 0f, parseChapterNumber("Extra"))
		}
	}

	private fun readingProgressTests() = TestRunner.suite("Reading progress") {
		TestRunner.test("counts the visible page as read") {
			assertEquals("page 0 of 4", 0.25f, ReadingProgress.chapterFraction(0, 4))
			assertEquals("last page", 1f, ReadingProgress.chapterFraction(3, 4))
			assertEquals("clamped", 1f, ReadingProgress.chapterFraction(9, 4))
		}

		TestRunner.test("computes whole-manga percent") {
			assertEquals("start", 0.25f, ReadingProgress.percentOf(0, 4, 0, 1))
			assertEquals("half", 0.375f, ReadingProgress.percentOf(1, 4, 0, 2))
			assertEquals("finished", 1f, ReadingProgress.percentOf(3, 4, 1, 2))
			assertEquals("no chapters", -1f, ReadingProgress.percentOf(0, 0, 0, 1))
		}

		TestRunner.test("completion threshold matches upstream") {
			assertTrue("1.0 completed", ReadingProgress.isCompleted(1f))
			assertTrue("0.99999 completed", ReadingProgress.isCompleted(0.99999f))
			assertTrue("0.99 not completed", !ReadingProgress.isCompleted(0.99f))
			assertEquals("string", "100%", ReadingProgress.percentToString(1f))
			assertEquals("invalid", "0%", ReadingProgress.percentToString(-1f))
		}

		TestRunner.test("estimates read chapters") {
			assertEquals("none", 0, ReadingProgress.estimatedReadChapters(-1f, 10))
			assertEquals("half", 5, ReadingProgress.estimatedReadChapters(0.5f, 10))
			assertEquals("complete", 10, ReadingProgress.estimatedReadChapters(1f, 10))
		}

		TestRunner.test("resumes at the saved page") {
			assertEquals("resume", 2 to 3, ReadingProgress.resumePosition(0.5f, 5, 3, 10))
			assertEquals("nothing saved", null, ReadingProgress.resumePosition(-1f, 5, 0, 10))
			assertEquals("start of manga", 0 to 0, ReadingProgress.resumePosition(0f, 5, 0, 10))
			assertEquals("nothing", null, ReadingProgress.resumePosition(-1f, 0, 0, 0))
		}

		TestRunner.test("maps scroll fractions") {
			assertEquals("middle", 0.5f, ReadingProgress.scrollFraction(500, 1000, 2000))
			assertEquals("offset", 500, ReadingProgress.scrollOffsetFor(0.5f, 1000, 2000))
			assertEquals("short page", 0, ReadingProgress.scrollOffsetFor(0.5f, 1000, 800))
		}
	}

	private fun idAndModelTests() = TestRunner.suite("Models and ids") {
		TestRunner.test("stableId is deterministic and positive") {
			val a = stableId("LOCAL", "C:/manga/one.cbz")
			val b = stableId("LOCAL", "C:/manga/one.cbz")
			val c = stableId("LOCAL", "C:/manga/two.cbz")
			assertEquals("same input", a, b)
			assertTrue("different input", a != c)
			assertTrue("positive", a > 0 && c > 0)
		}

		TestRunner.test("manga JSON round trip keeps upstream column names") {
			val manga = Manga(
				id = 42L,
				title = "Test",
				url = "C:/manga/test.cbz",
				authors = "Author",
				createdAt = 1000L,
			)
			val json = manga.toJson()
			assertTrue("column manga_id", json.has("manga_id"))
			assertTrue("column alt_title", json.has("alt_title"))
			assertEquals("round trip", manga, mangaFromJson(json))
		}

		TestRunner.test("chapter JSON round trip") {
			val chapter = Chapter(
				chapterId = 7L,
				mangaId = 42L,
				title = "Chapter 1",
				url = "C:/manga/test.cbz",
				index = 0,
				pagesCount = 12,
				isDownloaded = true,
			)
			assertEquals("round trip", chapter, chapterFromJson(chapter.toJson()))
		}

		TestRunner.test("reader mode ids match the Android app") {
			assertEquals("STANDARD", 1, ReaderMode.STANDARD.id)
			assertEquals("WEBTOON", 2, ReaderMode.WEBTOON.id)
			assertEquals("REVERSED", 3, ReaderMode.REVERSED.id)
			assertEquals("VERTICAL", 4, ReaderMode.VERTICAL.id)
			assertEquals("lookup", ReaderMode.WEBTOON, ReaderMode.valueOf(2))
			assertEquals("name lookup", ReaderMode.REVERSED, ReaderMode.fromName("reversed"))
		}

		TestRunner.test("manga JSON tolerates unknown fields and missing ones") {
			val json = Json.parse(
				"""{"manga_id":5,"title":"T","url":"u","unknown_field":{"a":1},"rating":-1.0}""",
			)
			val manga = assertNotNull("parsed", mangaFromJson(json))
			assertEquals("title", "T", manga.title)
			assertEquals("public url falls back", "u", manga.publicUrl)
			assertEquals("rating", -1f, manga.rating)
		}
	}

	private fun i18nTests() = TestRunner.suite("Translations") {
		TestRunner.test("every declared key has both translations") {
			val fields = K::class.java.declaredFields
				.filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
				.filter { it.type == String::class.java }
			assertTrue("keys discovered", fields.size > 50)
			fields.forEach { field ->
				field.isAccessible = true
				val key = field.get(null) as String
				val row = Strings.TRANSLATIONS[key]
				assertNotNull("missing translation for $key", row)
				assertTrue("English text for $key", row!![AppLanguage.ENGLISH.ordinal].isNotBlank())
				assertTrue("Portuguese text for $key", row[AppLanguage.PORTUGUESE_BR.ordinal].isNotBlank())
			}
		}

		TestRunner.test("placeholder counts match between languages") {
			Strings.TRANSLATIONS.forEach { (key, row) ->
				val english = row[AppLanguage.ENGLISH.ordinal].count { it == '%' }
				val portuguese = row[AppLanguage.PORTUGUESE_BR.ordinal].count { it == '%' }
				assertEquals("placeholders of $key", english, portuguese)
			}
		}

		TestRunner.test("language switching changes the text") {
			I18n.setLanguage(AppLanguage.ENGLISH)
			assertEquals("english", "Library", I18n[K.NAV_LIBRARY])
			I18n.setLanguage(AppLanguage.PORTUGUESE_BR)
			assertEquals("portuguese", "Biblioteca", I18n[K.NAV_LIBRARY])
			I18n.setLanguage(AppLanguage.ENGLISH)
		}

		TestRunner.test("unknown key falls back to itself") {
			assertEquals("fallback", "no.such.key", I18n["no.such.key"])
		}
	}

	@Suppress("unused")
	private fun unusedJsonImport(): JsonValue? = null
}
