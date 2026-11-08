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
    private fun search() {
        busy = true
        val source = provider; val text = query
        Core.taskScope.launch(Dispatchers.Main) {
            try { results = WatchCatalog.search(text, source); status = "${results.size} series found" }
            catch (e: Exception) { status = e.message.orEmpty() }
            finally { busy = false }
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
                }
                Text(if (busy) "Searching ${provider.label}…" else status)
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
