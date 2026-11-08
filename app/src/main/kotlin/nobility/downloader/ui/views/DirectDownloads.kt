package nobility.downloader.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import nobility.downloader.Page
import nobility.downloader.core.Core
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.entities.Download
import nobility.downloader.core.scraper.*
import nobility.downloader.core.scraper.video_download.HiAnimeFallback
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.VideoUtil
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.EpisodeNaming
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.update
import java.io.File
import java.net.URI

object DirectDownloads {
    private var input by mutableStateOf("")
    private var status by mutableStateOf("Paste an episode link to download, or a series link to select episodes.")
    private var busy by mutableStateOf(false)
    private var show by mutableStateOf<WatchCatalog.Show?>(null)
    private var entries by mutableStateOf<List<WatchCatalog.Entry>>(emptyList())
    private val selected = mutableStateMapOf<String, Boolean>()
    private var quality by mutableStateOf(Quality.MED)
    private var subtitles by mutableStateOf(true)
    private var language by mutableStateOf("sub")
    private var job: Job? = null
    fun cancel() { job?.cancel() }
    val isBusy get() = busy
    private fun key(e: WatchCatalog.Entry) = e.url + "|" + e.language
    private fun report(text: String) { status = text; FrogLog.message(text) }
    private fun task(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        job = Core.taskScope.launch(Dispatchers.Main) {
            try { action() }
            catch (e: CancellationException) { report("Direct download cancelled."); throw e }
            catch (e: Exception) { report("Direct download: ${e.message}") }
            finally { busy = false }
        }
    }
    fun open(value: WatchCatalog.Show) {
        Core.changePage(Page.DIRECT_LINK)
        if (!busy) { input = value.url; task { load(value) } }
    }
    private suspend fun load(value: WatchCatalog.Show) {
        report("Loading ${value.title} from ${value.provider.label}…")
        show = value; entries = emptyList(); selected.clear()
        entries = WatchCatalog.episodes(value).entries
        report("Choose episodes to download from ${value.provider.label}.")
    }
    private fun submit() = task {
        val provider = DownloadProvider.detect(input)
        val parsed = URI(input.trim())
        val url = URI("https", null, parsed.host.lowercase().removePrefix("www."), -1,
            parsed.path, parsed.query, null).toString()
        show = null; entries = emptyList(); selected.clear()
        report("Detected ${provider.label}. Resolving link…")
        if (provider == WatchCatalog.Provider.WCO) {
            Core.currentUrl = url; Core.child.start(); Core.changePage(Page.DOWNLOADER)
            return@task
        }
        val path = URI(url).path.trimEnd('/')
        if (provider == WatchCatalog.Provider.HIANIME && path.startsWith("/watch-")) {
            val title = path.removePrefix("/watch-").substringBeforeLast('-').replace('-', ' ')
            val value = WatchCatalog.Show(title.substringBefore(" episode "), url, provider)
            download(value, listOf(WatchCatalog.Entry(title, url, language)))
        } else {
            val value = WatchCatalog.search(url, provider).firstOrNull() ?: error("Series not found")
            load(value)
            val episodeLink = when (provider) {
                WatchCatalog.Provider.ANIMEFREAK -> path.contains("/episode/")
                WatchCatalog.Provider.KIMCARTOON -> !path.startsWith("/anime/")
                else -> false
            }
            if (episodeLink) {
                val chosen = entries.filter { URI(it.url).path.trimEnd('/') == path && (it.language.isBlank() || it.language == language || provider == WatchCatalog.Provider.ANIMEFREAK) }
                require(chosen.isNotEmpty()) { "No matching episode for this link and language." }
                download(value, chosen)
            }
        }
    }
    private suspend fun download(value: WatchCatalog.Show, chosen: List<WatchCatalog.Entry>) {
        val folderPath = Defaults.SAVE_FOLDER.string()
        require(folderPath.isNotBlank()) { "Choose a download folder in Settings." }
        val folder = File(folderPath)
        require(folder.path.isNotBlank()) { "Choose a download folder in Settings." }
        val q = quality; val captions = subtitles
        Core.changePage(Page.DOWNLOADER)
        var done = 0
        for (entry in chosen) {
            currentCoroutineContext().ensureActive()
            val filename = when (value.provider) {
                WatchCatalog.Provider.KIMCARTOON -> KimCartoon.filename(value, entry)
                WatchCatalog.Provider.ANIMEFREAK -> AnimeFreak.filename(AnimeFreak.Series(value.title, emptyList()), AnimeFreak.Episode(URI(entry.url).path.trimEnd('/').substringAfterLast('/').toInt(), entry.url))
                else -> EpisodeNaming.downloadFilename(entry.title, entry.url, value.title, false).replace(Regex("[\\\\/:*?\"<>|]"), "_") + " - ${entry.language.uppercase()}.mp4"
            }
            val output = File(folder, filename)
            var record = Download(output.absolutePath, System.currentTimeMillis(), q.resolution).apply {
                name = filename.removeSuffix(".mp4"); slug = key(entry); seriesSlug = value.title
                queued = true
            }
            Core.child.addDownload(record)
            record = Core.child.downloadList.first { it.matches(record) }
            if (output.exists()) {
                record.fileSize = output.length(); record.queued = false; record.update()
                report("${value.title}: ${entry.title} already exists; skipped.")
                continue
            }
            record.queued = false; record.downloading = true; record.manualProgress = true
            var lastLog = 0L
            fun updateProgress(text: String) {
                record.setProgress(text)
                val now = System.currentTimeMillis()
                if (now - lastLog >= 10000) {
                    report("${value.title} — ${entry.title}: $text")
                    lastLog = now
                }
            }
            suspend fun progress(text: String) = withContext(Dispatchers.Main) { updateProgress(text) }
            try {
                report("${value.provider.label}: downloading ${value.title} — ${entry.title}")
                record.setProgress("Resolving video link…")
                when (value.provider) {
                    WatchCatalog.Provider.KIMCARTOON -> KimCartoon.download(value, entry, q, folder, captions) { progress(it) }
                    WatchCatalog.Provider.ANIMEFREAK -> {
                        val ep = AnimeFreak.Episode(URI(entry.url).path.trimEnd('/').substringAfterLast('/').toInt(), entry.url)
                        val stream = AnimeFreak.resolve(ep)
                        AnimeFreak.download(stream, output) { text -> Core.taskScope.launch(Dispatchers.Main) { updateProgress(text) } }
                    }
                    WatchCatalog.Provider.HIANIME -> {
                        val client = HiAnimeFallback()
                        val stream = client.resolve(client.watchMatch(entry.url, entry.language.ifBlank { language }), q)
                        val owner = currentCoroutineContext()
                        withContext(Dispatchers.IO) {
                            client.download(stream, output, VideoUtil.loadFfmpegPath(), { owner.isActive }) { percent ->
                                Core.taskScope.launch(Dispatchers.Main) { updateProgress("$percent%") }
                            }
                        }
                    }
                    else -> error("Use the WCO queue")
                }
                record.fileSize = output.length(); record.downloading = false; record.manualProgress = false; record.update()
                report("Downloaded ${output.name}"); done++
            } catch (e: CancellationException) {
                record.downloading = false; record.setProgress("Cancelled"); record.update(false); throw e
            } catch (e: Exception) {
                record.downloading = false; record.setProgress("Failed: ${e.message}"); record.update(false)
                report("Failed ${entry.title}: ${e.message}")
            }
        }
        report("Finished: $done of ${chosen.size} downloaded.")
    }
    @OptIn(ExperimentalLayoutApi::class)
    @Composable fun Ui() {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Direct link", style = MaterialTheme.typography.headlineSmall)
            Text("WCO • HiAnime • AnimeFreak • KimCartoon — detected automatically")
            OutlinedTextField(input, { input = it }, enabled = !busy, label = { Text("Episode or series URL") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
                    if (it.key == Key.Enter || it.key == Key.NumPadEnter) {
                        if (it.type == KeyEventType.KeyUp && !busy && input.isNotBlank()) submit()
                        true
                    } else false
                })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ submit() }, enabled = !busy && input.isNotBlank()) { Text("Download link") }
                listOf("sub", "dub").forEach { lang -> FilterChip(language == lang, { language = lang; selected.clear() }, enabled = !busy, label = { Text(lang.uppercase()) }) }
                Quality.entries.forEach { q -> FilterChip(quality == q, { quality = q }, enabled = !busy, label = { Text(q.tag) }) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(subtitles, { subtitles = it }, enabled = !busy); Text("Include subtitles when available")
                }
                if (busy) TextButton({ job?.cancel() }) { Text("Cancel") }
            }
            Text("Separate subtitle tracks are currently available for KimCartoon. Embedded subtitles cannot be removed.", style = MaterialTheme.typography.bodySmall)
            Text(status)
            show?.let { value ->
                val visible = entries.filter { it.language.isBlank() || it.language == language || value.provider == WatchCatalog.Provider.ANIMEFREAK }
                Text(value.title, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ visible.forEach { selected[key(it)] = true } }, enabled = !busy) { Text("Select all") }
                    TextButton({ selected.clear() }, enabled = !busy) { Text("Clear") }
                    Button({ val chosen = visible.filter { selected[key(it)] == true }; task { download(value, chosen) } }, enabled = !busy && visible.any { selected[key(it)] == true }) { Text("Download selected") }
                }
                LazyColumn(Modifier.weight(1f)) { items(visible, key = { key(it) }) { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(selected[key(entry)] == true, { selected[key(entry)] = it }, enabled = !busy)
                        Text(entry.title)
                    }
                } }
            }
        }
    }
}
