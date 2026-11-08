package nobility.downloader.ui.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import nobility.downloader.core.Core
import nobility.downloader.core.maintenance.*
import nobility.downloader.core.scraper.WatchCatalog
import java.io.File
import java.nio.file.Files

@Composable fun RetryFailedButton() {
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { FailedDownloads.recover(Core.child.downloadList.toList()) } }
    val failed = FailedDownloads.requests.filter { it.reason.isNotBlank() }
    var confirm by remember { mutableStateOf(false) }
    var fallback by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    OutlinedButton(enabled = failed.isNotEmpty() && !Core.child.hasActiveDownloads && !VideoValidation.isBusy,
        onClick = { confirm = true }) { Text("Retry failed (${failed.size})") }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Retry failed episodes") },
        text = { Column {
            Text("Retry ${failed.size} episode(s). Partial files are preserved with a .retry-backup suffix; verified downloads are skipped.")
            Row { Checkbox(fallback, { fallback = it }); Text("Allow HiAnime fallback for failed WCO episodes") }
            if (error.isNotBlank()) Text(error)
        } }, confirmButton = { Button(onClick = {
            try {
                check(!Core.child.hasActiveDownloads && !VideoValidation.isBusy) { "Wait for current work to finish" }
                val candidates = failed.filter { request -> Core.child.downloadList.none { it.slug.substringBefore('|') == request.url && it.isComplete } }
                // Preserve partial outputs; never overwrite a pre-existing backup.
                Core.child.downloadList.filter { record -> candidates.any { it.url == record.slug.substringBefore('|') } && !record.isComplete }.forEach {
                    val file = File(it.downloadPath)
                    if (file.isFile) Files.move(file.toPath(), File(file.path + ".retry-backup-" + java.util.UUID.randomUUID()).toPath())
                    it.fileSize = 0
                }
                val wco = candidates.filter { it.provider == WatchCatalog.Provider.WCO }
                if (wco.isNotEmpty()) { Core.child.softStart(); wco.forEach { FailedDownloads.retryWco(it, fallback) } }
                val others = candidates.filter { it.provider != WatchCatalog.Provider.WCO }
                if (others.isNotEmpty()) DirectDownloads.retryFailed(others)
                confirm = false
            } catch (e: Exception) { error = e.message.orEmpty() }
        }) { Text("Retry") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
