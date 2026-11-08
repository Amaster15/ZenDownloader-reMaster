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
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.VideoPlayer
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.core.scraper.WatchCatalog
import nobility.downloader.core.scraper.player.WatchPlayback
import nobility.downloader.core.settings.Quality
import nobility.downloader.ui.windows.utils.AppWindowScope
import java.text.DateFormat
import java.util.Date

class WatchView : ViewPage {
    override val page = Page.WATCH
    private var query by mutableStateOf("")
    private var provider by mutableStateOf(WatchCatalog.Provider.WCO)
    private var results by mutableStateOf<List<WatchCatalog.Show>>(emptyList())
    private var selected by mutableStateOf<WatchCatalog.Show?>(null)
    private var listing by mutableStateOf<WatchCatalog.Listing?>(null)
    private var filter by mutableStateOf("")
    private var language by mutableStateOf("All")
    private var includeKimSubtitles by mutableStateOf(true)
    private var quality by mutableStateOf(Quality.MED)
    private var status by mutableStateOf("Search for a series, choose an episode, and press Play.")
    private var error by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var job: Job? = null
    private var sequence = 0

    private fun runTask(block: suspend () -> Unit) {
        val request = ++sequence
        job?.cancel()
        busy = true
        error = false
        job = Core.taskScope.launch(Dispatchers.Main) {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = true; status = e.message ?: "The provider could not complete this request." }
            finally { if (sequence == request) busy = false }
        }
    }
    override fun onClose() { sequence++; job?.cancel(); busy = false }

    fun openShow(show: WatchCatalog.Show) {
        provider = show.provider
        results = listOf(show)
        load(show)
    }

    private fun search() = runTask {
        selected = null
        listing = null
        results = emptyList()
        status = "Searching ${provider.name}…"
        results = WatchCatalog.search(query, provider)
        status = if (results.isEmpty()) "No series found. Try another title or paste a series URL."
            else "${results.size} series found. Select a series to see its episodes."
    }
    private fun load(show: WatchCatalog.Show, refresh: Boolean = false) = runTask {
        selected = show
        filter = ""
        val cached = withContext(Dispatchers.IO) { WatchCatalog.cached(show) }
        listing = cached
        if (!refresh && cached != null && WatchCatalog.fresh(cached)) {
            status = "Loaded saved episode list. Use Refresh to check for new episodes."
        } else {
            status = if (cached == null) "Loading episodes…" else "Showing saved episodes while checking for updates…"
            listing = WatchCatalog.episodes(show)
            status = "Episode list refreshed and saved."
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Ui(windowScope: AppWindowScope) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Watch", style = MaterialTheme.typography.headlineSmall)
            Text("Play videos in your configured player without opening the provider's advertising pages.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchCatalog.Provider.entries.forEach { p ->
                    FilterChip(selected = provider == p, enabled = !busy, onClick = {
                        provider = p; results = emptyList(); selected = null; listing = null
                    }, label = { Text(p.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, label = { Text("Series title or series URL") },
                    singleLine = true, enabled = !busy, modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                            if (event.type == KeyEventType.KeyUp && !busy && query.isNotBlank()) search()
                            true
                        } else false
                    })
                Button(onClick = { search() }, enabled = !busy && query.isNotBlank()) { Text("Search") }
                TextButton(onClick = { Core.openSettings() }) { Text("Player settings") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                var player by remember { mutableStateOf(VideoPlayer.playerForSetting()) }
                Text("Player:")
                VideoPlayer.entries.forEach { choice ->
                    FilterChip(selected = player == choice, enabled = !busy, onClick = {
                        Defaults.VIDEO_PLAYER.update(choice.name)
                        player = choice
                    }, label = { Text(choice.name) })
                }
                TextButton(onClick = { DialogHelper.showLinkPrompt("https://www.videolan.org/vlc/", prompt = false) }) {
                    Text("Get VLC")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    TextButton(onClick = { job?.cancel(); status = "Cancelled." }) { Text("Cancel") }
                }
                Text(status, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LazyColumn(Modifier.weight(0.38f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(results, key = { it.url }) { show ->
                        OutlinedButton(onClick = { load(show) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text((if (selected?.url == show.url) "▶ " else "") + show.title)
                                val alternate = nobility.downloader.core.scraper.AnimeTitleIndex.alternateTitle(show)
                                if (alternate.isNotBlank()) Text("Site name: $alternate", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Column(Modifier.weight(0.62f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val show = selected
                    if (show != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(show.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            if (show.provider == WatchCatalog.Provider.KIMCARTOON) TextButton(enabled = !busy, onClick = {
                                nobility.downloader.core.scraper.ProviderFavorites.toggle(show)
                            }) { Text(if (nobility.downloader.core.scraper.ProviderFavorites.contains(show)) "★ Favorited" else "☆ Favorite") }
                            TextButton(enabled = !busy, onClick = { nobility.downloader.core.scraper.FollowSeries.toggle(show, listing?.entries.orEmpty()) }) {
                                Text(if (nobility.downloader.core.scraper.FollowSeries.isFollowing(show)) "Unfollow" else "Follow")
                            }
                            TextButton(onClick = { load(show, true) }, enabled = !busy) { Text("Refresh episodes") }
                        }
                        listing?.let { Text("Saved: ${DateFormat.getDateTimeInstance().format(Date(it.fetchedAt))}",
                            style = MaterialTheme.typography.bodySmall) }
                        OutlinedTextField(filter, { filter = it }, label = { Text("Filter episodes") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("All", "Dub", "Sub").forEach { lang ->
                                FilterChip(language == lang, { language = lang }, label = { Text(lang) })
                            }
                            if (show.provider == WatchCatalog.Provider.ANIMEFREAK) Text("Source quality")
                            else Quality.entries.forEach { q ->
                                FilterChip(quality == q, { quality = q }, label = { Text(if (show.provider == WatchCatalog.Provider.KIMCARTOON && q == Quality.LOW) "360p" else q.tag) })
                            }
                        }
                        if (show.provider == WatchCatalog.Provider.KIMCARTOON) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(includeKimSubtitles, { includeKimSubtitles = it }, enabled = !busy)
                                Text("Include English subtitles in downloads")
                            }
                            Text("Sub/dub buttons are supplied by the site; some files may be unavailable.", style = MaterialTheme.typography.bodySmall)
                        }
                        val entries = listing?.entries.orEmpty().filter {
                            it.title.contains(filter, true) && (language == "All" ||
                                it.language.equals(language, true) || it.title.contains(language, true))
                        }
                        Text("${entries.size} episodes")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(entries, key = { it.url + it.language }) { entry ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    val progressKey = "${show.provider}|${entry.url}|${entry.language}"
                                    val watched = nobility.downloader.core.maintenance.WatchProgress.get(progressKey)
                                    Text(entry.title + if (watched.watched) " • Watched" else if (watched.seconds > 0) " • Resume ${watched.seconds.toInt()/60}:${(watched.seconds.toInt()%60).toString().padStart(2, '0')}" else "", modifier = Modifier.weight(1f))
                                    TextButton(onClick = { nobility.downloader.core.maintenance.WatchProgress.mark(progressKey, !watched.watched) }) { Text(if (watched.watched) "Unwatched" else "Mark watched") }
                                    if (watched.seconds > 0 && !watched.watched) TextButton(enabled = !busy, onClick = { nobility.downloader.core.maintenance.WatchProgress.reset(progressKey) }) { Text("Start over") }
                                    Button(enabled = !busy, onClick = {
                                        runTask {
                                            val playbackRequest = sequence
                                            WatchPlayback.play(show, entry, quality) { message ->
                                                withContext(Dispatchers.Main) { if (sequence == playbackRequest) status = message }
                                            }
                                            status = "${VideoPlayer.playerForSetting()} started for ${entry.title}."
                                        }
                                    }) { Text(if (watched.seconds > 0 && !watched.watched) "Resume" else "Play") }
                                    if (show.provider in listOf(WatchCatalog.Provider.KIMCARTOON, WatchCatalog.Provider.ANIMEFREAK, WatchCatalog.Provider.HIANIME)) {
                                        TextButton(enabled = !busy && !DirectDownloads.isBusy, onClick = {
                                            DirectDownloads.startEpisode(show, entry, quality, includeKimSubtitles)
                                        }) { Text("Download") }
                                    }
                                }
                            }
                        }
                    } else {
                        Text("Choose a series to browse its episodes. HiAnime currently uses the Ayame player when available.")
                    }
                }
            }
        }
    }
}
