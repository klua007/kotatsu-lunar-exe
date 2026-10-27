/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: replaces the Android "explore/sources" screens with a Swing Hub.
 * Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * The Hub is the entry point to online content:
 *
 *   left  – the catalogue of sources: enabled, pinned, last availability check, mirror / removal
 *   right – search results (or the "popular now" feed), with a "load more" page step
 *
 * Every control here does something: there are no placeholder buttons. Sources that this build
 * cannot run are listed with the reason (docs/SOURCES.md) instead of being hidden.
 */
package org.koitharu.kotatsu.desktop.ui.hub

import org.koitharu.kotatsu.desktop.core.i18n.K
import org.koitharu.kotatsu.desktop.core.i18n.tr
import org.koitharu.kotatsu.desktop.data.AppRepository
import org.koitharu.kotatsu.desktop.data.local.ImageDecoder
import org.koitharu.kotatsu.desktop.sources.RemoteManga
import org.koitharu.kotatsu.desktop.sources.SearchFilters
import org.koitharu.kotatsu.desktop.sources.SourceEntry
import org.koitharu.kotatsu.desktop.sources.SourceKind
import org.koitharu.kotatsu.desktop.sources.SourceOutcome
import org.koitharu.kotatsu.desktop.sources.mangadex.MangaDexSource
import org.koitharu.kotatsu.desktop.ui.Async
import org.koitharu.kotatsu.desktop.ui.Navigator
import org.koitharu.kotatsu.desktop.ui.Screen
import org.koitharu.kotatsu.desktop.ui.Theme
import org.koitharu.kotatsu.desktop.ui.Ui
import org.koitharu.kotatsu.desktop.ui.WrapLayout
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Insets
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants

class HubPanel(
	private val repo: AppRepository,
	private val navigator: Navigator,
) : JPanel(BorderLayout()), Screen {

	private val queryField = Ui.textField(tr(K.HUB_SEARCH_HINT)) { search() }
	private val tagsField = Ui.textField(tr(K.HUB_FILTER_TAGS))
	private val nsfwCheck = Ui.checkBox(tr(K.HUB_FILTER_NSFW))
	private val stateBox = Ui.comboBox(
		listOf(
			tr(K.HUB_STATE_ANY),
			tr(K.HUB_STATE_ONGOING),
			tr(K.HUB_STATE_COMPLETED),
			tr(K.HUB_STATE_HIATUS),
			tr(K.HUB_STATE_CANCELLED),
		),
	)
	private val statusLabel = Ui.label("", Theme.fontSmall, Theme.onSurfaceVariant)
	private val failuresLabel = Ui.label("", Theme.fontSmall, Theme.error)
	private val sourcesColumn = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		background = Theme.background
	}
	private val resultsGrid = JPanel(WrapLayout(FlowLayout.LEFT, 12, 12)).apply {
		background = Theme.background
	}
	private val resultsScroll = JScrollPane(resultsGrid).apply {
		background = Theme.background
		border = BorderFactory.createEmptyBorder()
		verticalScrollBar.unitIncrement = 24
		horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
	}
	private val loadMoreButton = Ui.button(tr(K.HUB_LOAD_MORE)) { loadMore() }.apply { isVisible = false }
	private val emptyState = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		background = Theme.background
		border = BorderFactory.createEmptyBorder(40, 20, 20, 20)
	}

	private var results: List<RemoteManga> = emptyList()
	private var nextPage: Int = 0
	private var lastQuery: String = ""
	private var searchInFlight = false
	private var autoLoaded = false
	private var hasMorePages: Boolean = false

	init {
		background = Theme.background
		add(createHeader(), BorderLayout.NORTH)
		val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createSourcesPane(), createResultsPane())
		split.background = Theme.background
		split.border = BorderFactory.createEmptyBorder()
		split.dividerLocation = 340
		split.resizeWeight = 0.28
		add(split, BorderLayout.CENTER)
		add(createFooter(), BorderLayout.SOUTH)
		clearResults(tr(K.HUB_EMPTY_HINT))
	}

	// --- Layout --------------------------------------------------------------------------------

	private fun fieldCaption(text: String): JLabel = Ui.label(text, Theme.fontSmall, Theme.onSurfaceVariant)

	/** A label that wraps at the width of the sources column instead of being clipped. */
	private fun wrappedLabel(text: String, colour: Color, width: Int): JLabel = JLabel(
		"<html><body style='width:" + width + "px'>" + escapeHtml(text) + "</body></html>",
	).apply {
		font = Theme.fontSmall
		foreground = colour
	}


	private fun createHeader(): JPanel = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		background = Theme.background
		border = BorderFactory.createEmptyBorder(14, 16, 6, 16)
		add(
			JPanel(BorderLayout()).apply {
				background = Theme.background
				add(Ui.title(tr(K.HUB_TITLE)), BorderLayout.WEST)
				add(statusLabel, BorderLayout.EAST)
				maximumSize = Dimension(Int.MAX_VALUE, 34)
				alignmentX = LEFT_ALIGNMENT
			},
		)
		add(Box.createVerticalStrut(8))
		add(
			JPanel(WrapLayout(FlowLayout.LEFT, 8, 4)).apply {
				background = Theme.background
				alignmentX = LEFT_ALIGNMENT
				add(fieldCaption(tr(K.HUB_SEARCH_HINT)))
				queryField.preferredSize = Dimension(260, 30)
				add(queryField)
				add(Ui.accentButton(tr(K.HUB_SEARCH_BUTTON)) { search() })
				add(Ui.button(tr(K.HUB_BROWSE)) { browse() })
			},
		)
		add(Box.createVerticalStrut(4))
		add(
			JPanel(WrapLayout(FlowLayout.LEFT, 8, 4)).apply {
				background = Theme.background
				alignmentX = LEFT_ALIGNMENT
				add(fieldCaption(tr(K.HUB_FILTER_TAGS)))
				tagsField.preferredSize = Dimension(150, 30)
				add(tagsField)
				add(fieldCaption(tr(K.HUB_FILTER_STATE)))
				stateBox.preferredSize = Dimension(140, 30)
				add(stateBox)
				add(Ui.button(tr(K.HUB_FILTER_APPLY)) { search() })
				add(Ui.button(tr(K.HUB_FILTER_CLEAR)) {
					tagsField.text = ""
					nsfwCheck.isSelected = false
					stateBox.selectedIndex = 0
					search()
				})
				add(nsfwCheck)
			},
		)
	}

	private fun createSourcesPane(): JPanel = JPanel(BorderLayout()).apply {
		background = Theme.background
		border = BorderFactory.createEmptyBorder(0, 16, 8, 4)
		add(Ui.subtitle(tr(K.HUB_SOURCES)), BorderLayout.NORTH)
		val scroll = JScrollPane(sourcesColumn)
		scroll.background = Theme.background
		scroll.border = BorderFactory.createEmptyBorder()
		scroll.verticalScrollBar.unitIncrement = 24
		scroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
		add(scroll, BorderLayout.CENTER)
		add(
			JPanel(WrapLayout(FlowLayout.LEFT, 6, 4)).apply {
				background = Theme.background
				add(Ui.button(tr(K.HUB_ADD_OPDS)) { addOpdsCatalog() })
				add(Ui.button(tr(K.HUB_CHECK_ALL)) { checkAll() })
			},
			BorderLayout.SOUTH,
		)
	}

	private fun createResultsPane(): JPanel = JPanel(BorderLayout()).apply {
		background = Theme.background
		border = BorderFactory.createEmptyBorder(0, 0, 8, 12)
		add(
			JPanel(BorderLayout()).apply {
				background = Theme.background
				add(Ui.subtitle(tr(K.HUB_RESULTS)), BorderLayout.WEST)
				add(
					JPanel(WrapLayout(FlowLayout.RIGHT, 6, 0)).apply {
						background = Theme.background
						add(loadMoreButton)
					},
					BorderLayout.EAST,
				)
			},
			BorderLayout.NORTH,
		)
		add(resultsScroll, BorderLayout.CENTER)
	}

	private fun createFooter(): JPanel = JPanel(BorderLayout()).apply {
		background = Theme.background
		border = BorderFactory.createEmptyBorder(2, 16, 8, 16)
		add(failuresLabel, BorderLayout.WEST)
	}

	// --- Screen --------------------------------------------------------------------------------

	override fun onShown() {
		refreshSources()
		if (results.isEmpty() && !autoLoaded && !isTestMode()) {
			autoLoaded = true
			browse()
		}
	}

	/** In the automated tests the Hub is painted but must never open a socket. */
	private fun isTestMode(): Boolean = System.getProperty("kotatsu.test") == "1"

	fun focusSearch() {
		queryField.requestFocusInWindow()
	}

	// --- Sources -------------------------------------------------------------------------------

	private fun refreshSources() {
		sourcesColumn.removeAll()
		val entries = repo.sourceCatalog()
		entries.filter { it.descriptor.kind != SourceKind.LOCAL }.forEach { entry ->
			sourcesColumn.add(createSourceCard(entry))
			sourcesColumn.add(Box.createVerticalStrut(6))
		}
		sourcesColumn.add(createLocalCard())
		sourcesColumn.revalidate()
		sourcesColumn.repaint()
	}

	private fun createSourceCard(entry: SourceEntry): JPanel = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		background = Theme.surface
		border = BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(Theme.border),
			BorderFactory.createEmptyBorder(8, 10, 8, 10),
		)
		alignmentX = LEFT_ALIGNMENT
		val unavailable = !entry.isAvailable
		val title = JLabel(entry.descriptor.title + if (entry.pinned) "  ★" else "").apply {
			font = if (entry.pinned) Theme.fontH2 else Theme.baseFont.deriveFont(Font.BOLD)
			foreground = if (unavailable) Theme.onSurfaceVariant else Theme.onBackground
			alignmentX = LEFT_ALIGNMENT
		}
		add(title)
		add(
			wrappedLabel(
				buildString {
					append(entry.descriptor.language)
					append(" · ")
					append(if (entry.enabled) tr(K.HUB_SOURCE_ENABLED) else tr(K.HUB_SOURCE_DISABLED))
					append(" · ")
					append(availabilityText(entry))
				},
				if (entry.availability?.available == false) Theme.error else Theme.onSurfaceVariant,
				SOURCE_CARD_TEXT_WIDTH,
			).apply { alignmentX = LEFT_ALIGNMENT },
		)
		if (unavailable) {
			add(
				wrappedLabel(
					tr(K.HUB_SOURCE_UNAVAILABLE_REASON).format(tr(entry.descriptor.unavailableReason.orEmpty())),
					Theme.onSurfaceVariant,
					SOURCE_CARD_TEXT_WIDTH,
				).apply { alignmentX = LEFT_ALIGNMENT },
			)
			return@apply
		}
		add(
			JPanel(WrapLayout(FlowLayout.LEFT, 4, 4)).apply {
				background = Theme.surface
				alignmentX = LEFT_ALIGNMENT
				maximumSize = Dimension(Int.MAX_VALUE, 96)
				val toggle = Ui.button(
					if (entry.enabled) tr(K.HUB_SOURCE_DISABLED) else tr(K.HUB_SOURCE_ENABLED),
				) {
					repo.registry.setEnabled(entry.descriptor.id, !entry.enabled)
					refreshSources()
				}.apply {
					font = Theme.fontSmall
					margin = Insets(4, 2, 4, 2)
				}
				add(toggle)
				add(
					Ui.button(if (entry.pinned) tr(K.HUB_UNPIN) else tr(K.HUB_PIN)) {
						repo.registry.setPinned(entry.descriptor.id, !entry.pinned)
						refreshSources()
					}.apply { font = Theme.fontSmall; margin = Insets(4, 2, 4, 2) },
				)
				add(
					Ui.button(tr(K.HUB_CHECK)) {
						checkSource(entry.descriptor.id)
					}.apply { font = Theme.fontSmall; margin = Insets(4, 2, 4, 2) },
				)
				if (entry.descriptor.kind == SourceKind.NATIVE && entry.descriptor.id == "MANGADEX") {
					add(
						Ui.button(tr(K.HUB_MIRROR)) { editMirror(entry) }
							.apply { font = Theme.fontSmall; margin = Insets(4, 2, 4, 2) },
					)
				}
				if (repo.registry.catalogs().any { it.id == entry.descriptor.id }) {
					add(
						Ui.button(tr(K.HUB_REMOVE_CATALOG)) {
							if (Ui.confirm(this@HubPanel, tr(K.HUB_REMOVE_CATALOG_CONFIRM))) {
								repo.registry.removeOpdsCatalog(entry.descriptor.id)
								refreshSources()
							}
						}.apply { font = Theme.fontSmall; margin = Insets(4, 2, 4, 2) },
					)
				}
			},
		)
	}

	private fun availabilityText(entry: SourceEntry): String {
		val availability = entry.availability ?: return tr(K.HUB_UNKNOWN)
		val stamp = SimpleDateFormat("HH:mm").format(Date(availability.checkedAt))
		val latency = tr(K.HUB_LATENCY).format(availability.latencyMs)
		return if (availability.available) {
			tr(K.HUB_AVAILABLE) + " · " + latency
		} else {
			val reason = availability.failure?.message() ?: tr(K.HUB_UNAVAILABLE)
			tr(K.HUB_UNAVAILABLE) + " · " + stamp + " · " + reason
		}
	}

	private fun createLocalCard(): JPanel = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		background = Theme.background
		border = BorderFactory.createEmptyBorder(8, 2, 8, 2)
		alignmentX = LEFT_ALIGNMENT
		add(
			Ui.label(tr(K.HUB_LOCAL_CARD), Theme.baseFont.deriveFont(Font.BOLD), Theme.onBackground)
				.apply { alignmentX = LEFT_ALIGNMENT },
		)
		add(
			wrappedLabel(tr(K.HUB_LOCAL_CARD_HINT), Theme.onSurfaceVariant, SOURCE_CARD_TEXT_WIDTH)
				.apply { alignmentX = LEFT_ALIGNMENT },
		)
		add(
			JPanel(WrapLayout(FlowLayout.LEFT, 6, 4)).apply {
				background = Theme.background
				alignmentX = LEFT_ALIGNMENT
				add(Ui.button(tr(K.LOCAL_IMPORT_FILES)) {
					navigator.showLibrary()
				}.apply { font = Theme.fontSmall })
			},
		)
	}

	private fun checkSource(id: String) {
		val title = repo.registry.get(id)?.descriptor?.title ?: id
		statusLabel.text = tr(K.APP_LOADING)
		Async.compute({ repo.registry.checkAvailability(id) }) { outcome ->
			statusLabel.text = tr(K.APP_READY)
			refreshSources()
			// An unreachable source is not an error of the application: the card shows why, and a
			// dialog spells the reason out instead of leaving a silent red badge.
			if (outcome is SourceOutcome.Failure) {
				Ui.showError(this, tr(K.HUB_CHECK_FAILED).format(title, outcome.failure.message()))
			} else if (outcome is SourceOutcome.Success && !outcome.value.available) {
				Ui.showError(
					this,
					tr(K.HUB_CHECK_FAILED).format(title, outcome.value.failure?.message() ?: tr(K.HUB_UNAVAILABLE)),
				)
			}
		}
	}

	private fun checkAll() {
		val ids = repo.registry.sources()
			.filter { repo.registry.isEnabled(it.descriptor) }
			.map { it.descriptor.id }
		if (ids.isEmpty()) {
			Ui.showInfo(this, tr(K.HUB_NO_SOURCES))
			return
		}
		statusLabel.text = tr(K.APP_LOADING)
		Async.compute({ ids.associateWith { repo.registry.checkAvailability(it) } }) { results ->
			statusLabel.text = tr(K.APP_READY)
			refreshSources()
			val down = results.mapNotNull { (id, outcome) ->
				val failure = when (outcome) {
					is SourceOutcome.Failure -> outcome.failure
					is SourceOutcome.Success -> outcome.value.takeIf { !it.available }?.failure
				} ?: return@mapNotNull null
				"${repo.registry.get(id)?.descriptor?.title ?: id}: ${failure.message()}"
			}
			if (down.isNotEmpty()) {
				failuresLabel.text = tr(K.HUB_FAILURES).format(down.joinToString("; "))
			}
		}
	}

	private fun editMirror(entry: SourceEntry) {
		val current = repo.registry.baseUrlOverride(entry.descriptor.id).orEmpty()
		val input = JOptionPane.showInputDialog(
			this,
			tr(K.HUB_MIRROR_HINT),
			current,
		) ?: return
		val trimmed = input.trim()
		if (trimmed.isNotEmpty() && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
			Ui.showError(this, tr(K.SOURCES_ERROR_INVALID))
			return
		}
		repo.registry.setBaseUrlOverride(entry.descriptor.id, trimmed.ifEmpty { null })
		Ui.showInfo(
			this,
			if (trimmed.isEmpty()) tr(K.HUB_MIRROR_RESET) else tr(K.HUB_MIRROR) + ": " + trimmed,
		)
		refreshSources()
	}

	private fun addOpdsCatalog() {
		val nameField = JTextField(tr(K.HUB_OPDS_NAME_HINT))
		val urlField = JTextField("http://localhost:25600/opds/v1.2/catalog")
		val panel = JPanel().apply {
			layout = BoxLayout(this, BoxLayout.Y_AXIS)
			add(Ui.label(tr(K.HUB_ADD_OPDS_NAME), Theme.fontSmall, Theme.onSurfaceVariant))
			add(nameField)
			add(Box.createVerticalStrut(6))
			add(Ui.label(tr(K.HUB_ADD_OPDS_URL), Theme.fontSmall, Theme.onSurfaceVariant))
			add(urlField)
			add(Box.createVerticalStrut(6))
			add(Ui.label(tr(K.HUB_ADD_OPDS_HINT), Theme.fontSmall, Theme.onSurfaceVariant))
		}
		val result = JOptionPane.showConfirmDialog(
			this,
			panel,
			tr(K.HUB_ADD_OPDS),
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE,
		)
		if (result != JOptionPane.OK_OPTION) return
		when (val outcome = repo.registry.addOpdsCatalog(nameField.text, urlField.text)) {
			is SourceOutcome.Success -> {
				refreshSources()
				Ui.showInfo(this, tr(K.HUB_ADD_OPDS) + ": " + outcome.value.title)
				checkSource(outcome.value.descriptor.id)
			}

			is SourceOutcome.Failure -> Ui.showError(this, outcome.failure.message())
		}
	}

	// --- Search --------------------------------------------------------------------------------

	private fun currentFilters(): SearchFilters = SearchFilters(
		tags = tagsField.text.split(',').map { it.trim() }.filter { it.isNotEmpty() },
		states = selectedStates(),
		includeNsfw = nsfwCheck.isSelected,
	)

	/**
	 * Publication status chosen in the Hub, mapped to the values the source understands. The
	 * MangaDex API wants `status[]=ongoing|completed|hiatus|cancelled`; sending a plain string
	 * used to be rejected with HTTP 400, which is why the mapping lives next to the source's own
	 * list ([MangaDexSource.STATES]) instead of being invented here.
	 */
	private fun selectedStates(): List<String> {
		val index = stateBox.selectedIndex
		val states = MangaDexSource.STATES
		return if (index <= 0 || index > states.size) emptyList() else listOf(states[index - 1])
	}

	private fun search() {
		runSearch(queryField.text.trim(), reset = true)
	}

	private fun browse() {
		runSearch("", reset = true)
	}

	private fun loadMore() {
		if (searchInFlight) return
		runSearch(lastQuery, reset = false)
	}

	private fun runSearch(query: String, reset: Boolean) {
		if (searchInFlight) return
		val enabled = repo.registry.enabledSources()
		if (enabled.isEmpty()) {
			clearResults(tr(K.HUB_NO_SOURCES))
			return
		}
		searchInFlight = true
		val page = if (reset) 0 else nextPage
		lastQuery = query
		statusLabel.text = tr(K.HUB_SEARCHING)
		failuresLabel.text = ""
		loadMoreButton.isEnabled = false
		var filters = SearchFilters()
		Async.compute(
			work = {
				filters = currentFilters()
				repo.searchRemote(query, page, filters)
			},
			onError = { error ->
				searchInFlight = false
				loadMoreButton.isEnabled = true
				statusLabel.text = tr(K.APP_READY)
				Ui.showError(this, tr(K.ERROR_UNEXPECTED).format(error.message ?: error.javaClass.simpleName))
			},
		) { outcome ->
			searchInFlight = false
			loadMoreButton.isEnabled = true
			val items = outcome.items
			// Only advance the page cursor when the request really produced a page: otherwise a
			// failed "load more" would silently skip results.
			if (items.isNotEmpty()) nextPage = page + 1
			results = if (reset) items else (results + items).distinctBy { it.sourceId to it.remoteId }
			hasMorePages = items.isNotEmpty()
			statusLabel.text = tr(K.HUB_RESULTS_COUNT).format(results.size)
			renderResults()
			loadMoreButton.isVisible = hasMorePages
			failuresLabel.text = buildString {
				if (!hasMorePages && !reset && items.isEmpty()) append(tr(K.HUB_NO_MORE_PAGES))
				if (outcome.failures.isNotEmpty()) {
					if (isNotEmpty()) append(" · ")
					append(
						tr(K.HUB_FAILURES).format(
							outcome.failures.joinToString("; ") { (name, failure) -> "$name: ${failure.message()}" },
						),
					)
				}
			}
		}
	}

	/**
	 * Replaces the results with an explanatory empty state. A new installation legitimately has an
	 * empty library and nothing to search yet, so the state always offers the two ways to add
	 * content: importing local files, or enabling a source on the left.
	 */
	private fun clearResults(message: String) {
		results = emptyList()
		hasMorePages = false
		loadMoreButton.isVisible = false
		resultsGrid.removeAll()
		resultsGrid.layout = BorderLayout()
		emptyState.removeAll()
		emptyState.add(
			JLabel("<html><body style='width:360px'>$message</body></html>").apply {
				font = Theme.fontSmall
				foreground = Theme.onSurfaceVariant
				alignmentX = CENTER_ALIGNMENT
			},
		)
		emptyState.add(Ui.vgap(12))
		emptyState.add(
			Ui.accentButton(tr(K.MENU_FILE_OPEN_ARCHIVE)) { navigator.importLocalFiles() }
				.apply { alignmentX = CENTER_ALIGNMENT },
		)
		emptyState.add(Ui.vgap(6))
		emptyState.add(Ui.button(tr(K.HUB_SOURCES)) { refreshSources() }.apply { alignmentX = CENTER_ALIGNMENT })
		resultsGrid.add(emptyState, BorderLayout.NORTH)
		resultsGrid.revalidate()
		resultsGrid.repaint()
		statusLabel.text = ""
	}

	private fun renderResults() {
		resultsGrid.removeAll()
		if (results.isEmpty()) {
			clearResults(if (repo.registry.enabledSources().isEmpty()) tr(K.HUB_NO_SOURCES) else tr(K.HUB_NO_RESULTS))
			return
		}
		resultsGrid.layout = WrapLayout(FlowLayout.LEFT, 12, 12)
		results.forEach { remote -> resultsGrid.add(ResultCard(remote)) }
		resultsGrid.revalidate()
		resultsGrid.repaint()
	}

	private fun open(remote: RemoteManga) {
		val existing = repo.findInLibrary(remote)
		if (existing != null) {
			navigator.showDetails(existing.id)
			return
		}
		statusLabel.text = tr(K.APP_LOADING)
		Async.compute({ repo.addFromSource(remote) }) { outcome ->
			statusLabel.text = tr(K.APP_READY)
			when (outcome) {
				is SourceOutcome.Success -> {
					renderResults()
					navigator.refreshAll()
					navigator.showDetails(outcome.value.id)
				}

				is SourceOutcome.Failure -> Ui.showError(this, outcome.failure.message())
			}
		}
	}

	// --- Result card ---------------------------------------------------------------------------

	private inner class ResultCard(private val remote: RemoteManga) : JPanel() {

		private var cover: BufferedImage? = null
		private val coverLabel = JLabel().apply {
			preferredSize = Dimension(150, 200)
			horizontalAlignment = SwingConstants.CENTER
			background = Theme.surfaceVariant
			isOpaque = true
		}

		init {
			layout = BoxLayout(this, BoxLayout.Y_AXIS)
			background = Theme.surface
			border = BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(Theme.border),
				BorderFactory.createEmptyBorder(8, 8, 8, 8),
			)
			preferredSize = Dimension(170, 282)
			maximumSize = Dimension(170, 282)
			cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
			toolTipText = buildString {
				append("<html><b>").append(escape(remote.title)).append("</b>")
				remote.authors.take(2).forEach { append("<br>").append(escape(it)) }
				if (remote.tags.isNotEmpty()) {
					append("<br>").append(escape(remote.tags.take(4).joinToString(", ")))
				}
				append("<br>").append(escape(repo.sourceForId(remote.sourceId)?.descriptor?.title.orEmpty()))
				append("</html>")
			}
			add(coverLabel.apply { alignmentX = LEFT_ALIGNMENT })
			add(Box.createVerticalStrut(6))
			add(
				JLabel(remote.title.take(46)).apply {
					font = Theme.baseFont.deriveFont(Font.BOLD)
					foreground = Theme.onBackground
					alignmentX = LEFT_ALIGNMENT
				},
			)
			add(
				JLabel(repo.findInLibrary(remote)?.let { tr(K.HUB_IN_LIBRARY) } ?: tr(K.HUB_ADD)).apply {
					font = Theme.fontSmall
					foreground = Theme.accent
					alignmentX = LEFT_ALIGNMENT
				},
			)
			addMouseListener(object : java.awt.event.MouseAdapter() {
				override fun mouseClicked(e: java.awt.event.MouseEvent) = open(remote)
			})
			loadCover()
		}

		private fun escape(text: String): String = text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")

		private fun loadCover() {
			val url = remote.coverUrl ?: return
			Async.compute({
				hubCoverCache[url] ?: repo.fetchRemoteImage(url)?.let(ImageDecoder::decode)?.also { image ->
					hubCoverCache[url] = image
				}
			}) { image ->
				if (image != null) {
					cover = image
					coverLabel.icon = javax.swing.ImageIcon(scale(image, 150, 200))
					coverLabel.text = null
					revalidate()
					repaint()
				}
			}
		}

		private fun scale(image: BufferedImage, maxWidth: Int, maxHeight: Int): BufferedImage {
			val ratio = minOf(maxWidth.toDouble() / image.width, maxHeight.toDouble() / image.height)
			val width = (image.width * ratio).toInt().coerceAtLeast(1)
			val height = (image.height * ratio).toInt().coerceAtLeast(1)
			val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
			val graphics = result.createGraphics()
			graphics.setRenderingHint(
				java.awt.RenderingHints.KEY_INTERPOLATION,
				java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
			)
			graphics.drawImage(image, 0, 0, width, height, null)
			graphics.dispose()
			return result
		}

		override fun paintComponent(g: java.awt.Graphics) {
			super.paintComponent(g)
			if (cover == null) {
				coverLabel.text = remote.title.take(20)
				coverLabel.foreground = Theme.onSurfaceVariant
				coverLabel.font = Theme.fontSmall
			}
			g.color = with(Theme) { if (isFocusOwner) accent else border }
			g.drawRect(0, 0, width - 1, height - 1)
		}
	}

	companion object {

		/** Text width (px) used by the wrapped labels of a source card. */
		private const val SOURCE_CARD_TEXT_WIDTH = 205

		/** Thumbnails are shared by all cards and never written to disk. */
		private val hubCoverCache = LinkedHashMap<String, BufferedImage>(32, 0.75f, true)

		@Suppress("unused")
		private fun emptyResultsLabel(message: String, background: Color): Component =
			Ui.label(message, Theme.fontSmall, background)
	}
}

private fun escapeHtml(text: String): String = text
	.replace("&", "&amp;")
	.replace("<", "&lt;")
	.replace(">", "&gt;")
