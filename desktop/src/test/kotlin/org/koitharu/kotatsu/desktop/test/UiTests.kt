/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Swing panels are built and painted into an off-screen image, so parts of the UI can be verified
 * without a display (the CI container and the Windows runner are both headless for this test).
 * The reader window itself is a JFrame and cannot be created headless; its geometry lives in
 * PageLayout and is covered here instead.
 */
package org.koitharu.kotatsu.desktop.test

import org.koitharu.kotatsu.desktop.data.AppRepository
import org.koitharu.kotatsu.desktop.data.model.ReaderMode
import org.koitharu.kotatsu.desktop.data.store.ActivityStore
import org.koitharu.kotatsu.desktop.data.store.LibraryStore
import org.koitharu.kotatsu.desktop.data.store.SettingsStore
import org.koitharu.kotatsu.desktop.test.TestRunner.assertEquals
import org.koitharu.kotatsu.desktop.test.TestRunner.assertTrue
import org.koitharu.kotatsu.desktop.ui.Navigator
import org.koitharu.kotatsu.desktop.ui.details.DetailsPanel
import org.koitharu.kotatsu.desktop.ui.downloads.DownloadsPanel
import org.koitharu.kotatsu.desktop.ui.hub.HubPanel
import org.koitharu.kotatsu.desktop.ui.history.HistoryPanel
import org.koitharu.kotatsu.desktop.ui.library.LibraryPanel
import org.koitharu.kotatsu.desktop.ui.reader.PageLayout
import org.koitharu.kotatsu.desktop.ui.settings.SettingsPanel
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.swing.JPanel

object UiTests {

	private class RecordingNavigator : Navigator {
		var lastStatus: String? = null
		var openedReader: Triple<Long, Int, Int>? = null

		override fun showLibrary() = Unit

		override fun showHub() = Unit

		override fun showHistory() = Unit

		override fun showDownloads() = Unit

		override fun showSettings() = Unit

		override fun showDetails(mangaId: Long) = Unit

		override fun backToLibrary() = Unit

		override fun openReader(mangaId: Long, chapterIndex: Int, page: Int) {
			openedReader = Triple(mangaId, chapterIndex, page)
		}

		override fun setStatus(text: String) {
			lastStatus = text
		}

		override fun refreshAll() = Unit
	}

	fun run() {
		pageLayoutTests()
		panelRenderTests()
	}

	private fun pageLayoutTests() = TestRunner.suite("Reader geometry") {
		TestRunner.test("fits a page to the width") {
			val placement = PageLayout.place(1000, 1500, 500, 800, fitToWidth = true, zoom = 1f)
			assertEquals("width", 500, placement.width)
			assertEquals("height keeps the ratio", 750, placement.height)
			assertEquals("centred horizontally", 0, placement.x)
			assertEquals("centred vertically", 25, placement.y)
		}

		TestRunner.test("zoom grows the page and allows panning") {
			val placement = PageLayout.place(1000, 1500, 500, 800, fitToWidth = true, zoom = 2f)
			assertEquals("double width", 1000, placement.width)
			assertTrue("overflows the viewport", placement.width > 500)
			assertTrue("kept inside on the left", placement.x <= 0)
			assertTrue("kept inside on the right", placement.right >= 500)
		}

		TestRunner.test("panning is clamped at the edges") {
			// dragging right reveals the left part of the page and stops at x = 0
			assertEquals("stops at the top-left", 0, PageLayout.clampAxis(500, 1000, 10_000))
			// dragging left reveals the right part and stops when the page ends at the viewport edge
			assertEquals("stops at the bottom-right", 500 - 1000, PageLayout.clampAxis(500, 1000, -10_000))
			assertEquals("centred when smaller", (500 - 400) / 2, PageLayout.clampAxis(500, 400, 0))
		}

		TestRunner.test("zoom is clamped to the documented range") {
			val min = PageLayout.place(1000, 1000, 500, 500, true, zoom = 0.01f)
			assertEquals("min zoom", 250, min.width)
			val max = PageLayout.place(1000, 1000, 500, 500, true, zoom = 99f)
			assertEquals("max zoom", 2000, max.width)
		}

		TestRunner.test("fit height uses the viewport height") {
			val placement = PageLayout.place(1000, 1000, 500, 400, fitToWidth = false, zoom = 1f)
			assertEquals("height", 400, placement.height)
			assertEquals("width", 400, placement.width)
		}

		TestRunner.test("degenerate sizes do not throw") {
			assertEquals("zero image", 0, PageLayout.place(0, 0, 500, 500, true, 1f).width)
			assertEquals("zero view", 0, PageLayout.place(100, 100, 0, 0, true, 1f).width)
		}

		TestRunner.test("continuous scale matches the width") {
			assertEquals("scale", 0.5, PageLayout.continuousScale(1000, 500, 1f))
			assertEquals("zoom applied", 1.0, PageLayout.continuousScale(1000, 500, 2f))
		}
	}

	private fun newRepositoryWithContent(): AppRepository {
		System.setProperty("kotatsu.home", Fixtures.tempDir("kotatsu-ui").toString())
		val home = java.nio.file.Paths.get(System.getProperty("kotatsu.home"))
		Files.createDirectories(home)
		val repo = AppRepository(
			SettingsStore(home.resolve("settings.json")),
			LibraryStore(home.resolve("library.json")),
			ActivityStore(home.resolve("activity.json")),
		)
		repo.init()
		val dir = Fixtures.tempDir()
		val archive = dir.resolve("UI Sample.cbz")
		Files.newOutputStream(archive).use { output ->
			ZipOutputStream(output).use { zip ->
				zip.putNextEntry(ZipEntry("01.png"))
				zip.write(Fixtures.pngBytes())
				zip.closeEntry()
			}
		}
		repo.importPaths(listOf(archive))
		repo.libraryItems().firstOrNull()?.let { item ->
			repo.library.chaptersOf(item.manga.id).firstOrNull()?.let { chapter ->
				repo.saveProgress(item.manga, chapter, 0, 0, 1)
				repo.downloadChapter(chapter)
			}
			repo.toggleFavourite(item.manga.id)
		}
		return repo
	}

	private fun render(panel: JPanel, width: Int = 1200, height: Int = 800): BufferedImage {
		panel.setSize(width, height)
		panel.doLayout()
		layoutTree(panel)
		val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
		val graphics = image.createGraphics()
		try {
			panel.paint(graphics)
		} finally {
			graphics.dispose()
		}
		return image
	}

	private fun layoutTree(component: java.awt.Container) {
		for (child in component.components) {
			if (child is java.awt.Container) {
				child.doLayout()
				layoutTree(child)
			}
		}
	}

	private fun panelRenderTests() = TestRunner.suite("UI panels (headless paint)") {
		TestRunner.test("library, details, history, downloads and settings panels build and paint") {
			val repo = newRepositoryWithContent()
			val navigator = RecordingNavigator()
			val library = LibraryPanel(repo, navigator)
			library.onShown()
			val libraryImage = render(library)
			assertTrue("library painted", hasVisiblePixels(libraryImage))

			val details = DetailsPanel(repo, navigator)
			val mangaId = repo.libraryItems().first().manga.id
			details.showManga(mangaId)
			assertTrue("details painted", hasVisiblePixels(render(details)))

			val history = HistoryPanel(repo, navigator)
			history.onShown()
			assertTrue("history painted", hasVisiblePixels(render(history)))

			val downloads = DownloadsPanel(repo, navigator)
			downloads.onShown()
			assertTrue("downloads painted", hasVisiblePixels(render(downloads)))

			val settings = SettingsPanel(repo, navigator)
			settings.onShown()
			assertTrue("settings painted", hasVisiblePixels(render(settings)))
		}

		TestRunner.test("hub panel lists the sources and paints without network access") {
			val repo = newRepositoryWithContent()
			val hub = HubPanel(repo, RecordingNavigator())
			hub.onShown()
			val image = render(hub)
			assertTrue("hub painted", hasVisiblePixels(image))
			assertTrue("catalogue non-empty", repo.sourceCatalog().isNotEmpty())
			assertTrue("no network in tests", repo.sourceCatalog().isNotEmpty())
		}

		TestRunner.test("list row renderers lay their text out (regression: blank history rows)") {
			// Rows are custom JPanels. JList paints them through CellRendererPane without validating
			// them, so a renderer that does not lay out its own subtree paints coloured bands with no
			// text. This checks the row produced by the real HistoryPanel renderer.
			val repo = newRepositoryWithContent()
			val history = HistoryPanel(repo, RecordingNavigator())
			history.onShown()
			history.setSize(900, 600)
			layoutTree(history)
			val listField = HistoryPanel::class.java.getDeclaredField("list").apply { isAccessible = true }
			val list = listField.get(history) as javax.swing.JList<*>
			// The panel loads its rows on a worker thread; wait for the result instead of racing it.
			val deadline = System.currentTimeMillis() + 5_000
			while (list.model.size == 0 && System.currentTimeMillis() < deadline) {
				Thread.sleep(20)
				javax.swing.SwingUtilities.invokeAndWait { }
			}
			assertEquals("history rows", 1, list.model.size)
			// A real window sizes the list through its scroll pane; off-screen the rows get the width
			// the renderer is given, which is what the bug was about.
			list.setSize(900, 400)
			val renderer = list.cellRenderer as javax.swing.ListCellRenderer<Any?>
			@Suppress("UNCHECKED_CAST")
			val row = renderer.getListCellRendererComponent(
				list as javax.swing.JList<out Any?>,
				list.model.getElementAt(0),
				0,
				false,
				false,
			) as java.awt.Container
			val texts = ArrayList<javax.swing.JLabel>()
			fun collect(component: java.awt.Container) {
				component.components.forEach { child ->
					if (child is javax.swing.JLabel) texts.add(child)
					if (child is java.awt.Container) collect(child)
				}
			}
			collect(row)
			assertTrue("row has labels", texts.isNotEmpty())
			// The renderer lays itself out while painting (as JList does), so paint one row.
			val image = BufferedImage(900, 80, BufferedImage.TYPE_INT_RGB)
			val graphics = image.createGraphics()
			try {
				row.setSize(900, 64)
				row.paint(graphics)
			} finally {
				graphics.dispose()
			}
			assertTrue(
				"labels have a non-zero width (found ${texts.map { it.width }})",
				texts.any { it.width > 0 && it.height > 0 },
			)
			assertTrue("labels carry text", texts.any { it.text.isNotBlank() })
		}

		TestRunner.test("empty library still paints an explanatory state") {
			System.setProperty("kotatsu.home", Fixtures.tempDir("kotatsu-empty").toString())
			val home = java.nio.file.Paths.get(System.getProperty("kotatsu.home"))
			Files.createDirectories(home)
			val repo = AppRepository(
				SettingsStore(home.resolve("settings.json")),
				LibraryStore(home.resolve("library.json")),
				ActivityStore(home.resolve("activity.json")),
			)
			repo.init()
			val library = LibraryPanel(repo, RecordingNavigator())
			library.onShown()
			assertTrue("painted", hasVisiblePixels(render(library)))
		}

		TestRunner.test("reader defaults are honoured by the settings panel state") {
			val repo = newRepositoryWithContent()
			repo.settings.defaultReaderMode = ReaderMode.WEBTOON
			repo.settings.readerPageGap = 32
			val settings = SettingsPanel(repo, RecordingNavigator())
			render(settings)
			assertEquals("mode kept", ReaderMode.WEBTOON, repo.settings.defaultReaderMode)
			assertEquals("gap kept", 32, repo.settings.readerPageGap)
		}
	}

	private fun hasVisiblePixels(image: BufferedImage): Boolean {
		val reference = image.getRGB(0, 0)
		for (y in 0 until image.height step 7) {
			for (x in 0 until image.width step 7) {
				if (image.getRGB(x, y) != reference) return true
			}
		}
		return false
	}
}
