package nobility.downloader.ui.views

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import nobility.downloader.core.Core
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.VideoPlayer
import nobility.downloader.core.driver.*
import nobility.downloader.core.driver.undetected_chrome.ChromeDriverBuilder
import nobility.downloader.core.scraper.player.PlayVideoWithVLC
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.VideoUtil
import nobility.downloader.ui.windows.FFSetDownloaderWindow
import java.io.File
import java.util.concurrent.TimeUnit

object SetupHealth {
    fun executable(path: String): Boolean = runCatching {
        if (path.isBlank()) return false
        val process = ProcessBuilder(path, if (File(path).name.startsWith("ff")) "-version" else "--version")
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        try { process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0 }
        finally { if (process.isAlive) process.destroyForcibly() }
    }.getOrDefault(false)

    suspend fun check(): String = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        val builder = ChromeDriverBuilder()
        try {
            val custom = Defaults.isUsingCustomChrome
            val driver = if (custom) Defaults.CHROME_DRIVER_PATH.string() else BrowserDriverSetup.setup()
            val browser = if (custom) Defaults.CHROME_BROWSER_PATH.string() else ManagedBrowser.browserPath()
            val session = builder.build(org.openqa.selenium.chrome.ChromeOptions(), driver, browser, true)
            try {
                session.get("data:text/html,<title>Zen setup check</title>")
                check(session.title == "Zen setup check")
                lines += "Browser + driver: working"
            } finally { session.quit() }
        } catch (e: Exception) { lines += "Browser + driver: ${e.message}" }
        finally { builder.close() }
        lines += "FFmpeg: " + if (executable(VideoUtil.loadFfmpegPath())) "working" else "missing or unable to run"
        lines += "FFplay: " + if (executable(VideoUtil.loadFfplayPath())) "working" else "missing or unable to run"
        val player = VideoPlayer.playerForSetting()
        val path = when (player) {
            VideoPlayer.VLC -> PlayVideoWithVLC.findVlcPath().orEmpty()
            VideoPlayer.MPV -> Defaults.MPV_PATH.string().ifBlank { "mpv" }
            else -> VideoUtil.loadFfplayPath()
        }
        lines += "Selected player ($player): " + if (executable(path)) "available" else "missing or unable to run"
        lines.joinToString("\n")
    }
    @Composable fun Button() {
        var open by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("Check the browser, driver, FFmpeg and selected player.") }
        val scope = rememberCoroutineScope()
        fun run(repair: Boolean) {
            busy = true; status = if (repair) "Repairing managed tools…" else "Checking setup…"
            scope.launch {
                try {
                    check(!Core.child.hasActiveDownloads && !nobility.downloader.core.maintenance.VideoValidation.isBusy && Core.child.runningDrivers.isEmpty() && Core.child.runningProcesses.none { it.isAlive }) { "Stop downloads, searches and playback, and wait for validation before checking setup." }
                    if (repair) withContext(Dispatchers.IO) {
                        if (Defaults.isUsingCustomChrome) error("Custom browser paths are enabled. Clear them in Settings to use automatic repair.")
                        File(AppInfo.databasePath, "browser/current").delete()
                        BrowserDriverSetup.setup()
                        val result = FFSetDownloaderWindow().downloadLatestFFmpeg(force = true)
                        check(!result.isFailed) { result.message.orEmpty() }
                    }
                    status = check()
                } catch (e: Exception) { status = "Setup: ${e.message}" }
                finally { busy = false }
            }
        }
        TextButton(onClick = { open = true }) { Text("Setup health / Repair setup") }
        if (open) AlertDialog(onDismissRequest = { if (!busy) open = false }, title = { Text("Setup health") },
            text = { Column { Text(status); if (busy) LinearProgressIndicator() } },
            confirmButton = { androidx.compose.material3.TextButton(enabled = !busy, onClick = { run(false) }) { Text("Check setup") } },
            dismissButton = { Column {
                TextButton(enabled = !busy && !Core.child.hasActiveDownloads, onClick = { run(true) }) { Text("Repair setup") }
                TextButton(enabled = !busy, onClick = { open = false }) { Text("Close") }
            } })
    }
}
