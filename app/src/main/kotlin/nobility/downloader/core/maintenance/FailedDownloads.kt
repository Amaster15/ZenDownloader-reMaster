package nobility.downloader.core.maintenance

import AppInfo
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.scraper.WatchCatalog
import nobility.downloader.core.settings.Quality
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object FailedDownloads {
    data class Request(val provider: WatchCatalog.Provider, val series: String, val seriesUrl: String,
        val title: String, val url: String, val language: String = "", val quality: Quality = Quality.LOW,
        val subtitles: Boolean = false, val movie: Boolean = false, val reason: String = "") {
        val key get() = "$url|$language"
    }
    private val gson = Gson()
    private val file get() = File(AppInfo.databasePath, "my_data/failed-downloads.json")
    val requests = mutableStateListOf<Request>()
    private var loaded = false
    @Synchronized fun load() {
        if (loaded) return
        loaded = true
        runCatching { gson.fromJson(file.readText(), Array<Request>::class.java).forEach {
            requests.add(if (it.reason.isBlank()) it.copy(reason = "Interrupted before validation finished") else it)
        } }
    }
    @Synchronized private fun save() {
        file.parentFile.mkdirs(); val temp = File(file.path + ".tmp")
        temp.writeText(gson.toJson(requests.toList()))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    @Synchronized fun begin(request: Request) { load(); requests.removeAll { it.key == request.key }; requests.add(request.copy(reason = "")); save() }
    @Synchronized fun fail(url: String, reason: String) {
        load(); requests.indices.filter { if (url.contains('|')) requests[it].key == url else requests[it].url == url }.forEach { requests[it] = requests[it].copy(reason = reason) }; save()
    }
    @Synchronized fun complete(url: String) { load(); requests.removeAll { if (url.contains('|')) it.key == url else it.url == url }; save() }
    fun wco(episode: Episode, quality: Quality?) = begin(Request(WatchCatalog.Provider.WCO,
        episode.seriesName,
        episode.seriesSlug, episode.name, episode.slug, quality = quality ?: Quality.LOW, movie = episode.isMovie))
    @Synchronized fun recover(downloads: List<nobility.downloader.core.entities.Download>) {
        load()
        for (download in downloads) {
            if (download.downloading || download.queued || download.downloadPath.isBlank()) continue
            val media = File(download.downloadPath)
            val invalid = VideoValidation.result(media)?.valid == false
            if (media.isFile && media.length() >= download.fileSize && download.fileSize > 5000 && !invalid) continue
            val url = download.slug.substringBefore('|')
            val language = download.slug.substringAfter('|', "")
            if (requests.any { it.key == "$url|$language" }) continue
            val provider = if (url.startsWith("https://")) runCatching { nobility.downloader.core.scraper.DownloadProvider.detect(url) }.getOrNull() ?: continue
                else WatchCatalog.Provider.WCO
            requests += Request(provider, if (provider == WatchCatalog.Provider.WCO) download.seriesName else download.seriesSlug,
                download.seriesSlug, download.name, url, language, Quality.qualityForResolution(download.resolution),
                reason = if (invalid) "Video validation failed" else "Missing or incomplete local file")
        }
        save()
    }
    fun retryWco(request: Request, fallback: Boolean) {
        Core.child.downloadThread.addRetry(Episode(request.title, request.url, request.seriesUrl).apply { isMovie = request.movie }, request.quality, fallback)
    }
}
