package nobility.downloader

import nobility.downloader.core.Core
import nobility.downloader.ui.views.ViewPage

enum class Page(val title: String) {
    DOWNLOADER("Download"),
    WATCH("Watch"),
    DOWNLOADS("Downloads"),
    COMPLETED("Completed"),
    NEW_EPISODES("New episodes"),
    HISTORY("History"),
    FOLLOW("Follow Series"),
    DATABASE("Browse"),
    FAVORITES("Favorites"),
    SETTINGS("Settings"),
    ERROR_CONSOLE("Error Console");

    val mainPage: Page get() = when (this) {
        DOWNLOADS, COMPLETED -> DOWNLOADER
        HISTORY, FAVORITES, NEW_EPISODES -> FOLLOW
        else -> this
    }
    val mainTitle: String get() = when (mainPage) {
        DOWNLOADER -> "Downloads"
        FOLLOW -> "My Library"
        else -> mainPage.title
    }

    companion object {
        val mainPages = listOf(DATABASE, WATCH, DOWNLOADER, FOLLOW)


        fun nextPage(page: Page): Page? {
            val index = indexForPage(page)
            if (index != -1) {
                return mainPages.getOrNull(index + 1)
            }
            return null
        }

        fun beforePage(page: Page): Page? {
            val index = indexForPage(page)
            if (index != -1) {
                return mainPages.getOrNull(index - 1)
            }
            return null
        }

        private fun indexForPage(page: Page): Int {
            mainPages.forEachIndexed { i, p ->
                if (page.mainPage == p) {
                    return i
                }
            }
            return -1
        }

        fun Page.viewPage(): ViewPage {
            // might check Core::view.isInitialized
            return when (this) {
                DOWNLOADS, COMPLETED -> Core.downloadsView
                DOWNLOADER -> Core.downloaderView
                WATCH -> Core.watchView
                HISTORY -> Core.historyView
                FOLLOW, NEW_EPISODES -> Core.followSeriesView
                DATABASE -> Core.databaseView
                FAVORITES -> Core.favoritesView
                SETTINGS -> Core.settingsView
                ERROR_CONSOLE -> Core.errorConsoleView
            }
        }
    }
}