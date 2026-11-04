package nobility.downloader.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.ColorPalette
import compose.icons.evaicons.fill.MoreVertical
import kotlinx.coroutines.launch
import nobility.downloader.Page
import nobility.downloader.Page.Companion.viewPage
import nobility.downloader.core.Core
import nobility.downloader.core.scraper.video_download.Functions
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.theme.CoreTheme
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.Constants
import nobility.downloader.utils.Constants.mediumIconSize
import nobility.downloader.utils.Theme
import nobility.downloader.utils.tone
import kotlin.system.exitProcess

@Composable
fun MainWindow(scope: AppWindowScope) {
    UiWrapper {
        Column(Modifier.fillMaxSize()) {
            val subPages = when (Core.currentPage.mainPage) {
                Page.DOWNLOADER -> listOf(Page.DOWNLOADER, Page.DOWNLOADS, Page.COMPLETED)
                Page.FOLLOW -> listOf(Page.NEW_EPISODES, Page.FOLLOW, Page.FAVORITES, Page.HISTORY)
                else -> emptyList()
            }
            if (subPages.isNotEmpty()) {
                LazyRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(subPages) { page ->
                        FilterChip(selected = Core.currentPage == page, onClick = { Core.changePage(page) }, label = {
                            Text(when (page) {
                                Page.DOWNLOADER -> "Add downloads"
                                Page.DOWNLOADS -> "Queue"
                                Page.FOLLOW -> "Followed"
                                Page.NEW_EPISODES -> "New episodes (${nobility.downloader.core.scraper.FollowSeries.unreadCount})"
                                else -> page.title
                            })
                        })
                    }
                }
            }
            Box(Modifier.weight(1f)) {
        when (Core.currentPage) {
            Page.DOWNLOADER -> Core.downloaderView.Ui(scope)
            Page.WATCH -> Core.watchView.Ui(scope)
            Page.DOWNLOADS, Page.COMPLETED -> Core.downloadsView.Ui(scope)
            Page.SETTINGS -> Core.settingsView.Ui(scope)
            Page.HISTORY -> Core.historyView.Ui(scope)
            Page.FOLLOW, Page.NEW_EPISODES -> Core.followSeriesView.Ui(scope)
            Page.DATABASE -> Core.databaseView.Ui(scope)
            Page.FAVORITES -> Core.favoritesView.Ui(scope)
            Page.ERROR_CONSOLE -> Core.errorConsoleView.Ui(scope)
        }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UiWrapper(
    content: @Composable () -> Unit
) {
    var showFileMenu by remember {
        mutableStateOf(false)
    }
    val closeMenu = { showFileMenu = false }
    CoreTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column {
                TopAppBar(
                    modifier = Modifier.height(Constants.topBarHeight),
                    title = {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ZenDownloader reMaster",
                                modifier = Modifier.weight(1f, fill = false),
                                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold
                            )
                            TooltipIconButton(
                                "Theme Editor",
                                icon = EvaIcons.Fill.ColorPalette,
                                iconSize = mediumIconSize,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                ThemeEditorWindow.open()
                            }

                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { Core.openSettings() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Black)
                        ) { Text("Settings") }
                        TextButton(
                            onClick = { Core.changePage(Page.ERROR_CONSOLE) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Black)
                        ) {
                            Text(if (Core.errorConsole.consoleState.unreadErrors) "Error Console • New" else "Error Console")
                        }

                        Tooltip("Support development on Buy Me a Coffee") {
                            Button(
                                onClick = {
                                    DialogHelper.showLinkPrompt("https://buymeacoffee.com/amaster15", prompt = false)
                                },
                                modifier = Modifier.height(30.dp).padding(end = 6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00FF00),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Donate", fontSize = 12.sp, maxLines = 1)
                            }
                        }
                        OverflowRow(
                            modifier = Modifier.width(175.dp),
                            items = Core.currentPage.viewPage().menuOptions,
                            itemContent = { option ->
                                if (option.badge != null) {
                                    BadgedBox(
                                        badge = option.badge
                                    ) {
                                        TooltipIconButton(
                                            option.tooltip,
                                            icon = option.icon,
                                            iconSize = mediumIconSize,
                                            iconColor = option.contentColor?: MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            option.onClick()
                                        }
                                    }
                                } else {
                                    TooltipIconButton(
                                        option.tooltip,
                                        icon = option.icon,
                                        iconSize = mediumIconSize,
                                        iconColor = option.contentColor ?: MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        option.onClick()
                                    }
                                }
                            }
                        ) { options ->
                            Box {
                                TooltipIconButton(
                                    "Options",
                                    EvaIcons.Fill.MoreVertical,
                                    mediumIconSize,
                                    onClick = { showFileMenu = !showFileMenu }
                                )
                                DefaultCursorDropdownMenu(
                                    showFileMenu,
                                    options.map {
                                        DropdownOption(
                                            it.tooltip,
                                            it.modifier,
                                            it.icon,
                                            it.visible,
                                            it.contentColor,
                                            it.onClick
                                        )
                                    }
                                ) { closeMenu() }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Theme.colorScheme.primary
                            .tone(65.0)
                    )
                )
                            val coroutineScope = rememberCoroutineScope()
                            val listState = rememberLazyListState()
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                    .draggable(
                                        state = rememberDraggableState {
                                            coroutineScope.launch {
                                                listState.scrollBy(-it)
                                            }
                                        },
                                        orientation = Orientation.Horizontal
                                    )
                                    .horizontalWheelScroll { scroll ->
                                        coroutineScope.launch {
                                            listState.scrollBy(scroll)
                                        }
                                    },
                                state = listState
                            ) {
                                items(
                                    Page.mainPages,
                                    key = { it.name }
                                ) { page ->
                                    val count = when(page) {
                                        Page.FOLLOW -> nobility.downloader.core.scraper.FollowSeries.unreadCount
                                        Page.DOWNLOADER -> Core.child.downloadThread.downloadsInQueue.value
                                        else -> 0
                                    }
                                    FilterChip(selected = Core.currentPage.mainPage == page,
                                        onClick = { Core.changePage(page) }, label = {
                                            Text(page.mainTitle + if (count > 0) " ($count)" else "")
                                        })
                                }
                            }
                }
            },
            content = { paddingValues ->
                if (Core.child.shutdownExecuted) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .zIndex(10f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.wrapContentHeight()
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp)
                                ).border(
                                    1.dp,
                                    MaterialTheme.colorScheme.error,
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(80.dp)
                                    .padding(top = 15.dp, bottom = 15.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                """
                                        Shutdown Executed.
                                        Please wait patiently while the program kills all running drivers.
                                        
                                        Drivers Killed: ${Core.child.shutdownProgressIndex}/${Core.child.shutdownProgressTotal}
                                    """.trimIndent(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp)
                            )
                            defaultButton(
                                "Force Shutdown",
                                height = 40.dp,
                                width = 140.dp,
                                padding = PaddingValues(
                                    top = 10.dp,
                                    bottom = 10.dp
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                fontColor = MaterialTheme.colorScheme.onError
                            ) {
                                Functions.killChromeProcesses()
                                exitProcess(if (Core.child.triggeredUpdate) 100 else 2)
                            }
                        }
                    }
                }
                Column(modifier = Modifier.padding(paddingValues)) {
                    val unread = nobility.downloader.core.scraper.FollowSeries.unreadCount
                    if (unread > 0) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("$unread new episode(s) in followed series", modifier = Modifier.weight(1f))
                                TextButton(onClick = { Core.changePage(Page.NEW_EPISODES) }) { Text("View updates") }
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) { content() }
                }
            }
        )
    }
}

