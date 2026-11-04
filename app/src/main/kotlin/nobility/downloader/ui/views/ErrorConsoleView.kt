package nobility.downloader.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import nobility.downloader.Page
import nobility.downloader.core.Core
import nobility.downloader.ui.windows.utils.AppWindowScope

class ErrorConsoleView: ViewPage {

    override val page = Page.ERROR_CONSOLE

    @Composable
    override fun Ui(windowScope: AppWindowScope) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Text("Error Console", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
            Text("Download failures and connection problems. Copy the details below when troubleshooting.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Core.errorConsole.ConsoleTextField(
                windowScope,
                modifier = Modifier.weight(1f)
            )
        }
    }
}