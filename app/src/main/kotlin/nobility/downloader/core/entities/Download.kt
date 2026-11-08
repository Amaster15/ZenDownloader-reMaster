package nobility.downloader.core.entities

import androidx.compose.runtime.mutableStateOf
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Transient
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.settings.Quality
import nobility.downloader.core.settings.Quality.Companion.qualityForResolution
import nobility.downloader.utils.Tools
import java.io.File

@Entity
data class Download(
    var downloadPath: String = "",
    var dateAdded: Long = 0,
    var resolution: Int = 0
) : Episode() {

    var manualProgress = false

    @Transient
    var downloading = false

    @Transient
    var paused = false

    @Transient
    var queued = false

    @Transient
    var downloadProgress = mutableStateOf("0%")

    @Transient
    var downloadSeconds = mutableStateOf(0)
        private set

    @Transient
    var downloadSpeed = mutableStateOf("")
        private set

    fun updateWithDownload(
        download: Download,
        updateProperties: Boolean
    ) {
        if (downloadPath != download.downloadPath) {
            downloadPath = download.downloadPath
        }
        if (dateAdded != download.dateAdded) {
            dateAdded = download.dateAdded
        }
        if (fileSize != download.fileSize) {
            fileSize = download.fileSize
        }
        if (queued != download.queued) {
            queued = download.queued
        }
        if (downloading != download.downloading) {
            downloading = download.downloading
        }
        if (resolution != download.resolution) {
            resolution = download.resolution
        }
        if (manualProgress != download.manualProgress) {
            manualProgress = download.manualProgress
        }
        if (downloadProgress.value != download.downloadProgress.value) {
            downloadProgress.value = download.downloadProgress.value
        }
        if (updateProperties) {
            updateProgress()
        }
        BoxHelper.shared.downloadBox.put(this)
    }

    fun matches(download: Download): Boolean {
        return download.slug == slug
                && download.nameAndResolution() == nameAndResolution()
    }

    fun updateProgress() {
        if (queued) {
            setProgress("Queued")
            return
        } else if (paused) {
            setProgress("Paused")
            return
        }
        if (!downloading && listOf("Error:", "Failed:", "Cancelled", "Stopped").any { downloadProgress.value.startsWith(it) }) return
        if (manualProgress && downloading) {
            return
        }
        val video = downloadFile()
        if (video != null && video.exists()) {
            if (!downloading && fileSize > 5000 && video.length() >= fileSize) {
                nobility.downloader.core.maintenance.VideoValidation.enqueue(this)
                return
            }
            if (manualProgress) {
                setProgress("100%")
            } else {
                val ratio = video.length() / fileSize.toDouble()
                setProgress(Tools.percentFormat.format(ratio))
            }
        } else {
            setProgress("File Not Found")
        }
    }

    fun setSeconds(seconds: Int) {
        downloadSeconds.value = seconds
    }

    fun setDownloadSpeed(bps: Long) {
        downloadSpeed.value = " (${Tools.bytesToString(bps)}ps)"
    }

    @Transient
    private var reportedProgress = false

    fun setProgress(value: String) {
        downloadProgress.value = value
        if (downloading || reportedProgress) {
            val percentage = Regex("(\\d+(?:[.,]\\d+)?)\\s*%").find(value)?.groupValues?.get(1)
                ?.replace(',', '.')?.toDoubleOrNull()?.toInt()?.coerceIn(0, 100)
            if (percentage != null) {
                reportedProgress = true
                nobility.downloader.core.Core.console.consoleState.updateDownloadProgress(
                    "$slug|$downloadPath", "${name.take(240)}: $percentage%"
                )
            } else if (reportedProgress && listOf("Validating", "Finished", "Failed", "Cancelled", "Stopped").any { value.startsWith(it) }) {
                nobility.downloader.core.Core.console.consoleState.updateDownloadProgress(
                    "$slug|$downloadPath", "${name.take(220)}: $value")
            }
        }
    }

    fun downloadFile(): File? {
        if (downloadPath.isNotEmpty()) {
            val file = File(downloadPath)
            if (file.exists()) {
                return file
            }
        }
        return null
    }

    val isComplete: Boolean
        get() {
            if (downloading || queued) return false
            if (fileSize <= 5000) {
                return false
            }
            val file = downloadFile()
            return if (file != null) {
                if (file.length() <= 0) {
                    false
                } else {
                    file.length() >= fileSize && nobility.downloader.core.maintenance.VideoValidation.result(file)?.valid == true
                }
            } else false
        }

    fun nameAndResolution(): String {
        val quality = qualityForResolution(resolution)
        val extra = if (quality == Quality.LOW) "" else " (" + quality.tag + ")"
        return name + extra
    }
}
