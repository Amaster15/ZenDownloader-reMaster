package nobility.downloader.core.scraper

import AppInfo
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.scraper.video_download.Functions
import nobility.downloader.utils.slugToLink
import org.jsoup.Jsoup
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest

/** Cache catalog metadata only. Signed media URLs are always resolved at playback time. */
object WatchCatalog {
    enum class Provider(val label: String) { WCO("WCO"), HIANIME("HiAnime"), ANIMEFREAK("AnimeFreak"), KIMCARTOON("KimCartoon") }
    data class Show(val title: String, val url: String, val provider: Provider)
    data class Entry(val title: String, val url: String, val language: String = "")
    data class Listing(val fetchedAt: Long, val entries: List<Entry>)
    private val gson = Gson()
    private const val ttl = 6 * 60 * 60 * 1000L

    private suspend fun read(url: String, provider: Provider): String = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        if (provider == Provider.KIMCARTOON) return@withContext KimCartoon.read(url)
        if (provider == Provider.ANIMEFREAK) return@withContext AnimeFreak.catalogHtml(url)
        if (provider == Provider.WCO) {
            val result = Functions.readUrlLines(url, "Watch catalog")
            result.data?.toString() ?: error(result.message ?: "Unable to read WCO catalog")
        } else {
            Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(30_000).maxBodySize(4 * 1024 * 1024).get().outerHtml()
        }
    }

    suspend fun search(query: String, provider: Provider): List<Show> = withContext(Dispatchers.IO) {
        val q = query.trim()
        require(q.isNotEmpty()) { "Enter a series name or series URL" }
        if (provider == Provider.KIMCARTOON && q.startsWith("https://")) return@withContext listOf(KimCartoon.show(q))
        if (provider == Provider.ANIMEFREAK && q.startsWith("https://")) {
            val url = AnimeFreak.seriesUrl(q)
            val series = AnimeFreak.load(url)
            return@withContext listOf(Show(series.title, url, provider)).also { AnimeTitleIndex.remember(it) }
        }
        if (q.startsWith("https://")) {
            val uri = URI(q)
            val allowed = if (provider == Provider.HIANIME) uri.host == "hianime.ms" && uri.path.startsWith("/details/")
                else uri.host == URI("anime/".slugToLink()).host && uri.path.startsWith("/anime/") ||
                    uri.host in listOf("wcoflix.tv", "www.wcoflix.tv") && uri.path.startsWith("/anime/")
            require(allowed) { "Paste a series URL for the selected provider (HiAnime: /details/; WCO: /anime/)" }
            return@withContext listOf(Show(uri.path.substringAfterLast('/').ifBlank { q }, q, provider))
        }
        if (provider == Provider.WCO) {
            val local = BoxHelper.allSeriesNoMovies.filter { it.name.contains(q, true) }
                .map { Show(it.name, it.slug.slugToLink(), provider) }.distinctBy { it.url }
            if (local.isNotEmpty()) return@withContext local.sortedBy { it.title }.take(150)
        }
        val encoded = URLEncoder.encode(q, Charsets.UTF_8)
        val url = if (provider == Provider.KIMCARTOON) "https://kimcartoon.com.co/?s=$encoded"
            else if (provider == Provider.ANIMEFREAK) "https://animefreak.it.com/search?keyword=$encoded"
            else if (provider == Provider.HIANIME) "https://hianime.ms/search?q=$encoded"
            else "".slugToLink().trimEnd('/') + "/search"
        val html = if (provider == Provider.WCO) searchWco(q, url) else read(url, provider)
        val results = parseSearch(html, url, provider)
        if (provider == Provider.ANIMEFREAK) {
            AnimeTitleIndex.remember(results)
            (results + AnimeTitleIndex.search(q)).distinctBy { it.url }.take(150)
        } else results
    }

    private suspend fun searchWco(query: String, url: String): String {
        try {
            return Jsoup.connect(url).userAgent(nobility.downloader.utils.user_agents.UserAgents.random)
                .timeout(30_000).maxBodySize(4 * 1024 * 1024)
                .data("catara", query, "konuara", "series").post().outerHtml()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { currentCoroutineContext().ensureActive() }
        val result = Functions.readUrlLines("".slugToLink(), "Watch title search", skipSimpleMode = true,
            fullModeFunction = { base ->
                val input = base.driver.findElement(org.openqa.selenium.By.name("catara"))
                input.clear()
                input.sendKeys(query)
                org.openqa.selenium.support.ui.Select(base.driver.findElement(org.openqa.selenium.By.name("konuara")))
                    .selectByValue("series")
                base.driver.findElement(org.openqa.selenium.By.cssSelector("form#konuara input[type=submit]")).click()
                base.waitForPageJs()
            })
        return result.data?.toString() ?: error(result.message ?: "WCO search is unavailable. Try a series URL or the cached database.")
    }

    private fun sameHost(a: String?, b: String?) = a?.removePrefix("www.") == b?.removePrefix("www.")

    @JvmStatic fun parseSearch(html: String, base: String, provider: Provider): List<Show> {
        if (provider == Provider.KIMCARTOON) return KimCartoon.parseSearch(html, base)
        val doc = Jsoup.parse(html, base)
        if (provider == Provider.ANIMEFREAK) doc.select("#sidebar, .sidebar").remove()
        return doc.select("a[href]").mapNotNull { a ->
            val url = a.absUrl("href")
            val uri = runCatching { URI(url) }.getOrNull() ?: return@mapNotNull null
            val path = if (provider == Provider.HIANIME) "/details/" else "/anime/"
            val title = a.text().trim().ifBlank { a.selectFirst("img")?.attr("alt").orEmpty() }
            if (provider == Provider.ANIMEFREAK && !uri.path.matches(Regex("/anime/[a-zA-Z0-9-]+/?"))) return@mapNotNull null
            if (!sameHost(uri.host, URI(base).host) || !uri.path.startsWith(path) || title.isBlank()) null
            else Show(title, url, provider)
        }.groupBy { it.url }.values.map { matches -> matches.minBy { it.title.length } }.take(150)
    }

    private fun cacheFile(show: Show): File {
        val key = MessageDigest.getInstance("SHA-256").digest((show.provider.name + show.url).toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(AppInfo.databasePath, "my_data/watch-cache/$key.json")
    }
    fun cached(show: Show): Listing? = runCatching {
        val file = cacheFile(show)
        if (!file.exists() || file.length() > 4 * 1024 * 1024) {
            if (show.provider == Provider.WCO) {
                BoxHelper.seriesForSlug(slug(show.url))?.let { series ->
                    series.episodes.toList().takeIf { it.isNotEmpty() }?.let { episodes ->
                        Listing(series.lastUpdated, episodes.map { Entry(it.name, it.slug.slugToLink()) })
                    }
                }
            } else null
        }
        else gson.fromJson(file.readText(), Listing::class.java)?.takeIf { it.entries.isNotEmpty() }
    }.getOrNull()
    fun fresh(listing: Listing) = System.currentTimeMillis() - listing.fetchedAt in 0 until ttl

    suspend fun episodes(show: Show): Listing = withContext(Dispatchers.IO) {
        var url = show.url
        if (show.provider == Provider.WCO) {
            val uri = URI(url)
            url = URI(uri.scheme, uri.authority, uri.path, "season=all", null).toString()
        }
        var html = read(url, show.provider)
        if (show.provider == Provider.HIANIME) {
            val doc = Jsoup.parse(html, url)
            url = doc.select("a[href]").map { it.absUrl("href") }.firstOrNull {
                val uri = URI(it)
                uri.host == "hianime.ms" && uri.path.startsWith("/watch-")
            } ?: error("HiAnime did not list any episodes for this series")
            html = read(url, show.provider)
        }
        val entries = parseEpisodes(html, url, show.provider).sortedWith(compareBy<Entry> {
            nobility.downloader.utils.EpisodeNaming.numbering(it.title, it.url, show.title)?.season ?: Int.MAX_VALUE
        }.thenBy {
            nobility.downloader.utils.EpisodeNaming.numbering(it.title, it.url, show.title)?.episode ?: Int.MAX_VALUE
        }.thenBy { it.title })
        require(entries.isNotEmpty()) { "No episodes were found. The provider may be unavailable or its page has changed." }
        val listing = Listing(System.currentTimeMillis(), entries)
        val file = cacheFile(show)
        file.parentFile.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(gson.toJson(listing))
        java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        listing
    }

    @JvmStatic fun parseEpisodes(html: String, base: String, provider: Provider): List<Entry> {
        if (provider == Provider.KIMCARTOON) return KimCartoon.parseEpisodes(html, base)
        val doc = Jsoup.parse(html, base)
        if (provider == Provider.ANIMEFREAK) {
            val series = AnimeFreak.parseSeries(html, base)
            val language = if (series.title.contains("dub", true) || base.contains("-dub")) "dub" else "sub"
            return series.episodes.map { Entry("Episode ${it.number}", it.url, language) }
        }
        if (provider == Provider.HIANIME) return doc.select("a[data-episode][href]").flatMap { a ->
            val number = a.attr("data-episode").toIntOrNull() ?: return@flatMap emptyList()
            val url = a.absUrl("href")
            if (URI(url).host != "hianime.ms") return@flatMap emptyList()
            listOf("dub", "sub").filter { a.attr("data-$it") == "1" }.map {
                Entry("Episode $number English ${if (it == "dub") "Dubbed" else "Subbed"}", url, it)
            }
        }.distinctBy { it.url to it.language }
        return doc.select("#episodeList a.dark-episode-item, .cat-eps a[href]").mapNotNull { a ->
            val url = a.absUrl("href")
            val title = nobility.downloader.utils.EpisodeNaming.sourceTitle(a)
            if (title.isBlank() || !sameHost(URI(url).host, URI(base).host)) null
            else Entry(title, url)
        }.distinctBy { it.url }
    }

    @JvmStatic fun slug(url: String): String = URI(url).path.trim('/')

    fun wcoEpisode(show: Show, entry: Entry) = Episode(entry.title, slug(entry.url), slug(show.url))
}
