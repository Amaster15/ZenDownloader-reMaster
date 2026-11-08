package nobility.downloader.ui.views

import androidx.compose.foundation.layout.*
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nobility.downloader.Page
import nobility.downloader.core.Core
import nobility.downloader.core.scraper.WatchCatalog
import nobility.downloader.ui.windows.utils.AppWindowScope

class DownloaderView : ViewPage {
    override val page = Page.DOWNLOADER
    fun openKimCartoon(show: WatchCatalog.Show) = DirectDownloads.open(show)
    override val menuOptions get() = Core.downloadsView.menuOptions + listOf(
        nobility.downloader.ui.components.OverflowOption(
            compose.icons.EvaIcons.Fill.Close, "Cancel direct download", visible = DirectDownloads.isBusy
        ) { DirectDownloads.cancel() }
    )
    @Composable override fun Ui(windowScope: AppWindowScope) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Core.console.ConsoleTextField(windowScope, modifier = Modifier.fillMaxSize())
        }
    }
}
