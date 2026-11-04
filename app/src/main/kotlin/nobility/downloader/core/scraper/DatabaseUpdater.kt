package nobility.downloader.core.scraper

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import nobility.downloader.core.*
import nobility.downloader.core.entities.Series
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.core.scraper.video_download.Functions
import nobility.downloader.utils.slugToLink
import org.jsoup.Jsoup
import java.net.URI

/** Refresh the live index without replacing user data with an old downloadable snapshot. */
object DatabaseUpdater {
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    private var job: Job? = null
    data class Title(val slug: String, val name: String)

    @JvmStatic fun parseIndex(html: String, base: String): List<Title> {
        val doc = Jsoup.parse(html, base)
        val legacy = doc.select(".ddmcc li a[href]")
        val anchors = if (legacy.isNotEmpty()) legacy else doc.select("a[href*=/anime/]")
        return anchors.mapNotNull { a ->
            val url = runCatching { URI(a.absUrl("href")) }.getOrNull() ?: return@mapNotNull null
            if (url.host?.removePrefix("www.") != URI(base).host?.removePrefix("www.") || a.text().isBlank()) null
            else url.path.trim('/').takeIf { it.isNotEmpty() && it != "anime/movies" }?.let { Title(it, a.text().trim()) }
        }.distinctBy { it.slug }
    }

    fun cancel() { job?.cancel() }
    fun updateCatalog() {
        if (busy) return
        busy = true
        job = Core.taskScope.launch(Dispatchers.Main) {
            var added = 0
            var completed = 0
            val failures = mutableListOf<String>()
            try {
                for (category in SeriesIdentity.filteredValues()) {
                    status = "Checking WCO ${category.name.lowercase()} catalog…"
                    try {
                        withTimeout(120_000) {
                            withContext(Dispatchers.IO) {
                                val url = category.slug.slugToLink()
                                val response = Functions.readUrlLines(url, "Database update")
                                val html = response.data?.toString() ?: error(response.message ?: "WCO returned no catalog")
                                val titles = parseIndex(html, url)
                                require(titles.isNotEmpty()) { "No titles returned; saved database preserved." }
                                BoxHelper.addIdentityLinksWithSlug(titles.map { it.slug }, category)
                                val existing = BoxHelper.allSeries.map { it.slug }.toMutableSet()
                                for (title in titles) {
                                    ensureActive()
                                    if (existing.add(title.slug)) {
                                        BoxMaker.makeSeries(title.slug, title.name, identity = category.type, addToHistory = false)
                                        added++
                                    }
                                }
                            }
                        }
                        completed++
                    } catch (e: CancellationException) {
                        if (e !is TimeoutCancellationException) throw e
                        failures.add("${category.name}: timed out")
                    } catch (e: Exception) { failures.add("${category.name}: ${e.message}") }
                }
                status = "Checked $completed/4 catalogs; added $added series. " +
                    if (failures.isEmpty()) "Open a series to load its episode details." else "Saved data kept. " + failures.joinToString("; ")
            } catch (_: CancellationException) { status = "Update cancelled. Saved series kept; $added new series added." }
            finally { busy = false; LibraryChanges.refresh() }
        }
    }

    fun refreshSeries(series: Series) {
        if (busy) return
        busy = true
        job = Core.taskScope.launch(Dispatchers.Main) {
            status = "Updating ${series.name} details and artwork…"
            try {
                val result = withTimeout(120_000) { SlugHandler().refreshSeries(series) }
                status = if (result.data != null) "Updated ${series.name}." else "Update failed: ${result.message}. Saved data kept."
            } catch (_: CancellationException) { status = "Update stopped. Saved data kept." }
            catch (e: Exception) { status = "Update failed: ${e.message}. Saved data kept." }
            finally { busy = false; LibraryChanges.refresh() }
        }
    }
}
