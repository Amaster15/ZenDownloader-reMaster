package nobility.downloader.core.scraper

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import nobility.downloader.core.entities.Download
import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.scraper.data.DownloadQueue
import nobility.downloader.core.scraper.data.M3U8Data
import nobility.downloader.core.scraper.video_download.VideoDownloadHandler
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.FrogLog
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Used to handle video download tasks.
 * This will continuously run during the entire apps' lifespan.
 */
class DownloadThread {

    val downloadQueue: MutableList<DownloadQueue> = Collections
        .synchronizedList(mutableStateListOf())
    // Preparation exists before a file/quality is known; never persist these as completed files.
    val preparation = mutableStateMapOf<String, Download>()

    @Synchronized
    private fun track(episode: Episode) {
        preparation[episode.slug] = Download(dateAdded = System.currentTimeMillis()).apply {
            name = episode.name
            slug = episode.slug
            seriesSlug = episode.seriesSlug
            queued = true
            setProgress("Queued")
        }
    }

    @Synchronized
    fun preparationStatus(slug: String, status: String) {
        preparation[slug]?.setProgress(status)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentJobs = AtomicInteger(0)
    private var stopJob: Job? = null
    private val hiAnimeQueue = LinkedHashMap<String, DownloadQueue>()

    @Synchronized
    fun deferHiAnime(queue: DownloadQueue) {
        if (Core.child.isRunning) {
            hiAnimeQueue[queue.episode.slug] = queue
            preparationStatus(queue.episode.slug, "Waiting for HiAnime fallback")
        }
        synchronizeDownloadsInQueue()
    }

    @Volatile
    var downloadsInProgress = mutableStateOf(0)
        private set

    @Volatile
    var downloadsInQueue = mutableStateOf(0)
        private set

    @Volatile
    var downloadsFinishedForSession = 0

    suspend fun run() = withContext(scope.coroutineContext) {
        while (isActive) {
            val headless = Defaults.HEADLESS_MODE.boolean()
            val maxThreads = if (!headless) 1 else Defaults.DOWNLOAD_THREADS.int()
            val availableSlots = (maxThreads - currentJobs.get()).coerceAtLeast(0)
            if (Core.child.isRunning && availableSlots > 0) {
                var launched = 0
                while (launched < availableSlots) {
                    launchDownloadJob()
                    launched++
                }
            }
            delay(500)
        }
    }

    suspend fun launchStopJob() = withContext(scope.coroutineContext) {
        stopJob?.cancel()
        delay(5000)
        stopJob = launch {
            while (isActive) {
                if (hasFinished()) {
                    val finished = downloadsFinishedForSession
                    if (finished > 0) {
                        FrogLog.message("Gracefully finished downloading $finished video(s).")
                    } else {
                        FrogLog.message("Gracefully finished. No downloads have been made.")
                    }
                    Core.child.stop()
                    break
                }
                delay(2500)
            }
        }
    }

    @Synchronized
    private fun launchDownloadJob() {
        var fallback = false
        val nextDownload = nextDownload ?: takeHiAnimeIfReady()?.also { fallback = true }
        if (nextDownload == null) {
            return
        }
        preparationStatus(nextDownload.episode.slug, if (fallback) "Resolving HiAnime video link" else "Resolving video link")
        FrogLog.message("[S] [${nextDownload.episode.name}] Starting ${if (fallback) "HiAnime fallback" else "WCO download"}.")
        currentJobs.incrementAndGet()
        scope.launch(Dispatchers.IO) {
            var downloader: VideoDownloadHandler? = null
            var failure: String? = null
            try {
                downloader = VideoDownloadHandler(nextDownload, nextDownload.quality)
                if (fallback) downloader.runHiAnime() else downloader.run()
            } catch (e: CancellationException) {
                failure = "Cancelled"
                throw e
            } catch (e: Exception) {
                failure = "Failed: ${e.message ?: e.javaClass.simpleName}"
                FrogLog.message("[S] [${nextDownload.episode.name}] $failure")
                FrogLog.error(
                    "Failed to finish download.",
                    e
                )
            } finally {
                val lastStatus = preparation[nextDownload.episode.slug]?.downloadProgress?.value.orEmpty()
                val finalStatus = when {
                    !Core.child.isRunning -> "Stopped"
                    isDeferred(nextDownload.episode.slug) -> "Waiting for HiAnime fallback"
                    failure != null -> failure!!
                    else -> lastStatus.ifBlank { "Attempt ended without a video file" }
                }
                preparationStatus(nextDownload.episode.slug, finalStatus)
                FrogLog.message("[S] [${nextDownload.episode.name}] $finalStatus")
                try { downloader?.killDriver() } finally { currentJobs.decrementAndGet() }
            }
        }
    }

    @Synchronized
    private fun isDeferred(slug: String) = hiAnimeQueue.containsKey(slug)

    @Synchronized
    private fun takeHiAnimeIfReady(): DownloadQueue? {
        if (downloadQueue.isNotEmpty() || currentJobs.get() != 0 || hiAnimeQueue.isEmpty()) return null
        val entry = hiAnimeQueue.entries.first()
        val next = entry.value
        hiAnimeQueue.remove(entry.key)
        synchronizeDownloadsInQueue()
        return next
    }

    @Synchronized
    private fun hasFinished(): Boolean {
        val noJobs = currentJobs.get() <= 0
        val nothingToDownload = isQueueEmpty
        return noJobs && nothingToDownload
    }

    fun addToQueue(
        episode: Episode,
        m3U8Data: M3U8Data
    ): Boolean {
        if (!isInQueue(episode)) {
            track(episode)
            downloadQueue.add(
                DownloadQueue(
                    episode,
                    m3U8Data
                )
            )
            synchronizeDownloadsInQueue()
            return true
        }
        return false
    }

    fun addToQueue(
        vararg downloads: Episode,
        temporaryQuality: Quality? = null
    ): Int {
        return addToQueue(
            listOf(*downloads),
            temporaryQuality
        )
    }

    fun addToQueue(
        downloads: List<Episode>,
        temporaryQuality: Quality? = null
    ): Int {
        var added = 0
        for (dl in downloads) {
            if (!isInQueue(dl)) {
                track(dl)
                downloadQueue.add(
                    DownloadQueue(
                        dl,
                        quality = temporaryQuality
                    )
                )
                added++
            }
        }
        synchronizeDownloadsInQueue()
        return added
    }

    fun removeFromQueue(
        queue: DownloadQueue
    ) {
        preparation.remove(queue.episode.slug)
        downloadQueue.removeIf {
            it.episode.matches(queue.episode)
        }
        synchronizeDownloadsInQueue()
    }

    @get:Synchronized
    private val nextDownload: DownloadQueue?
        get() {
            if (downloadQueue.isEmpty()) {
                return null
            }
            val link = downloadQueue.first()
            downloadQueue.removeAt(0)
            synchronizeDownloadsInQueue()
            return link
        }

    private fun isInQueue(episode: Episode): Boolean {
        return downloadQueue.any {
            it.episode.matches(episode)
        }
    }

    @JvmOverloads
    @Synchronized
    fun clear(preserveStatus: Boolean = false) {
        if (!preserveStatus) preparation.clear()
        else {
            downloadQueue.forEach { preparationStatus(it.episode.slug, "Stopped before starting") }
            hiAnimeQueue.values.forEach { preparationStatus(it.episode.slug, "Fallback stopped before starting") }
        }
        downloadQueue.clear()
        hiAnimeQueue.clear()
        synchronizeDownloadsInQueue()
    }

    fun stop() {
        scope.cancel()
    }

    @Synchronized
    fun synchronizeDownloadsInQueue() {
        downloadsInQueue.value = downloadQueue.size + hiAnimeQueue.size
    }

    @Synchronized
    fun incrementDownloadsFinished() {
        downloadsFinishedForSession++
    }

    @Synchronized
    fun incrementDownloadsInProgress() {
        downloadsInProgress.value++
    }

    @Synchronized
    fun decrementDownloadsInProgress() {
        if (downloadsInProgress.value > 0) {
            downloadsInProgress.value--
        }
    }

    val isQueueEmpty @Synchronized get() = downloadQueue.isEmpty() && hiAnimeQueue.isEmpty()

}