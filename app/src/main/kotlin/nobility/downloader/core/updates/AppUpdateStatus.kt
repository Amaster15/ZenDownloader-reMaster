package nobility.downloader.core.updates

import Updater
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import nobility.downloader.core.Core
import nobility.downloader.utils.FrogLog

/** Session-wide indicator, shared by startup and manual release checks. */
object AppUpdateStatus {
    var availableVersion by mutableStateOf<String?>(null)
        private set
    val available: Boolean get() = availableVersion != null

    fun recordVersion(version: String) {
        availableVersion = version.takeUnless { Updater.isLatest(it) }
    }

    fun checkOnStartup() {
        Core.taskScope.launch(Dispatchers.IO) {
            val result = Updater.parseLatestRelease()
            val release = result.data
            if (release != null) withContext(Dispatchers.Main) { recordVersion(release.version) }
            else FrogLog.error("Startup update check failed. ${result.message.orEmpty()}")
        }
    }
}
