package nobility.downloader.core.scraper.video_download

import org.jsoup.Jsoup

object PlayerPageStatus {
    const val SERVER_BUSY_MESSAGE = "WCO's video server reports that all servers are at full capacity. " +
        "No video link was returned."

    fun isServerBusy(source: String): Boolean {
        val page = Jsoup.parse(source)
        return page.title().equals("Servers Busy", ignoreCase = true) ||
            page.select(".capacity-box h1").any {
                it.text().equals("All Servers Are At Full Capacity", ignoreCase = true)
            }
    }
}
