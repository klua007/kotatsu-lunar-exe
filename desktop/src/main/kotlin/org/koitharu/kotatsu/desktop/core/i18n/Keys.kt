/*
 * Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
 * New file: does not exist upstream. Licensed under GPL-3.0-or-later (see LICENSE / NOTICE.md).
 *
 * All UI string keys in one place so that a unit test can assert every key has a translation
 * (see StringsCoverageTest).
 */
package org.koitharu.kotatsu.desktop.core.i18n

object K {

	// Application chrome
	const val APP_TITLE = "app.title"
	const val APP_SUBTITLE = "app.subtitle"
	const val APP_LOADING = "app.loading"
	const val APP_READY = "app.ready"

	// Menu
	const val MENU_FILE = "menu.file"
	const val MENU_FILE_OPEN_ARCHIVE = "menu.file.open_archive"
	const val MENU_FILE_IMPORT_FOLDER = "menu.file.import_folder"
	const val MENU_FILE_BACKUP = "menu.file.backup"
	const val MENU_FILE_RESTORE = "menu.file.restore"
	const val MENU_FILE_EXIT = "menu.file.exit"
	const val MENU_VIEW = "menu.view"
	const val MENU_VIEW_FULLSCREEN = "menu.view.fullscreen"
	const val MENU_VIEW_THEME = "menu.view.theme"
	const val MENU_VIEW_LANGUAGE = "menu.view.language"
	const val MENU_VIEW_RELOAD = "menu.view.reload"
	const val MENU_HELP = "menu.help"
	const val MENU_HELP_SHORTCUTS = "menu.help.shortcuts"
	const val MENU_HELP_ABOUT = "menu.help.about"
	const val MENU_HELP_DATA_FOLDER = "menu.help.data_folder"
	const val MENU_HELP_LOG_FOLDER = "menu.help.log_folder"

	// Navigation
	const val NAV_LIBRARY = "nav.library"
	const val NAV_HISTORY = "nav.history"
	const val NAV_DOWNLOADS = "nav.downloads"
	const val NAV_LOCAL = "nav.local"
	const val NAV_SETTINGS = "nav.settings"

	// Generic actions
	const val ACTION_OK = "action.ok"
	const val ACTION_CANCEL = "action.cancel"
	const val ACTION_CLOSE = "action.close"
	const val ACTION_DELETE = "action.delete"
	const val ACTION_REMOVE = "action.remove"
	const val ACTION_OPEN = "action.open"
	const val ACTION_READ = "action.read"
	const val ACTION_CONTINUE = "action.continue"
	const val ACTION_REFRESH = "action.refresh"
	const val ACTION_CHOOSE_FILES = "action.choose_files"
	const val ACTION_CHOOSE_FOLDER = "action.choose_folder"
	const val ACTION_OPEN_FOLDER = "action.open_folder"
	const val ACTION_CLEAR = "action.clear"
	const val ACTION_APPLY = "action.apply"
	const val ACTION_DOWNLOAD = "action.download"
	const val ACTION_SEARCH = "action.search"

	// Library
	const val LIBRARY_TITLE = "library.title"
	const val LIBRARY_EMPTY = "library.empty"
	const val LIBRARY_EMPTY_HINT = "library.empty_hint"
	const val LIBRARY_COUNT = "library.count"
	const val LIBRARY_SEARCH_HINT = "library.search_hint"
	const val LIBRARY_FILTER_ALL = "library.filter.all"
	const val LIBRARY_FILTER_FAVOURITES = "library.filter.favourites"
	const val LIBRARY_FILTER_DOWNLOADED = "library.filter.downloaded"
	const val LIBRARY_SORT = "library.sort"
	const val LIBRARY_SORT_TITLE = "library.sort.title"
	const val LIBRARY_SORT_ADDED = "library.sort.added"
	const val LIBRARY_SORT_LAST_READ = "library.sort.last_read"
	const val LIBRARY_SORT_PROGRESS = "library.sort.progress"
	const val LIBRARY_SOURCE = "library.source"
	const val LIBRARY_CHAPTERS = "library.chapters"
	const val LIBRARY_NO_MATCH = "library.no_match"

	// Details
	const val DETAILS_TITLE = "details.title"
	const val DETAILS_AUTHORS = "details.authors"
	const val DETAILS_TAGS = "details.tags"
	const val DETAILS_CHAPTERS = "details.chapters"
	const val DETAILS_PROGRESS = "details.progress"
	const val DETAILS_NO_CHAPTERS = "details.no_chapters"
	const val DETAILS_ADD_FAVOURITE = "details.add_favourite"
	const val DETAILS_REMOVE_FAVOURITE = "details.remove_favourite"
	const val DETAILS_DOWNLOAD_CHAPTER = "details.download_chapter"
	const val DETAILS_MARK_READ = "details.mark_read"

	// History
	const val HISTORY_TITLE = "history.title"
	const val HISTORY_EMPTY = "history.empty"
	const val HISTORY_CLEAR_CONFIRM = "history.clear_confirm"
	const val HISTORY_UPDATED = "history.updated"

	// Downloads
	const val DOWNLOADS_TITLE = "downloads.title"
	const val DOWNLOADS_EMPTY = "downloads.empty"
	const val DOWNLOADS_CHAPTERS = "downloads.chapters"
	const val DOWNLOADS_OFFLINE_READY = "downloads.offline_ready"

	// Local library
	const val LOCAL_TITLE = "local.title"
	const val LOCAL_EMPTY = "local.empty"
	const val LOCAL_IMPORT_FILES = "local.import_files"
	const val LOCAL_IMPORT_FOLDER = "local.import_folder"
	const val LOCAL_IMPORT_RESULT = "local.import_result"
	const val LOCAL_IMPORT_SKIPPED = "local.import_skipped"
	const val LOCAL_DROP_HINT = "local.drop_hint"
	const val LOCAL_MANGA = "local.manga"

	// Reader
	const val READER_TITLE = "reader.title"
	const val READER_PAGE_OF = "reader.page_of"
	const val READER_CHAPTER = "reader.chapter"
	const val READER_MODE = "reader.mode"
	const val READER_MODE_STANDARD = "reader.mode.standard"
	const val READER_MODE_RTL = "reader.mode.rtl"
	const val READER_MODE_VERTICAL = "reader.mode.vertical"
	const val READER_MODE_WEBTOON = "reader.mode.webtoon"
	const val READER_ZOOM_IN = "reader.zoom_in"
	const val READER_ZOOM_OUT = "reader.zoom_out"
	const val READER_ZOOM_RESET = "reader.zoom_reset"
	const val READER_FIT_WIDTH = "reader.fit_width"
	const val READER_FIT_HEIGHT = "reader.fit_height"
	const val READER_CONTENTS = "reader.contents"
	const val READER_SETTINGS = "reader.settings"
	const val READER_NEXT_CHAPTER = "reader.next_chapter"
	const val READER_PREV_CHAPTER = "reader.prev_chapter"
	const val READER_END_OF_CHAPTER = "reader.end_of_chapter"
	const val READER_END_OF_MANGA = "reader.end_of_manga"
	const val READER_LOADING = "reader.loading"
	const val READER_ERROR_PAGE = "reader.error_page"
	const val READER_ERROR_ARCHIVE = "reader.error_archive"
	const val READER_NO_PAGES = "reader.no_pages"
	const val READER_SAVED = "reader.saved"
	const val READER_GAP = "reader.gap"
	const val READER_BACKGROUND = "reader.background"
	const val READER_BACKGROUND_BLACK = "reader.background.black"
	const val READER_BACKGROUND_GRAY = "reader.background.gray"
	const val READER_BACKGROUND_WHITE = "reader.background.white"
	const val READER_INSECURE_SUPPORT = "reader.unsupported_format"
	const val READER_PAGE_SAVE = "reader.page_save"
	const val READER_PAGE_SAVED = "reader.page_saved"

	// Settings
	const val SETTINGS_TITLE = "settings.title"
	const val SETTINGS_APPEARANCE = "settings.appearance"
	const val SETTINGS_LANGUAGE = "settings.language"
	const val SETTINGS_THEME = "settings.theme"
	const val SETTINGS_THEME_DARK = "settings.theme.dark"
	const val SETTINGS_THEME_LIGHT = "settings.theme.light"
	const val SETTINGS_READER = "settings.reader"
	const val SETTINGS_READER_DEFAULT_MODE = "settings.reader.default_mode"
	const val SETTINGS_READER_SCROLL_ZOOM = "settings.reader.wheel_zoom"
	const val SETTINGS_READER_PRELOAD = "settings.reader.preload"
	const val SETTINGS_READER_PROGRESS_SAVE = "settings.reader.progress_save"
	const val SETTINGS_STORAGE = "settings.storage"
	const val SETTINGS_DATA_FOLDER = "settings.data_folder"
	const val SETTINGS_CACHE_FOLDER = "settings.cache_folder"
	const val SETTINGS_CACHE_SIZE = "settings.cache_size"
	const val SETTINGS_CACHE_CLEAR = "settings.cache_cleared"
	const val SETTINGS_CACHE_CLEARED_COUNT = "settings.cache_cleared_count"

	// Backup
	const val BACKUP_TITLE = "backup.title"
	const val BACKUP_EXPORT = "backup.export"
	const val BACKUP_IMPORT = "backup.import"
	const val BACKUP_EXPORTED = "backup.exported"
	const val BACKUP_IMPORTED = "backup.imported"
	const val BACKUP_REPORT = "backup.report"
	const val BACKUP_REPORT_FAVOURITES = "backup.report.favourites"
	const val BACKUP_REPORT_HISTORY = "backup.report.history"
	const val BACKUP_REPORT_CATEGORIES = "backup.report.categories"
	const val BACKUP_REPORT_BOOKMARKS = "backup.report.bookmarks"
	const val BACKUP_REPORT_SOURCES = "backup.report.sources"
	const val BACKUP_REPORT_SETTINGS = "backup.report.settings"
	const val BACKUP_REPORT_SKIPPED = "backup.report.skipped"
	const val BACKUP_REPORT_INVALID = "backup.report.invalid"
	const val BACKUP_WARNING = "backup.warning"
	const val BACKUP_DOWNLOADS_NOTE = "backup.downloads_note"

	// Errors / dialogs
	const val ERROR_TITLE = "error.title"
	const val ERROR_IO = "error.io"
	const val ERROR_UNEXPECTED = "error.unexpected"
	const val ERROR_ARCHIVE_CORRUPT = "error.archive_corrupt"
	const val ERROR_NO_IMAGES = "error.no_images"
	const val ERROR_FILE_NOT_FOUND = "error.file_not_found"
	const val CONFIRM_TITLE = "confirm.title"

	// About & shortcuts
	const val ABOUT_TITLE = "about.title"
	const val ABOUT_BODY = "about.body"
	const val ABOUT_LICENSE = "about.license"
	const val SHORTCUTS_TITLE = "shortcuts.title"
	const val SHORTCUTS_BODY = "shortcuts.body"

	// Status bar
	const val STATUS_ITEMS = "status.items"
	const val STATUS_SOURCE_LOCAL = "status.source.local"

	// Online sources (not implemented yet)
	const val SOURCES_TITLE = "sources.title"
	const val SOURCES_UNAVAILABLE = "sources.unavailable"
	const val SOURCES_SEARCH_LOCAL_ONLY = "sources.search_local_only"
}
