package nobility.downloader.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import nobility.downloader.core.Core
import nobility.downloader.core.maintenance.*

object DownloadTools {
    @Composable fun Ui() {
        var plan by remember { mutableStateOf<List<DownloadRepair.Move>?>(null) }
        var message by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy && !Core.child.hasActiveDownloads, onClick = {
                    busy = true
                    scope.launch {
                        try { plan = withContext(Dispatchers.IO) { DownloadRepair.preview() } }
                        catch (e: Exception) { message = e.message.orEmpty() }
                        finally { busy = false }
                    }
                }) { Text("Repair folders…") }
                OutlinedButton(enabled = !busy && !Core.child.hasActiveDownloads, onClick = {
                    Core.child.downloadList.toList().filter { it.fileSize > 5000 && (it.downloadFile()?.length() ?: 0) >= it.fileSize }.forEach { VideoValidation.recheck(it) }
                    message = "Checking completed files. Validation runs one file at a time."
                }) { Text("Check files") }
                RetryFailedButton()
            }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        }
        plan?.let { moves ->
            AlertDialog(onDismissRequest = { if (!busy) plan = null }, title = { Text("Review folder repair") },
                text = { Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                    Text("${moves.size} episode(s). Existing destination files will be skipped. Subtitle files move with their episode.")
                    moves.forEach { Text("${it.source.name}\n→ ${it.target.absolutePath}${if (it.conflict) " • CONFLICT: skip" else ""}\n") }
                } }, confirmButton = { Button(enabled = !busy && moves.isNotEmpty(), onClick = {
                    busy = true
                    scope.launch {
                        try { message = withContext(Dispatchers.IO) { DownloadRepair.apply(moves) }; plan = null }
                        catch (e: Exception) { message = e.message.orEmpty() }
                        finally { busy = false }
                    }
                }) { Text(if (busy) "Moving…" else "Move files") } },
                dismissButton = { TextButton(enabled = !busy, onClick = { plan = null }) { Text("Cancel") } })
        }
    }
}
