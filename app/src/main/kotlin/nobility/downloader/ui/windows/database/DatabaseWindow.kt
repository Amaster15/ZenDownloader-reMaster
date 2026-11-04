package nobility.downloader.ui.windows.database

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.Outline
import compose.icons.evaicons.fill.*
import compose.icons.evaicons.outline.Star
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.scraper.DatabaseUpdater
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Series
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Save
import nobility.downloader.ui.components.*
import nobility.downloader.ui.windows.MovieEditorWindow
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Constants.mediumIconSize
import nobility.downloader.utils.Tools
import nobility.downloader.utils.hover
import nobility.downloader.utils.linkToSlug
import nobility.downloader.utils.slugToLink
import java.util.regex.Pattern

/**
 * Shared database and favorites tab, backed by the persisted series library.
 * @author NobilityDev
 */
class DatabaseWindow(private val favoritesOnly: Boolean = false) : nobility.downloader.ui.views.ViewPage {
    override val page = if (favoritesOnly) nobility.downloader.Page.FAVORITES else nobility.downloader.Page.DATABASE
    fun search(text: String) { mSearchText.value = text }


    private lateinit var windowScope: AppWindowScope
    private var loading by mutableStateOf(false)
    private var resultText by mutableStateOf("")

    private val mShowFavorites = MutableStateFlow(favoritesOnly)
    private val showFavorites = mShowFavorites.asStateFlow()

    private val mSearchByGenre = MutableStateFlow(
        Defaults.DB_SEARCH_GENRE.boolean()
    )
    private val searchByGenre = mSearchByGenre.asStateFlow()

    private val mSearchByDesc = MutableStateFlow(
        Defaults.DB_SEARCH_DESC.boolean()
    )
    private val searchByDesc = mSearchByDesc.asStateFlow()

    private val mDatabaseType = MutableStateFlow(
        if (favoritesOnly) DatabaseType.ALL else DatabaseType.typeForId(Defaults.DB_LAST_TYPE_USED.int())
    )
    private val databaseType = mDatabaseType.asStateFlow()

    private val mSearchText = if (favoritesOnly) MutableStateFlow("") else Core.databaseSearchText
    private val searchText = mSearchText.asStateFlow()

    @Suppress("TYPE_INTERSECTION_AS_REIFIED_WARNING")
    private val series = combine(
        databaseType,
        searchText,
        searchByGenre,
        searchByDesc,
        showFavorites,
        nobility.downloader.core.LibraryChanges.revision
    ) { seriesData ->
        // Each library revision re-reads the persisted catalog and favorite membership.
        val selectedType = seriesData[0] as DatabaseType
        val search = if (favoritesOnly) "" else seriesData[1] as String
        val byGenre = seriesData[2] as Boolean
        val byDesc = seriesData[3] as Boolean
        val favorite = seriesData[4] as Boolean
        val favoriteSlugs = BoxHelper.shared.favoriteBox.all.map { it.seriesSlug }.toSet()
        databaseForType(selectedType).filter {
            matches(it, search, byGenre, byDesc, favorite, favoriteSlugs)
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    private fun databaseForType(type: DatabaseType): List<Series> {
            return when (type) {
                DatabaseType.ALL -> BoxHelper.allSeries
                DatabaseType.DUBBED -> BoxHelper.dubbed
                DatabaseType.SUBBED -> BoxHelper.subbed
                DatabaseType.CARTOON -> BoxHelper.cartoons
                DatabaseType.MOVIE -> BoxHelper.movies
                DatabaseType.MISC -> BoxHelper.misc
            }
        }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
    @Composable
    override fun Ui(windowScope: AppWindowScope) {
        this.windowScope = windowScope
        LaunchedEffect(Unit) { nobility.downloader.core.LibraryChanges.refresh() }
        val revision by nobility.downloader.core.LibraryChanges.revision.collectAsState()
        val entries by series.collectAsState(initial = emptyList())
        val search by searchText.collectAsState()
        val type by databaseType.collectAsState()
        val byGenre by searchByGenre.collectAsState()
        val byDesc by searchByDesc.collectAsState()
        var reverse by remember { mutableStateOf(false) }
        val rows = remember(entries, reverse) { entries.sortedBy { it.name.lowercase() }.let { if (reverse) it.reversed() else it } }
        val listState = rememberLazyListState()
        LaunchedEffect(type, search, byGenre, byDesc, reverse) { listState.scrollToItem(0) }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (favoritesOnly) "Favorites" else "Browse series", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = { DatabaseUpdater.updateCatalog() }, enabled = !DatabaseUpdater.busy) { Text("Update catalog") }
                if (DatabaseUpdater.busy) TextButton(onClick = { DatabaseUpdater.cancel() }) { Text("Cancel") }
            }
            if (DatabaseUpdater.status.isNotBlank()) Text(DatabaseUpdater.status, style = MaterialTheme.typography.bodySmall)
            if (!favoritesOnly) OutlinedTextField(search, { mSearchText.value = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Search series name or URL") }, singleLine = true,
                trailingIcon = { if (search.isNotBlank()) TextButton(onClick = { mSearchText.value = "" }) { Text("Clear") } })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DatabaseType.entries.forEach { category ->
                    FilterChip(selected = type == category, onClick = {
                        mDatabaseType.value = category
                        if (!favoritesOnly) Defaults.DB_LAST_TYPE_USED.update(category.id)
                    }, label = { Text(category.title) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!favoritesOnly) FilterChip(selected = byGenre, onClick = { mSearchByGenre.value = !byGenre; Defaults.DB_SEARCH_GENRE.update(!byGenre) }, label = { Text("Include genres") })
                if (!favoritesOnly) FilterChip(selected = byDesc, onClick = { mSearchByDesc.value = !byDesc; Defaults.DB_SEARCH_DESC.update(!byDesc) }, label = { Text("Include descriptions") })
                TextButton(onClick = { reverse = !reverse }) { Text(if (reverse) "Sort: Z–A" else "Sort: A–Z") }
                Text("${rows.size} series", modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall)
            }
            if (rows.isEmpty()) Text(if (favoritesOnly) "No matching favorites. Use the star beside a series in Browse." else "No matches in your saved catalog. Update the catalog or try a series URL in Downloads.")
            Box(Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
                    items(rows, key = { "${it.identity}:${it.slug}:${it.id}" }) { item ->
                        val favorite = remember(item.slug, revision) { BoxHelper.isSeriesFavorited(item) }
                        val show = followShow(item)
                        val followed = nobility.downloader.core.scraper.FollowSeries.isFollowing(show)
                        var expanded by remember(item.slug) { mutableStateOf(false) }
                        fun favoriteAction() {
                            if (BoxHelper.isSeriesFavorited(item)) BoxHelper.removeSeriesFavorite(item.slug)
                            else BoxMaker.makeFavorite(item.slug)
                        }
                        ContextMenuArea(items = { buildList {
                            add(ContextMenuItem("Download") { Core.openSeriesDetails(item.slug, windowScope) })
                            add(ContextMenuItem(if (favorite) "Remove from favorites" else "Add to favorites") { favoriteAction() })
                            if (item.seriesIdentity != SeriesIdentity.MOVIE) add(ContextMenuItem(if (followed) "Unfollow series" else "Follow series") { nobility.downloader.core.scraper.FollowSeries.toggle(show) })
                            add(ContextMenuItem("Update details and thumbnail") { DatabaseUpdater.refreshSeries(item) })
                            add(ContextMenuItem("Copy name") { Tools.clipboardString = item.name })
                            if (item.seriesIdentity == SeriesIdentity.MOVIE) add(ContextMenuItem("Edit movie") { MovieEditorWindow.open(item) })
                        } }) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    DefaultImage(item.imagePath, item.imageLink, series = item, contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(70.dp, 100.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                                        Text("WCO • ${if (item.episodesSize > 0) "${item.episodesSize} episodes" else "Open to load episodes"}", style = MaterialTheme.typography.bodySmall)
                                        val unread = nobility.downloader.core.scraper.FollowSeries.items.firstOrNull { nobility.downloader.core.scraper.FollowSeries.key(it.show) == nobility.downloader.core.scraper.FollowSeries.key(show) }?.unread?.size ?: 0
                                        if (unread > 0) Text("$unread new episode(s)", color = MaterialTheme.colorScheme.primary)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Tooltip(if (favorite) "Remove from favorites" else "Add to favorites") {
                                                IconButton(onClick = { favoriteAction() }) { Icon(if (favorite) EvaIcons.Fill.Star else EvaIcons.Outline.Star, "Favorite", tint = if (favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                                            }
                                            if (item.seriesIdentity != SeriesIdentity.MOVIE) {
                                                Tooltip(if (followed) "Following: checks for new episodes at startup" else "Follow to check for new episodes at startup") {
                                                    IconToggleButton(checked = followed, onCheckedChange = { nobility.downloader.core.scraper.FollowSeries.toggle(show) }) { Icon(EvaIcons.Fill.Bookmark, if (followed) "Unfollow" else "Follow", tint = if (followed) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                                                }
                                                Button(onClick = { Core.changePage(nobility.downloader.Page.WATCH); Core.watchView.openShow(show) }) { Text("Watch") }
                                            }
                                            OutlinedButton(onClick = { Core.openSeriesDetails(item.slug, windowScope) }) { Text("Download") }
                                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less" else "Details") }
                                        }
                                    }
                                }
                                if (expanded) {
                                    Text(item.description.ifBlank { "No description cached. Update details to fetch it." }, modifier = Modifier.padding(top = 8.dp))
                                    LinkifyGenres(item.filteredGenres, linkColor = MaterialTheme.colorScheme.primary)
                                    TextButton(onClick = { DatabaseUpdater.refreshSeries(item) }, enabled = !DatabaseUpdater.busy) { Text("Update details and thumbnail") }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
                androidx.compose.foundation.VerticalScrollbar(adapter = rememberScrollbarAdapter(listState), modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }

    @Composable
    private fun LinkifyGenres(
        genreNames: List<String>,
        textColor: Color = Color.Unspecified,
        textAlign: TextAlign = TextAlign.Center,
        style: TextStyle = LocalTextStyle.current,
        linkColor: Color = Color.Red,
        modifier: Modifier = Modifier
    ) {
        val layoutResult = remember {
            mutableStateOf<TextLayoutResult?>(null)
        }
        val sb = StringBuilder()
        genreNames.forEachIndexed { index, s ->
            sb.append(s)
            if (index != genreNames.lastIndex) {
                sb.append(", ")
            }
        }
        val text = sb.toString()
        val genres = extractGenres(text)
        val annotatedString = buildAnnotatedString {
            append(text)
            genres.forEach {
                addStyle(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    ),
                    start = it.start,
                    end = it.end
                )
                addStringAnnotation(
                    tag = GENRE_TAG,
                    annotation = it.text,
                    start = it.start,
                    end = it.end
                )
            }
        }
        Text(
            text = annotatedString,
            color = textColor,
            style = style,
            textAlign = textAlign,
            onTextLayout = { layoutResult.value = it },
            modifier = modifier.pointerInput(Unit) {
                detectTapGestures { offsetPosition ->
                    layoutResult.value?.let {
                        val position = it.getOffsetForPosition(offsetPosition)
                        annotatedString.getStringAnnotations(position, position).firstOrNull()
                            ?.let { result ->
                                if (result.tag == GENRE_TAG) {
                                    val genre = BoxHelper.genreForName(result.item.trim())
                                    mSearchText.value = genre.name
                                }
                            }
                    }
                }
            }
        )
    }

    private val genrePattern
        get() = Pattern.compile(
            "[^,]+"
        )

    private fun extractGenres(text: String): List<GenreInfo> {
        val matcher = genrePattern.matcher(text)
        var matchStart: Int
        var matchEnd: Int
        val genres = arrayListOf<GenreInfo>()

        while (matcher.find()) {
            matchStart = matcher.start()
            matchEnd = matcher.end()
            val genre = text.substring(matchStart, matchEnd)
            genres.add(
                GenreInfo(
                    genre,
                    matchStart,
                    matchEnd
                )
            )
        }
        return genres
    }

    data class GenreInfo(
        val text: String,
        val start: Int,
        val end: Int
    )

    private val randomSearchHint = "Search by title, series URL, genre or description"

    @OptIn(ExperimentalFoundationApi::class)
    private fun openGenresWindow() {
        ApplicationState.newWindow(
            "Genres",
            size = DpSize(300.dp, 300.dp),
            alwaysOnTop = true
        ) {
            FullBox {
                val scope = rememberCoroutineScope()
                val scrollState = rememberLazyListState()
                Column(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Click a genre to search for it",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(2.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                            .padding(end = verticalScrollbarEndPadding)
                            .draggable(
                                state = rememberDraggableState {
                                    scope.launch {
                                        scrollState.scrollBy(-it)
                                    }
                                },
                                orientation = Orientation.Vertical,
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        state = scrollState
                    ) {
                        items(
                            BoxHelper.shared.wcoGenreBox.all
                                .distinctBy { it.name }
                                .sortedBy { it.capitalName },
                            key = { it.name }
                        ) { genre ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .height(35.dp)
                                    .multiClickable(
                                        indication = ripple(
                                            color = MaterialTheme.colorScheme
                                                .secondaryContainer.hover()
                                        ),
                                        onSecondaryClick = {
                                            mSearchText.value = genre.name
                                        }
                                    ) {
                                        mSearchText.value = genre.name
                                    }
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(5.dp)
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        genre.capitalName,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
                VerticalScrollbar(scrollState)
            }
        }
    }

    companion object {
        private const val GENRE_TAG = "GEN"
        fun followShow(series: Series) = nobility.downloader.core.scraper.WatchCatalog.Show(
            series.name, series.slug.slugToLink(), nobility.downloader.core.scraper.WatchCatalog.Provider.WCO)

        @JvmStatic fun matches(series: Series, search: String, byGenre: Boolean, byDesc: Boolean,
            favoriteOnly: Boolean, favoriteSlugs: Set<String>): Boolean {
            if (favoriteOnly && series.slug !in favoriteSlugs) return false
            val query = search.trim()
            return query.isEmpty() || series.name.contains(query, true) ||
                series.slug == query.linkToSlug() ||
                (byDesc && series.description.contains(query, true)) ||
                (byGenre && series.filteredGenres.any { it.equals(query, true) })
        }
    }

}