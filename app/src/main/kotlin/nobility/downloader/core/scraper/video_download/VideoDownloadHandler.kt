package nobility.downloader.core.scraper.video_download

import Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.seriesForSlug
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.VideoUtil
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.scraper.data.DownloadData
import nobility.downloader.core.scraper.data.DownloadQueue
import nobility.downloader.core.scraper.video_download.Functions.downloadVideo
import nobility.downloader.core.scraper.video_download.Functions.fileSize
import nobility.downloader.core.scraper.video_download.MovieDownloader.handleMovie
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.ui.windows.m3u8.ChooseM3U8Window
import nobility.downloader.utils.Constants
import nobility.downloader.utils.Tools
import nobility.downloader.utils.update

/**
 * The core handler of video downloading.
 * This class is used to organize the executions in an easy-to-read manner (for my sanity).
 */
class VideoDownloadHandler(
    private val queue: DownloadQueue,
    temporaryQuality: Quality? = queue.quality
) {

    private val help = VideoDownloadHelper(
        queue,
        temporaryQuality
    )

    private val data get() = help.videoDownloadData

    private var connectionRetries = 0
    private suspend fun finishFailed(reason: String, allowFallback: Boolean = true) {
        data.error(reason)
        if (allowFallback && Defaults.HIANIME_FALLBACK.boolean() && Core.child.isRunning &&
            queue.m3U8Data == null && !queue.episode.isMovie) {
            Core.child.downloadThread.deferHiAnime(queue)
            data.message("Queued for HiAnime after all WCO downloads finish.")
        }
        if (!data.finished) data.finishEpisode()
    }

    suspend fun runHiAnime() {
        Core.child.downloadThread.incrementDownloadsInProgress()
        try {
            if (Defaults.HIANIME_FALLBACK.boolean() && Core.child.isRunning) {
                if (data.quickCheckVideoExists(queue.episode.slug)) return
                data.resetDownload()
                data.message("WCO pass finished. Searching HiAnime for the same episode and language.")
                val provider = HiAnimeFallback()
                val series = seriesForSlug(queue.episode.seriesSlug)?.name ?: queue.episode.seriesName
                val match = provider.find(series, queue.episode.name)
                if (!Core.child.isRunning) return
                data.message("HiAnime matched $series, episode ${match.episode}, ${match.language}. Resolving Ayame.")
                val wanted = data.temporaryQuality ?: Quality.qualityForTag(Defaults.QUALITY.string())
                val stream = provider.resolve(match, wanted)
                val ffmpeg = VideoUtil.loadFfmpegPath()
                require(ffmpeg.isNotBlank()) { "FFmpeg is required for HiAnime downloads" }
                val output = data.generateEpisodeSaveFile(stream.quality, " [HiAnime]")
                // A fallback must never append to a partial WCO file or replace an existing video.
                require(!output.exists()) { "HiAnime output already exists: ${output.name}" }
                if (!data.createDownload(queue.episode.slug, stream.quality, output)) return
                val record = data.currentDownload
                record.fileSize = 0
                record.manualProgress = true
                record.queued = false
                record.downloading = true
                record.update()
                var lastReported = -1
                provider.download(stream, output, ffmpeg, { Core.child.isRunning }) { percent ->
                    record.setProgress("HiAnime $percent%")
                    if (percent / 10 != lastReported) {
                        lastReported = percent / 10
                        data.message("HiAnime Ayame: $percent% (${stream.quality.tag})")
                    }
                }
                record.fileSize = output.length()
                record.downloading = false
                record.manualProgress = false
                record.update()
                BoxMaker.makeDownloadedEpisode(queue.episode.slug)
                Core.child.downloadThread.incrementDownloadsFinished()
                data.message("Successfully downloaded from HiAnime Ayame with ${stream.quality.tag} quality.")
            }
        } catch (e: CancellationException) {
            data.message("HiAnime fallback stopped.")
            throw e
        } catch (e: Exception) {
            data.error("HiAnime fallback could not complete. | Skipping episode", e.message)
        } finally {
            data.mCurrentDownload?.let { it.manualProgress = false }
            if (!data.finished) data.finishEpisode()
        }
    }

    private suspend fun retryConnection(failure: Exception): Boolean {
        val limit = Defaults.PAGE_LINK_RETRIES.int().coerceIn(1, 100)
        val seconds = Defaults.PAGE_LINK_RETRY_DELAY_SECONDS.int().coerceIn(1, 100)
        val reason = generateSequence<Throwable>(failure) { it.cause }.last().let {
            "${it.javaClass.simpleName}: ${it.message.orEmpty()}"
        }
        data.resetDownload()
        if (connectionRetries >= limit) {
            finishFailed("Video connection failed after $limit retries. $reason")
            return false
        }
        connectionRetries++
        data.message("A video link was found, but the video server connection failed: ${failure.message ?: reason} " +
            "Refreshing the page for another link in $seconds seconds (retry $connectionRetries of $limit). The popup may appear again.")
        data.error("Video connection failed. Refreshing the page for a new link in $seconds seconds " +
            "(connection retry $connectionRetries of $limit).", reason)
        data.downloadDatas.clear()
        data.resRetries = 0
        return PageLinkRetry.waitBeforeRetry(seconds) { Core.child.isRunning }
    }

    suspend fun run() = withContext(Dispatchers.IO) {
        Core.child.downloadThread.incrementDownloadsInProgress()
        while (Core.child.isRunning) {
            if (data.retries >= Defaults.VIDEO_RETRIES.int()) {
                finishFailed("WCO reached its maximum download retry count.")
                break
            }
            if (data.reachedMax()) {
                break
            }
            if (data.finished) {
                break
            }
            val slug = queue.episode.slug
            if (slug.isEmpty()) {
                data.message("Skipping episode with no slug.")
                data.finishEpisode()
                continue
            }
            if (data.quickCheckVideoExists(slug)) {
                continue
            }
            val movie = Core.child.movieHandler.movieForSlug(slug)
            if (movie != null) {
                handleMovie(movie, data)
                continue
            }
            val m3U8Data = queue.m3U8Data
            var parsedResult: Resource<DownloadData>? = null
            if (m3U8Data == null) {
                val retriesBeforePage = data.retries
                var pageAttempt = 0
                var browserStartupFailed = false
                parsedResult = PageLinkRetry.run(
                    retries = Defaults.PAGE_LINK_RETRIES.int().coerceIn(1, 100),
                    delaySeconds = Defaults.PAGE_LINK_RETRY_DELAY_SECONDS.int().coerceIn(1, 100),
                    running = { Core.child.isRunning },
                    attempt = {
                        // Page attempts have their own budget; old quality counters must not
                        // prevent a fresh page read or consume the download retry budget.
                        data.resRetries = 0
                        data.retries = retriesBeforePage
                        pageAttempt++
                        data.message("Resolving video link: attempt $pageAttempt of ${Defaults.PAGE_LINK_RETRIES.int().coerceIn(1, 100) + 1}.")
                        try {
                            help.parseQualities(slug)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: nobility.downloader.core.driver.BrowserStartupException) {
                            browserStartupFailed = true
                            data.message("${e.message}. Open Error Console for details; page retries cannot repair browser startup.")
                            Resource.Error(e.message.orEmpty(), e)
                        } catch (e: Exception) {
                            Resource.Error("Player/link resolution failed: ${e.message ?: e.javaClass.simpleName}", e)
                        }
                    },
                    shouldRetry = { !browserStartupFailed && it.isFailed && it.errorCode != ErrorCode.FFMPEG_NOT_INSTALLED.code },
                    onRetry = { retry, total, seconds ->
                        data.downloadDatas.clear()
                        data.message("No video link found. Page retry $retry of $total in $seconds seconds.")
                    }
                )
                data.retries = retriesBeforePage
                if (parsedResult == null || !Core.child.isRunning) break
                if (parsedResult.isFailed) {
                    finishFailed("Unable to get a WCO video link after the page attempts. ${parsedResult.message.orEmpty()}",
                        parsedResult.errorCode != ErrorCode.FFMPEG_NOT_INSTALLED.code)
                    break
                }
            }
            val preferredDownload = if (m3U8Data != null) {
                DownloadData(
                    Quality.qualityForM3U8Data(m3U8Data),
                    m3U8Data.videoOption.uri,
                    separateAudioLink = m3U8Data.audioOption?.uri ?: "",
                    subtitleOption = m3U8Data.subtitleOption
                )
            } else parsedResult!!.data!! //idk how to do this im tired will fix later
            val downloadLink = preferredDownload.downloadLink
            val qualityOption = preferredDownload.quality
            val saveFile = data.generateEpisodeSaveFile(qualityOption)
            val created = data.createDownload(
                slug,
                qualityOption,
                saveFile
            )
            if (!created) {
                continue
            }
            data.info("Successfully found video link with ${data.retries} retries.")
            try {
                if (downloadLink.endsWith(".m3u8")) {
                    val m3u8Source = data.m3u8Source
                    val m3u8Domain = data.m3u8Domain
                    val m3u8MasterLink = data.m3u8MasterLink
                    //only open the window when the m3u8 data is null.
                    //m3u8data is only passed when downloaded from ChooseM3U8Window
                    if (Defaults.CHOOSE_M3U8_STREAM.boolean()
                        && queue.m3U8Data == null
                        && m3u8Source != null
                        && m3u8Domain != null
                        && m3u8MasterLink != null) {
                        data.finishEpisode(true)
                        val window = ChooseM3U8Window(
                            m3u8MasterLink,
                            m3u8Domain,
                            data.userAgent,
                            m3u8Source,
                            queue.episode
                        )
                        window.open()
                    } else {
                        help.handleM3U8(
                            preferredDownload,
                            saveFile
                        )
                    }
                } else {
                    val fileSizeRetries = Defaults.FILE_SIZE_RETRIES.int()
                    var originalFileSize = 0L
                    var headMode = true
                    var sizeFailure: Exception? = null
                    data.debug("Checking video file size with $fileSizeRetries max retries.")
                    var fileSizeErrorLogged = false
                    for (i in 0..fileSizeRetries) {
                        try {
                            originalFileSize = fileSize(downloadLink, data.userAgent, headMode)
                        } catch (e: MediaTlsException) {
                            sizeFailure = e
                            break
                        } catch (e: java.io.IOException) {
                            sizeFailure = e
                            originalFileSize = 0L
                        }
                        if (originalFileSize <= Constants.minFileSize) {
                            headMode = headMode.not()
                            if (i == fileSizeRetries / 2 && !fileSizeErrorLogged) {
                                data.error(
                                    "Failed to find video file size. Current retries: $i",
                                    important = true
                                )
                                fileSizeErrorLogged = true
                            }
                            continue
                        } else {
                            break
                        }
                    }
                    if (sizeFailure is MediaTlsException) {
                        if (retryConnection(sizeFailure)) continue
                        break
                    }
                    if (originalFileSize <= Constants.minFileSize) {
                        if (sizeFailure != null) {
                            if (retryConnection(sizeFailure)) continue
                            break
                        }
                        if (data.downloadDatas.isNotEmpty()) {
                            data.error(
                                "Failed to find video file size after $fileSizeRetries retries. | Using another quality.",
                                sizeFailure?.message
                            )
                            data.resetDownload()
                            data.downloadDatas.remove(preferredDownload)
                            if (data.mCurrentDownload != null) {
                                Core.child.removeDownload(data.currentDownload)
                                data.mCurrentDownload = null
                            }
                            data.retries++
                            if (data.downloadDatas.isEmpty()) {
                                finishFailed("There are no more WCO qualities to check.")
                            }
                            continue
                        } else {
                            finishFailed("WCO could not find a video file size and has no more qualities to check.")
                            continue
                        }
                    }
                    data.debug(
                        "Successfully found video file size of: ${Tools.bytesToString(originalFileSize)}"
                    )
                    if (saveFile.exists()) {
                        if (saveFile.length() >= originalFileSize) {
                            data.message("(IO) Skipping completed video.")
                            data.currentDownload.downloadPath = saveFile.absolutePath
                            data.currentDownload.fileSize = originalFileSize
                            data.currentDownload.downloading = false
                            data.currentDownload.queued = false
                            data.currentDownload.update()
                            BoxMaker.makeDownloadedEpisode(data.currentDownload.slug)
                            data.finishEpisode()
                            continue
                        }
                    } else {
                        var fileRetries = 0
                        var fileError: Exception? = null
                        for (i in 0..3) {
                            try {
                                val created = saveFile.createNewFile()
                                if (!created) {
                                    throw Exception("No error thrown. $i")
                                }
                            } catch (e: Exception) {
                                fileError = e
                                fileRetries++
                                continue
                            }
                        }
                        if (!saveFile.exists()) {
                            data.error(
                                "Failed to create video file after 3 retries. | Skipping episode",
                                fileError
                            )
                            data.finishEpisode()
                            continue
                        }
                    }
                    data.message("Starting download with ${qualityOption.tag} quality.")
                    data.currentDownload.queued = false
                    data.currentDownload.downloading = true
                    data.currentDownload.fileSize = originalFileSize
                    Core.child.addDownload(data.currentDownload)
                    data.currentDownload.update()
                    data.undriver.blank()
                    downloadVideo(
                        downloadLink,
                        saveFile,
                        data
                    )
                    data.currentDownload.downloading = false
                    //second time to ensure ui update
                    data.currentDownload.update()
                    if (saveFile.exists() && saveFile.length() >= originalFileSize) {
                        Core.child.downloadThread.incrementDownloadsFinished()
                        data.message("Successfully downloaded with ${qualityOption.tag} quality.")
                        BoxMaker.makeDownloadedEpisode(
                            data.currentDownload.slug
                        )
                        help.handleSecondVideo()
                        data.finishEpisode()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is java.io.IOException) {
                    if (retryConnection(e)) continue
                    break
                }
                data.currentDownload.queued = true
                data.currentDownload.downloading = false
                data.currentDownload.update()
                if (e.message?.contains("Connection reset", true) == false) {
                    data.error(
                        "Failed to download video. | Retrying...",
                        e,
                        true
                    )
                }
                data.retries++
            }
        }
        killDriver()
    }

    fun killDriver() {
        data.base.killDriver()
    }
}