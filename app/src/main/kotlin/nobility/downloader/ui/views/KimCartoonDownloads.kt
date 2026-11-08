package nobility.downloader.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.*
import nobility.downloader.core.Core
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.scraper.KimCartoon
import nobility.downloader.core.scraper.WatchCatalog
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import java.io.File

/** Provider batch remains alive while navigating the main tabs. */
class KimCartoonDownloads {
    private var input by mutableStateOf("")
    private var results by mutableStateOf<List<WatchCatalog.Show>>(emptyList())
    private var show by mutableStateOf<WatchCatalog.Show?>(null)
    private var entries by mutableStateOf<List<WatchCatalog.Entry>>(emptyList())
    private val selected = mutableStateMapOf<String, Boolean>()
    private val outcomes = mutableStateMapOf<String, String>()
    private var language by mutableStateOf("sub")
    private var quality by mutableStateOf(Quality.MED)
    private var includeSubtitles by mutableStateOf(true)
    private var busy by mutableStateOf(false)
    private var failed by mutableStateOf(false)
    private var status by mutableStateOf("Search a title or paste a KimCartoon series/episode URL.")
    private var job: Job? = null
    private fun key(entry: WatchCatalog.Entry) = entry.url + "|" + entry.language
    private fun task(block: suspend () -> Unit) {
        busy = true; failed = false
        job = Core.taskScope.launch(Dispatchers.Main) {
            try { block() }
            catch (e: CancellationException) { status = "Cancelled. Completed files were kept."; throw e }
            catch (e: Exception) { failed = true; status = e.message.orEmpty() }
            finally { busy = false }
        }
    }
    private fun search() = task {
        status = "Searching KimCartoon…"
        results = WatchCatalog.search(input, WatchCatalog.Provider.KIMCARTOON)
        status = "${results.size} series found. Choose one to load episodes."
    }
    fun openShow(value: WatchCatalog.Show) { if (!busy) { input = value.url; results = listOf(value); load(value) } }
    private fun load(value: WatchCatalog.Show) = task {
        status = "Loading episodes…"
        val listing = WatchCatalog.episodes(value)
        show = value; entries = listing.entries; selected.clear(); outcomes.clear()
        entries.filter { it.language == language }.forEach { selected[key(it)] = true }
        status = "Loaded ${entries.map { it.url }.distinct().size} episodes. Some advertised players may be unavailable."
    }
    @OptIn(ExperimentalLayoutApi::class)
    @Composable fun Ui() {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("KimCartoon downloads", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(input, { input = it }, label = { Text("Series title or KimCartoon URL") }, singleLine = true,
                enabled = !busy, modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
                    if (it.key == Key.Enter || it.key == Key.NumPadEnter) {
                        if (it.type == KeyEventType.KeyUp && !busy && input.isNotBlank()) search()
                        true
                    } else false
                })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ search() }, enabled = !busy && input.isNotBlank()) { Text("Search / Load URL") }
                listOf("sub", "dub").forEach { lang ->
                    FilterChip(language == lang, { language = lang; selected.clear() }, enabled = !busy, label = { Text(lang.uppercase()) })
                }
                Quality.entries.forEach { q -> FilterChip(quality == q, { quality = q }, enabled = !busy,
                    label = { Text(if (q == Quality.LOW) "360p" else q.tag) }) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeSubtitles, { includeSubtitles = it }, enabled = !busy)
                    Text("Include English subtitles")
                }
            }
            if (results.isNotEmpty()) {
                LazyColumn(Modifier.heightIn(max = 130.dp)) {
                    items(results, key = { it.url }) { value ->
                        TextButton({ nobility.downloader.core.scraper.ProviderFavorites.toggle(value) }, enabled = !busy) {
                        Text(if (nobility.downloader.core.scraper.ProviderFavorites.contains(value)) "★ Favorited" else "☆ Favorite")
                    }
                    TextButton({ nobility.downloader.core.scraper.FollowSeries.toggle(value, entries) }, enabled = !busy) {
                        Text(if (nobility.downloader.core.scraper.FollowSeries.isFollowing(value)) "Unfollow" else "Follow")
                    }
                    TextButton({ load(value) }, enabled = !busy) { Text(value.title) }
                    }
                }
            }
            show?.let { value ->
                Text(value.title, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ load(value) }, enabled = !busy) { Text("Refresh episodes") }
                    TextButton({ entries.filter { it.language == language }.forEach { selected[key(it)] = true } }, enabled = !busy) { Text("Select all") }
                    TextButton({ selected.clear() }, enabled = !busy) { Text("Clear selection") }
                    Button(enabled = !busy && selected.values.any { it }, onClick = {
                        val chosen = entries.filter { selected[key(it)] == true && it.language == language }
                        val chosenQuality = quality; val captions = includeSubtitles
                        task {
                            var downloaded = 0; var skipped = 0; var errors = 0
                            for (episode in chosen) {
                                currentCoroutineContext().ensureActive()
                                val output = File(Defaults.SAVE_FOLDER.string(), KimCartoon.filename(value, episode))
                                if (output.exists()) { outcomes[key(episode)] = "Already exists"; skipped++; continue }
                                try {
                                    outcomes[key(episode)] = "Downloading…"
                                    KimCartoon.download(value, episode, chosenQuality, File(Defaults.SAVE_FOLDER.string()), captions) { message ->
                                        withContext(Dispatchers.Main) { status = message }
                                    }
                                    outcomes[key(episode)] = "Downloaded"; downloaded++
                                } catch (e: CancellationException) { outcomes[key(episode)] = "Cancelled"; throw e }
                                catch (e: Exception) { outcomes[key(episode)] = "Failed: ${e.message}"; errors++; failed = true }
                            }
                            status = "Finished: $downloaded downloaded, $skipped existing, $errors failed."
                        }
                    }) { Text("Download selected") }
                }
            }
            if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton({ job?.cancel() }) { Text("Cancel") } }
            Text(status, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text("Save to: ${Defaults.SAVE_FOLDER.string()}", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f)) {
                items(entries.filter { it.language == language }, key = { key(it) }) { episode ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(selected[key(episode)] == true, { selected[key(episode)] = it }, enabled = !busy)
                        Column { Text(episode.title); outcomes[key(episode)]?.let { Text(it) } }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
