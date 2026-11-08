package nobility.downloader.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import nobility.downloader.Page
import nobility.downloader.core.Core
import nobility.downloader.core.scraper.WatchCatalog
import nobility.downloader.ui.windows.utils.AppWindowScope

object ProviderBrowse {
    private var provider by mutableStateOf(WatchCatalog.Provider.WCO)
    private var query by mutableStateOf("")
    private var results by mutableStateOf<List<WatchCatalog.Show>>(emptyList())
    private var status by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var showBrowser by mutableStateOf(false)
    private var searchJob: Job? = null
    private var elapsed by mutableStateOf(0)
    private fun search() {
        if (busy || query.isBlank()) return
        busy = true
        val source = provider; val text = query
        status = "Starting search"; elapsed = 0
        searchJob = Core.taskScope.launch(Dispatchers.Main) {
            val timer = launch { while (isActive) { delay(1000); elapsed++ } }
            try {
                results = withTimeout(if (showBrowser) 240_000 else 120_000) { WatchCatalog.search(text, source, showBrowser) }
                status = "${results.size} series found"
            } catch (e: TimeoutCancellationException) {
                status = "Search timed out. The provider may be waiting for browser verification. Please retry."
            } catch (e: CancellationException) {
                status = "Search cancelled."; throw e
            } catch (e: Exception) { status = e.message.orEmpty() }
            finally { timer.cancel(); busy = false }
        }
    }
    @OptIn(ExperimentalLayoutApi::class)
    @Composable fun Ui(scope: AppWindowScope) {
        Column(Modifier.fillMaxSize()) {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchCatalog.Provider.entries.forEach { p ->
                    FilterChip(provider == p, { provider = p; results = emptyList(); status = "" }, enabled = !busy, label = { Text(p.label) })
                }
            }
            if (provider == WatchCatalog.Provider.WCO) Box(Modifier.weight(1f)) { Core.databaseView.Ui(scope) }
            else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it }, label = { Text("Search ${provider.label}") }, singleLine = true,
                        enabled = !busy, modifier = Modifier.weight(1f).onPreviewKeyEvent {
                            if (it.key == Key.Enter || it.key == Key.NumPadEnter) {
                                if (it.type == KeyEventType.KeyUp && !busy && query.isNotBlank()) search()
                                true
                            } else false
                        })
                    Button({ search() }, enabled = !busy && query.isNotBlank()) { Text("Search") }
                    if (busy) OutlinedButton({ searchJob?.cancel(); status = "Cancelling search…" }) { Text("Cancel") }
                }
                if (provider == WatchCatalog.Provider.ANIMEFREAK) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(showBrowser, { showBrowser = it }, enabled = !busy)
                    Text("Show browser for site verification")
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (busy) "Searching ${provider.label} — ${elapsed}s. ${if (provider == WatchCatalog.Provider.ANIMEFREAK) if (showBrowser) "Complete any verification in the browser window." else "Waiting for the site / browser verification…" else ""}" else status)
                LazyColumn {
                    items(results, key = { it.url }) { show ->
                        Text(show.title, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ Core.changePage(Page.WATCH); Core.watchView.openShow(show) }) { Text("Watch") }
                            OutlinedButton({ DirectDownloads.open(show) }) { Text("Download") }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}
