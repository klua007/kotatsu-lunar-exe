/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * Desktop translation table. English + Brazilian Portuguese (upstream Android locales remain in
 * app/src/main/res/values-* and are unaffected).
 */
package org.koitharu.kotatsu.desktop.core.i18n

import org.koitharu.kotatsu.desktop.core.i18n.K as K

internal object Strings {

	private fun row(en: String, ptBr: String): Array<String> = arrayOf(en, ptBr)

	val TRANSLATIONS: Map<String, Array<String>> = mapOf(
		K.APP_TITLE to row("Kotatsu Lunar", "Kotatsu Lunar"),
		K.APP_SUBTITLE to row(
			"Unofficial desktop port of Kotatsu-Redo",
			"Porta não oficial do Kotatsu-Redo para desktop",
		),
		K.APP_LOADING to row("Loading…", "Carregando…"),
		K.APP_READY to row("Ready", "Pronto"),

		K.MENU_FILE to row("File", "Arquivo"),
		K.MENU_FILE_OPEN_ARCHIVE to row("Open CBZ/ZIP…", "Abrir CBZ/ZIP…"),
		K.MENU_FILE_IMPORT_FOLDER to row("Import folder…", "Importar pasta…"),
		K.MENU_FILE_BACKUP to row("Create backup…", "Criar backup…"),
		K.MENU_FILE_RESTORE to row("Restore backup…", "Restaurar backup…"),
		K.MENU_FILE_EXIT to row("Exit", "Sair"),
		K.MENU_VIEW to row("View", "Exibir"),
		K.MENU_VIEW_FULLSCREEN to row("Full screen", "Tela cheia"),
		K.MENU_VIEW_THEME to row("Theme", "Tema"),
		K.MENU_VIEW_LANGUAGE to row("Language", "Idioma"),
		K.MENU_VIEW_RELOAD to row("Reload library", "Recarregar biblioteca"),
		K.MENU_HELP to row("Help", "Ajuda"),
		K.MENU_HELP_SHORTCUTS to row("Keyboard shortcuts", "Atalhos de teclado"),
		K.MENU_HELP_ABOUT to row("About", "Sobre"),
		K.MENU_HELP_DATA_FOLDER to row("Open data folder", "Abrir pasta de dados"),
		K.MENU_HELP_LOG_FOLDER to row("Open log folder", "Abrir pasta de logs"),

		K.NAV_LIBRARY to row("Library", "Biblioteca"),
		K.NAV_HISTORY to row("History", "Histórico"),
		K.NAV_DOWNLOADS to row("Downloads", "Downloads"),
		K.NAV_LOCAL to row("Local files", "Arquivos locais"),
		K.NAV_SETTINGS to row("Settings", "Configurações"),

		K.ACTION_OK to row("OK", "OK"),
		K.ACTION_CANCEL to row("Cancel", "Cancelar"),
		K.ACTION_CLOSE to row("Close", "Fechar"),
		K.ACTION_DELETE to row("Delete", "Excluir"),
		K.ACTION_REMOVE to row("Remove", "Remover"),
		K.ACTION_OPEN to row("Open", "Abrir"),
		K.ACTION_READ to row("Read", "Ler"),
		K.ACTION_CONTINUE to row("Continue reading", "Continuar lendo"),
		K.ACTION_REFRESH to row("Refresh", "Atualizar"),
		K.ACTION_CHOOSE_FILES to row("Choose files…", "Escolher arquivos…"),
		K.ACTION_CHOOSE_FOLDER to row("Choose folder…", "Escolher pasta…"),
		K.ACTION_OPEN_FOLDER to row("Open folder", "Abrir pasta"),
		K.ACTION_CLEAR to row("Clear", "Limpar"),
		K.ACTION_APPLY to row("Apply", "Aplicar"),
		K.ACTION_DOWNLOAD to row("Download", "Baixar"),
		K.ACTION_SEARCH to row("Search", "Pesquisar"),

		K.LIBRARY_TITLE to row("Library", "Biblioteca"),
		K.LIBRARY_EMPTY to row("Your library is empty", "Sua biblioteca está vazia"),
		K.LIBRARY_EMPTY_HINT to row(
			"Import CBZ/ZIP archives or a folder of archives with File → Open CBZ/ZIP.",
			"Importe arquivos CBZ/ZIP ou uma pasta de arquivos em Arquivo → Abrir CBZ/ZIP.",
		),
		K.LIBRARY_COUNT to row("%d titles", "%d títulos"),
		K.LIBRARY_SEARCH_HINT to row("Search title, author or tag…", "Pesquisar título, autor ou tag…"),
		K.LIBRARY_FILTER_ALL to row("All", "Todos"),
		K.LIBRARY_FILTER_FAVOURITES to row("Favourites", "Favoritos"),
		K.LIBRARY_FILTER_DOWNLOADED to row("Downloaded", "Baixados"),
		K.LIBRARY_SORT to row("Sort by", "Ordenar por"),
		K.LIBRARY_SORT_TITLE to row("Title", "Título"),
		K.LIBRARY_SORT_ADDED to row("Date added", "Data de inclusão"),
		K.LIBRARY_SORT_LAST_READ to row("Last read", "Última leitura"),
		K.LIBRARY_SORT_PROGRESS to row("Progress", "Progresso"),
		K.LIBRARY_SOURCE to row("Source", "Fonte"),
		K.LIBRARY_CHAPTERS to row("Chapters", "Capítulos"),
		K.LIBRARY_NO_MATCH to row("Nothing matches your search", "Nada corresponde à sua pesquisa"),

		K.DETAILS_TITLE to row("Details", "Detalhes"),
		K.DETAILS_AUTHORS to row("Author", "Autor"),
		K.DETAILS_TAGS to row("Tags", "Tags"),
		K.DETAILS_CHAPTERS to row("Chapters", "Capítulos"),
		K.DETAILS_PROGRESS to row("Progress", "Progresso"),
		K.DETAILS_NO_CHAPTERS to row("No chapters found", "Nenhum capítulo encontrado"),
		K.DETAILS_ADD_FAVOURITE to row("Add to favourites", "Adicionar aos favoritos"),
		K.DETAILS_REMOVE_FAVOURITE to row("Remove from favourites", "Remover dos favoritos"),
		K.DETAILS_DOWNLOAD_CHAPTER to row("Download chapter (CBZ)", "Baixar capítulo (CBZ)"),
		K.DETAILS_MARK_READ to row("Mark as read", "Marcar como lido"),

		K.HISTORY_TITLE to row("History", "Histórico"),
		K.HISTORY_EMPTY to row("You have not read anything yet", "Você ainda não leu nada"),
		K.HISTORY_CLEAR_CONFIRM to row(
			"Remove all history? Reading progress will be lost.",
			"Remover todo o histórico? O progresso de leitura será perdido.",
		),
		K.HISTORY_UPDATED to row("Last read: %s", "Última leitura: %s"),

		K.DOWNLOADS_TITLE to row("Downloads", "Downloads"),
		K.DOWNLOADS_EMPTY to row(
			"No offline chapters yet. Download a chapter from the details screen.",
			"Nenhum capítulo offline ainda. Baixe um capítulo na tela de detalhes.",
		),
		K.DOWNLOADS_CHAPTERS to row("%d offline chapters", "%d capítulos offline"),
		K.DOWNLOADS_OFFLINE_READY to row("Available offline", "Disponível offline"),

		K.LOCAL_TITLE to row("Local files", "Arquivos locais"),
		K.LOCAL_EMPTY to row("No local archives imported", "Nenhum arquivo local importado"),
		K.LOCAL_IMPORT_FILES to row("Import CBZ/ZIP files", "Importar arquivos CBZ/ZIP"),
		K.LOCAL_IMPORT_FOLDER to row("Import folder", "Importar pasta"),
		K.LOCAL_IMPORT_RESULT to row("Imported %d file(s)", "%d arquivo(s) importado(s)"),
		K.LOCAL_IMPORT_SKIPPED to row("Skipped %d file(s) (already imported or unreadable)", "%d arquivo(s) ignorado(s) (já importados ou ilegíveis)"),
		K.LOCAL_DROP_HINT to row(
			"Drop CBZ/ZIP files here, or use File → Open CBZ/ZIP",
			"Solte arquivos CBZ/ZIP aqui ou use Arquivo → Abrir CBZ/ZIP",
		),
		K.LOCAL_MANGA to row("Local archive", "Arquivo local"),

		K.READER_TITLE to row("Reader", "Leitor"),
		K.READER_PAGE_OF to row("Page %1\$d of %2\$d", "Página %1\$d de %2\$d"),
		K.READER_CHAPTER to row("Chapter %s", "Capítulo %s"),
		K.READER_MODE to row("Reading mode", "Modo de leitura"),
		K.READER_MODE_STANDARD to row("Standard (page by page)", "Padrão (página a página)"),
		K.READER_MODE_RTL to row("Right to left", "Da direita para a esquerda"),
		K.READER_MODE_VERTICAL to row("Vertical (fit width)", "Vertical (ajustar à largura)"),
		K.READER_MODE_WEBTOON to row("Webtoon (continuous)", "Webtoon (contínuo)"),
		K.READER_ZOOM_IN to row("Zoom in", "Aumentar zoom"),
		K.READER_ZOOM_OUT to row("Zoom out", "Diminuir zoom"),
		K.READER_ZOOM_RESET to row("Reset zoom", "Redefinir zoom"),
		K.READER_FIT_WIDTH to row("Fit width", "Ajustar à largura"),
		K.READER_FIT_HEIGHT to row("Fit height", "Ajustar à altura"),
		K.READER_CONTENTS to row("Contents", "Sumário"),
		K.READER_SETTINGS to row("Reader settings", "Configurações do leitor"),
		K.READER_NEXT_CHAPTER to row("Next chapter", "Próximo capítulo"),
		K.READER_PREV_CHAPTER to row("Previous chapter", "Capítulo anterior"),
		K.READER_END_OF_CHAPTER to row("End of chapter", "Fim do capítulo"),
		K.READER_END_OF_MANGA to row("End of the last chapter", "Fim do último capítulo"),
		K.READER_LOADING to row("Loading page…", "Carregando página…"),
		K.READER_ERROR_PAGE to row("This page could not be loaded", "Não foi possível carregar esta página"),
		K.READER_ERROR_ARCHIVE to row("The archive could not be opened", "Não foi possível abrir o arquivo"),
		K.READER_NO_PAGES to row("This chapter has no readable pages", "Este capítulo não tem páginas legíveis"),
		K.READER_SAVED to row("Progress saved", "Progresso salvo"),
		K.READER_GAP to row("Page gap", "Espaço entre páginas"),
		K.READER_BACKGROUND to row("Background", "Fundo"),
		K.READER_BACKGROUND_BLACK to row("Black", "Preto"),
		K.READER_BACKGROUND_GRAY to row("Gray", "Cinza"),
		K.READER_BACKGROUND_WHITE to row("White", "Branco"),
		K.READER_INSECURE_SUPPORT to row(
			"Unsupported image format (e.g. WebP/AVIF); convert the page to JPEG or PNG.",
			"Formato de imagem não suportado (ex.: WebP/AVIF); converta a página para JPEG ou PNG.",
		),
		K.READER_PAGE_SAVE to row("Save page as image…", "Salvar página como imagem…"),
		K.READER_PAGE_SAVED to row("Page saved to %s", "Página salva em %s"),

		K.SETTINGS_TITLE to row("Settings", "Configurações"),
		K.SETTINGS_APPEARANCE to row("Appearance", "Aparência"),
		K.SETTINGS_LANGUAGE to row("Interface language", "Idioma da interface"),
		K.SETTINGS_THEME to row("Theme", "Tema"),
		K.SETTINGS_THEME_DARK to row("Dark", "Escuro"),
		K.SETTINGS_THEME_LIGHT to row("Light", "Claro"),
		K.SETTINGS_READER to row("Reader defaults", "Padrões do leitor"),
		K.SETTINGS_READER_DEFAULT_MODE to row("Default reading mode", "Modo de leitura padrão"),
		K.SETTINGS_READER_SCROLL_ZOOM to row("Mouse wheel zooms (Ctrl+Wheel)", "Roda do mouse dá zoom (Ctrl+Roda)"),
		K.SETTINGS_READER_PRELOAD to row("Preload next pages", "Pré-carregar próximas páginas"),
		K.SETTINGS_READER_PROGRESS_SAVE to row("Save progress automatically", "Salvar progresso automaticamente"),
		K.SETTINGS_STORAGE to row("Storage", "Armazenamento"),
		K.SETTINGS_DATA_FOLDER to row("Data folder", "Pasta de dados"),
		K.SETTINGS_CACHE_FOLDER to row("Cache folder", "Pasta de cache"),
		K.SETTINGS_CACHE_SIZE to row("Cache size: %s", "Tamanho do cache: %s"),
		K.SETTINGS_CACHE_CLEAR to row("Clear cache", "Limpar cache"),
		K.SETTINGS_CACHE_CLEARED_COUNT to row("Cache cleared (%d files)", "Cache limpo (%d arquivos)"),

		K.BACKUP_TITLE to row("Backup", "Backup"),
		K.BACKUP_EXPORT to row("Create backup", "Criar backup"),
		K.BACKUP_IMPORT to row("Restore backup", "Restaurar backup"),
		K.BACKUP_EXPORTED to row("Backup written to %s", "Backup gravado em %s"),
		K.BACKUP_IMPORTED to row("Backup restored", "Backup restaurado"),
		K.BACKUP_REPORT to row("Import report", "Relatório da importação"),
		K.BACKUP_REPORT_FAVOURITES to row("Favourites: %d", "Favoritos: %d"),
		K.BACKUP_REPORT_HISTORY to row("History entries: %d", "Itens do histórico: %d"),
		K.BACKUP_REPORT_CATEGORIES to row("Categories: %d", "Categorias: %d"),
		K.BACKUP_REPORT_BOOKMARKS to row("Bookmarks: %d", "Marcadores: %d"),
		K.BACKUP_REPORT_SOURCES to row("Sources: %d", "Fontes: %d"),
		K.BACKUP_REPORT_SETTINGS to row("Settings applied: %d", "Configurações aplicadas: %d"),
		K.BACKUP_REPORT_SKIPPED to row("Skipped entries: %d", "Entradas ignoradas: %d"),
		K.BACKUP_REPORT_INVALID to row("Invalid entries: %d", "Entradas inválidas: %d"),
		K.BACKUP_WARNING to row(
			"Merging a backup only adds or updates data; nothing already stored is deleted.",
			"A mesclagem de um backup apenas adiciona ou atualiza dados; nada já armazenado é apagado.",
		),
		K.BACKUP_DOWNLOADS_NOTE to row(
			"Downloaded chapters (CBZ files) are not part of the backup and must be copied manually.",
			"Capítulos baixados (arquivos CBZ) não fazem parte do backup e devem ser copiados manualmente.",
		),

		K.ERROR_TITLE to row("Error", "Erro"),
		K.ERROR_IO to row("Could not access the file: %s", "Não foi possível acessar o arquivo: %s"),
		K.ERROR_UNEXPECTED to row("Unexpected error: %s", "Erro inesperado: %s"),
		K.ERROR_ARCHIVE_CORRUPT to row("The archive is damaged or is not a ZIP file", "O arquivo está danificado ou não é um ZIP"),
		K.ERROR_NO_IMAGES to row("No supported images were found in the archive", "Nenhuma imagem suportada foi encontrada no arquivo"),
		K.ERROR_FILE_NOT_FOUND to row("File not found: %s", "Arquivo não encontrado: %s"),
		K.CONFIRM_TITLE to row("Confirm", "Confirmar"),

		K.ABOUT_TITLE to row("About Kotatsu Lunar", "Sobre o Kotatsu Lunar"),
		K.ABOUT_BODY to row(
			"Kotatsu Lunar is an unofficial desktop (Windows 10/11 x64) port of Kotatsu-Redo, an open-source manga reader.\n\n" +
				"It is not affiliated with, endorsed by, or supported by the Kotatsu or Kotatsu-Redo maintainers. " +
				"The application stores no content of its own; it only reads archives and files provided by the user.",
			"Kotatsu Lunar é uma versão não oficial para desktop (Windows 10/11 x64) do Kotatsu-Redo, um leitor de mangá de código aberto.\n\n" +
				"Não é afiliado, endossado ou suportado pelos mantenedores do Kotatsu ou Kotatsu-Redo. " +
				"O aplicativo não armazena conteúdo próprio; ele apenas lê arquivos fornecidos pelo usuário.",
		),
		K.ABOUT_LICENSE to row(
			"Licensed under the GNU General Public License v3.0 or later. Source code and credits: see LICENSE and NOTICE.md.",
			"Licenciado sob a GNU General Public License v3.0 ou posterior. Código-fonte e créditos: veja LICENSE e NOTICE.md.",
		),
		K.SHORTCUTS_TITLE to row("Keyboard shortcuts", "Atalhos de teclado"),
		K.SHORTCUTS_BODY to row(
			"Reader\n" +
				"  → / ↓ / Space / Page Down : next page\n" +
				"  ← / ↑ / Page Up : previous page\n" +
				"  Home / End : first / last page\n" +
				"  Ctrl + Wheel, + / - : zoom\n" +
				"  0 : reset zoom, W : fit width, H : fit height\n" +
				"  M : cycle reading mode\n" +
				"  T : table of contents\n" +
				"  N / P : next / previous chapter\n" +
				"  S : save current page as image\n" +
				"  F11 : full screen, Esc : exit reader\n\n" +
				"Library\n" +
				"  Ctrl+F : focus search\n" +
				"  Ctrl+O : open CBZ/ZIP\n" +
				"  Enter : open the selected title\n" +
				"  F5 : reload",
			"Leitor\n" +
				"  → / ↓ / Espaço / Page Down : próxima página\n" +
				"  ← / ↑ / Page Up : página anterior\n" +
				"  Home / End : primeira / última página\n" +
				"  Ctrl + Roda, + / - : zoom\n" +
				"  0 : redefinir zoom, W : ajustar à largura, H : ajustar à altura\n" +
				"  M : alternar modo de leitura\n" +
				"  T : sumário\n" +
				"  N / P : próximo / capítulo anterior\n" +
				"  S : salvar página atual como imagem\n" +
				"  F11 : tela cheia, Esc : sair do leitor\n\n" +
				"Biblioteca\n" +
				"  Ctrl+F : focar pesquisa\n" +
				"  Ctrl+O : abrir CBZ/ZIP\n" +
				"  Enter : abrir o título selecionado\n" +
				"  F5 : recarregar",
		),

		K.STATUS_ITEMS to row("%d items", "%d itens"),
		K.STATUS_SOURCE_LOCAL to row("Local", "Local"),

		K.SOURCES_TITLE to row("Online sources", "Fontes online"),
		K.SOURCES_UNAVAILABLE to row(
			"Online sources are not available in this desktop build yet. See docs/FEATURE_MATRIX.md.",
			"As fontes online ainda não estão disponíveis nesta versão de desktop. Veja docs/FEATURE_MATRIX.md.",
		),
		K.SOURCES_SEARCH_LOCAL_ONLY to row(
			"Searching local library only",
			"Pesquisando apenas na biblioteca local",
		),
	)
}
