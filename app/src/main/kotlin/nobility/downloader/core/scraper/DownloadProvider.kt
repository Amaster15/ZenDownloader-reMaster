package nobility.downloader.core.scraper

import java.net.URI

object DownloadProvider {
    fun detect(input: String): WatchCatalog.Provider {
        val uri = runCatching { URI(input.trim()) }.getOrElse { throw IllegalArgumentException("Paste a valid HTTPS episode or series link.") }
        require(uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443)) {
            "Paste an HTTPS episode or series link."
        }
        return when (uri.host?.lowercase()?.removePrefix("www.")) {
            "wcoflix.tv", "wcofun.net", "wcofun.tv", "wco.tv", "wcostream.tv", "wcoforever.net", "wcofun.com" -> WatchCatalog.Provider.WCO
            "hianime.ms" -> WatchCatalog.Provider.HIANIME
            "animefreak.it.com" -> WatchCatalog.Provider.ANIMEFREAK
            "kimcartoon.com.co" -> WatchCatalog.Provider.KIMCARTOON
            else -> error("Unsupported site. Use a WCO, HiAnime, AnimeFreak or KimCartoon link.")
        }
    }
}
