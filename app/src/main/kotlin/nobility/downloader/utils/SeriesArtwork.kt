package nobility.downloader.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.entities.Series
import nobility.downloader.core.scraper.video_download.Functions
import org.jsoup.Jsoup
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** New catalog entries may have only a name and URL. Resolve their artwork when displayed. */
object SeriesArtwork {
    private val slots = Semaphore(2)
    private val attempted = ConcurrentHashMap<String, Long>()

    @JvmStatic fun imageUrl(html: String, base: String): String {
        val doc = Jsoup.parse(html, base)
        val image = doc.selectFirst("img.img5")
        return listOfNotNull(image?.absUrl("src"), image?.absUrl("data-src"),
            doc.selectFirst("meta[property=og:image]")?.absUrl("content"))
            .firstOrNull { it.startsWith("https://") || it.startsWith("http://") }.orEmpty()
    }

    suspend fun ensure(series: Series): Boolean = withContext(Dispatchers.IO) {
        val file = File(series.imagePath)
        if (ThumbnailCache.ensure(file, series.imageLink)) return@withContext true
        slots.withPermit {
            if (ThumbnailCache.valid(file)) return@withPermit true
            val now = System.currentTimeMillis()
            val previous = attempted[series.slug]
            if (previous != null && now - previous < 10 * 60_000) return@withPermit false
            attempted[series.slug] = now
            try {
                withTimeout(90_000) {
                    val url = series.slug.slugToLink()
                    val response = Functions.readUrlLines(url, "Series thumbnail")
                    val html = response.data?.toString() ?: return@withTimeout false
                    val image = imageUrl(html, url)
                    if (image.isBlank()) return@withTimeout false
                    // Re-read the entity so an artwork fetch cannot overwrite a concurrent episode refresh.
                    BoxHelper.seriesForSlug(series.slug)?.let { saved ->
                        saved.imageLink = image
                        BoxHelper.addSeries(saved)
                    }
                    ThumbnailCache.ensure(file, image)
                }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) { attempted.remove(series.slug); throw e }
                false
            } catch (_: Exception) { false }
        }
    }
}
