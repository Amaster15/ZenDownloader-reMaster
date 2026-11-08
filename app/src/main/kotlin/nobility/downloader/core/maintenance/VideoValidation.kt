package nobility.downloader.core.maintenance

import AppInfo
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Download
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.VideoUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/** Completion requires successful video decoding, not just an HTTP-sized file. */
object VideoValidation {
    data class Result(val size: Long, val modified: Long, val valid: Boolean, val detail: String)
    private val gson = Gson()
    private val stateFile get() = File(AppInfo.databasePath, "my_data/video-validation.json")
    private val results = mutableStateMapOf<String, Result>()
    private val running = mutableSetOf<String>()
    val isBusy get() = synchronized(this) { running.isNotEmpty() }
    private val slots = Semaphore(1)
    private var loaded = false
    @Synchronized private fun load() {
        if (loaded) return
        loaded = true
        runCatching { gson.fromJson(stateFile.readText(), Array<Record>::class.java).forEach { results[it.path] = it.result } }
    }
    private data class Record(val path: String, val result: Result)
    @Synchronized fun result(file: File): Result? {
        load()
        return results[file.absolutePath]?.takeIf { it.size == file.length() && it.modified == file.lastModified() }
    }
    @Synchronized fun remember(file: File, valid: Boolean, detail: String) {
        load()
        results[file.absolutePath] = Result(file.length(), file.lastModified(), valid, detail)
        stateFile.parentFile.mkdirs()
        val temp = File(stateFile.path + ".tmp")
        temp.writeText(gson.toJson(results.map { Record(it.key, it.value) }))
        Files.move(temp.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    @Synchronized fun recheck(download: Download) {
        load()
        if (download.downloading || download.queued) return
        results.remove(File(download.downloadPath).absolutePath)
        enqueue(download)
    }
    @Synchronized fun enqueue(download: Download) {
        val file = download.downloadFile() ?: return
        result(file)?.let { download.setProgress(if (it.valid) "Finished • verified" else "Failed validation: ${it.detail}"); return }
        download.setProgress("Validating video…")
        if (!running.add(file.absolutePath)) return
        Core.taskScope.launch(Dispatchers.IO) {
            try {
                slots.withPermit {
                    val size = file.length(); val modified = file.lastModified()
                    val error = runInterruptible { inspect(file, VideoUtil.loadFfmpegPath()) }
                    if (file.length() != size || file.lastModified() != modified) {
                        download.setProgress("File changed during validation; check again")
                    } else {
                        remember(file, error == null, error ?: "Playable video")
                        if (error == null) FailedDownloads.complete(download.slug) else { FailedDownloads.recover(listOf(download)); FailedDownloads.fail(download.slug, "Failed validation: $error") }
                        download.setProgress(if (error == null) "Finished • verified" else "Failed validation: $error")
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { download.setProgress("Validation unavailable: ${e.message}") }
            finally { synchronized(this@VideoValidation) { running.remove(file.absolutePath) } }
        }
    }
    /** Returns a diagnostic on failure. The original file is always left intact. */
    @JvmStatic fun inspect(file: File, ffmpeg: String): String? {
        if (!file.isFile || file.length() <= 5000) return "Missing or empty video"
        if (ffmpeg.isBlank()) throw IllegalStateException("FFmpeg is missing; use Repair setup in Settings")
        val log = File.createTempFile("zen-validate-", ".log")
        var process: Process? = null
        try {
            process = ProcessBuilder(ffmpeg, "-v", "error", "-xerror", "-nostdin", "-i", file.absolutePath,
                "-map", "0:v:0", "-map", "0:a?", "-f", "null", "-")
                .redirectErrorStream(true).redirectOutput(log).start()
            if (!process.waitFor(10, TimeUnit.MINUTES)) return "Validation timed out; file has not been marked complete"
            return if (process.exitValue() == 0) null else log.readText().take(250).ifBlank { "Video cannot be decoded" }
        } finally { process?.let { if (it.isAlive) it.destroyForcibly() }; log.delete() }
    }
}
