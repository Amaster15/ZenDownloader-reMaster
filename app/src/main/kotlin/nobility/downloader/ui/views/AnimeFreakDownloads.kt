package nobility.downloader.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import nobility.downloader.core.Core
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.scraper.AnimeFreak
import nobility.downloader.core.settings.Defaults
import java.io.File

/** Separate provider batch; navigating between main tabs does not cancel downloads. */
class AnimeFreakDownloads {
    private var url by mutableStateOf("")
    private var series by mutableStateOf<AnimeFreak.Series?>(null)
    private val selected = mutableStateMapOf<Int, Boolean>()
    private val results = mutableStateMapOf<Int, String>()
    private var status by mutableStateOf("Paste an AnimeFreak series URL to load its episodes.")
    private var busy by mutableStateOf(false)
    private var failed by mutableStateOf(false)
    private var job: Job? = null

    private fun task(action: suspend () -> Unit) {
        busy = true; failed = false
        job = Core.taskScope.launch(Dispatchers.Main) {
            try { action() }
            catch (e: CancellationException) { status = "Cancelled. Completed files were kept; partial files were removed."; throw e }
            catch (e: Exception) { failed = true; status = e.message ?: "AnimeFreak is unavailable." }
            finally { busy = false }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable fun Ui() {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AnimeFreak downloads", style = MaterialTheme.typography.titleLarge)
            Text("Select episodes to download from EchoVideo. Chrome/Chromium and FFmpeg are required.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(url, { url = it }, enabled = !busy, singleLine = true,
                label = { Text("AnimeFreak series or episode URL") }, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy && url.isNotBlank(), onClick = {
                    task {
                        status = "Loading episode list. Waiting for AnimeFreak's browser verification…"
                        val loaded = AnimeFreak.load(url)
                        series = loaded; selected.clear(); results.clear()
                        loaded.episodes.forEach { selected[it.number] = true }
                        status = "Found ${loaded.episodes.size} episodes."
                    }
                }) { Text("Load episodes") }
                TextButton(enabled = !busy && series != null, onClick = { series?.episodes?.forEach { selected[it.number] = true } }) { Text("Select all") }
                TextButton(enabled = !busy && series != null, onClick = { selected.clear() }) { Text("Clear selection") }
                Button(enabled = !busy && selected.values.any { it }, onClick = {
                    val show = series ?: return@Button
                    val episodes = show.episodes.filter { selected[it.number] == true }
                    val folder = File(Defaults.SAVE_FOLDER.string())
                    task {
                        require(folder.path.isNotBlank()) { "Choose a download folder in Settings first." }
                        var count = 0
                        for (episode in episodes) {
                            currentCoroutineContext().ensureActive()
                            val output = File(folder, AnimeFreak.filename(show, episode))
                            if (output.exists()) { results[episode.number] = "Already exists"; continue }
                            try {
                                results[episode.number] = "Resolving player…"
                                status = "Episode ${episode.number}: waiting for the player and a fresh video link…"
                                val stream = AnimeFreak.resolve(episode)
                                AnimeFreak.download(stream, output) { status = it }
                                results[episode.number] = "Downloaded"; count++
                            } catch (e: CancellationException) {
                                results[episode.number] = "Cancelled"; throw e
                            } catch (e: Exception) {
                                results[episode.number] = "Failed: ${e.message}"; failed = true
                            }
                        }
                        status = "Finished: $count downloaded. See episode results below."
                    }
                }) { Text("Download selected") }
                if (busy) TextButton(onClick = { status = "Cancelling…"; job?.cancel() }) { Text("Cancel") }
            }
            Text("Save to: ${Defaults.SAVE_FOLDER.string()}", style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            series?.let { show ->
                Text(show.title, style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.weight(1f)) {
                    items(show.episodes, key = { it.number }) { episode ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(selected[episode.number] == true, { selected[episode.number] = it }, enabled = !busy)
                            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                Text("Episode ${episode.number}")
                                Text(AnimeFreak.filename(show, episode), style = MaterialTheme.typography.bodySmall)
                                results[episode.number]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
