package nobility.downloader.ui.views

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.Outline
import compose.icons.evaicons.fill.Bookmark
import compose.icons.evaicons.fill.Star
import compose.icons.evaicons.outline.Star
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.Page
import nobility.downloader.core.*
import nobility.downloader.core.entities.Series
import nobility.downloader.core.scraper.FollowSeries
import nobility.downloader.core.scraper.WatchCatalog
import nobility.downloader.ui.components.DefaultImage
import nobility.downloader.ui.components.Tooltip
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.Tools
import java.text.DateFormat
import java.util.Date

class FollowSeriesView : ViewPage {
    override val page = Page.FOLLOW

    @OptIn(ExperimentalLayoutApi::class)
    @Composable override fun Ui(windowScope: AppWindowScope) {
        val newOnly = Core.currentPage == Page.NEW_EPISODES
        val revision by LibraryChanges.revision.collectAsState()
        var reverse by remember { mutableStateOf(false) }
        val visible = FollowSeries.items.filter { !newOnly || it.unread.isNotEmpty() }
            .sortedBy { it.show.title.lowercase() }.let { if (reverse) it.reversed() else it }
        val listState = rememberLazyListState()
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (newOnly) "New episodes" else "Followed", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = { FollowSeries.checkAll() }, enabled = !FollowSeries.checking && FollowSeries.items.isNotEmpty()) {
                    Text(if (FollowSeries.checking) "Checking…" else "Check now")
                }
            }
            Text("Followed series are checked for new episodes each time the app opens.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { reverse = !reverse }) { Text(if (reverse) "Sort: Z–A" else "Sort: A–Z") }
                Text("${visible.size} series", style = MaterialTheme.typography.bodySmall)
            }
            if (visible.isEmpty()) Text(if (newOnly) "No new episodes. Followed shows will be checked at startup." else "No followed series yet. Use the bookmark beside a series in Browse.")
            Box(Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
                    items(visible, key = { FollowSeries.key(it.show) }) { follow ->
                        val isWco = follow.show.provider == WatchCatalog.Provider.WCO
                        val cached by produceState<Series?>(null, follow.show.url, revision) {
                            value = if (isWco) withContext(Dispatchers.IO) { BoxHelper.seriesForSlug(WatchCatalog.slug(follow.show.url)) } else null
                        }
                        val artwork = cached ?: remember(follow.show) {
                            Series(slug = WatchCatalog.slug(follow.show.url), name = follow.show.title)
                        }
                        val favorite = remember(cached, revision) { cached?.let { BoxHelper.isSeriesFavorited(it) } ?: false }
                        var expanded by remember(follow.show.url) { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                DefaultImage(artwork.imagePath, artwork.imageLink, series = if (isWco) artwork else null,
                                    contentScale = ContentScale.Fit, modifier = Modifier.size(70.dp, 100.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(follow.show.title, style = MaterialTheme.typography.titleMedium)
                                    Text("${follow.show.provider.label} • " + if (!follow.initialized) "Waiting for first check…" else if (follow.unread.isEmpty()) "No new episodes" else "${follow.unread.size} new episode(s)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (follow.unread.isNotEmpty()) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                                    if (follow.error.isNotBlank()) Text(follow.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        cached?.let { series ->
                                            Tooltip(if (favorite) "Remove from favorites" else "Add to favorites") {
                                                IconButton(onClick = {
                                                    if (BoxHelper.isSeriesFavorited(series)) BoxHelper.removeSeriesFavorite(series.slug)
                                                    else BoxMaker.makeFavorite(series.slug)
                                                }) { Icon(if (favorite) EvaIcons.Fill.Star else EvaIcons.Outline.Star, "Favorite", tint = if (favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                                            }
                                        }
                                        Tooltip("Unfollow series") {
                                            IconButton(onClick = { FollowSeries.toggle(follow.show) }) { Icon(EvaIcons.Fill.Bookmark, "Unfollow", tint = MaterialTheme.colorScheme.primary) }
                                        }
                                        Button(onClick = { Core.changePage(Page.WATCH); Core.watchView.openShow(follow.show) }) { Text("Watch") }
                                        if (follow.show.provider == WatchCatalog.Provider.KIMCARTOON) OutlinedButton(onClick = {
                                            Core.changePage(Page.DOWNLOADER); Core.downloaderView.openKimCartoon(follow.show)
                                        }) { Text("Download") }
                                        if (isWco) OutlinedButton(onClick = { Core.openSeriesDetails(WatchCatalog.slug(follow.show.url), windowScope) }) { Text("Download") }
                                        if (follow.unread.isNotEmpty()) TextButton(onClick = { FollowSeries.markSeen(follow.show) }) { Text("Mark seen") }
                                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less" else "Details") }
                                    }
                                }
                            }
                            if (expanded) {
                                if (follow.checkedAt > 0) Text("Last successful check: ${DateFormat.getDateTimeInstance().format(Date(follow.checkedAt))}", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                                cached?.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                follow.unread.forEach { Text("• ${it.title}", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        HorizontalDivider()
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}
