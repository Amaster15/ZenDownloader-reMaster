package nobility.downloader.core.scraper.player

import kotlinx.coroutines.*
import Resource
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.scraper.video_download.ErrorCode
import nobility.downloader.core.scraper.WatchCatalog
import nobility.downloader.core.scraper.data.DownloadQueue
import nobility.downloader.core.scraper.video_download.HiAnimeFallback
import nobility.downloader.core.scraper.video_download.VideoDownloadHelper
import nobility.downloader.core.settings.Quality
import nobility.downloader.core.settings.VideoPlayer

object WatchPlayback {
    suspend fun play(show: WatchCatalog.Show, entry: WatchCatalog.Entry, quality: Quality, status: suspend (String) -> Unit) = withContext(Dispatchers.IO) {
        var userAgent = "Mozilla/5.0"
        val referer = ""
        var bridge: java.io.Closeable? = null
        try {
            val url: String
            if (show.provider == WatchCatalog.Provider.ANIMEFREAK) {
                status("Resolving the AnimeFreak player…")
                val episode = nobility.downloader.core.scraper.AnimeFreak.Episode(entry.url.trimEnd('/').substringAfterLast('/').toInt(), entry.url)
                val stream = nobility.downloader.core.scraper.AnimeFreak.resolve(episode)
                val local = nobility.downloader.core.scraper.AnimeFreak.playback(stream)
                bridge = local
                url = local.url
            } else if (show.provider == WatchCatalog.Provider.HIANIME) {
                status("Finding the selected HiAnime ${entry.language} player…")
                val provider = HiAnimeFallback()
                val match = provider.watchMatch(entry.url, entry.language)
                status("Resolving a fresh video link…")
                val stream = provider.resolve(match, quality)
                val playlist = provider.playbackPlaylist(stream)
                val local = HiAnimePlaybackServer(playlist, stream.segments.size) { index ->
                    provider.playbackSegment(stream.segments[index])
                }
                bridge = local
                url = local.url
            } else {
                val help = VideoDownloadHelper(DownloadQueue(WatchCatalog.wcoEpisode(show, entry)), quality)
                try {
                    status("Loading WCO player. Waiting for any player countdown…")
                    suspend fun resolveAccessible(): Resource<nobility.downloader.core.scraper.data.DownloadData> {
                        val parsed = help.parseQualities(WatchCatalog.slug(entry.url))
                        val candidate = parsed.data ?: return parsed
                        if (!candidate.downloadLink.contains(".m3u8")) {
                            status("Checking that the video server can stream this link…")
                            try {
                                val size = nobility.downloader.core.scraper.video_download.Functions.fileSize(
                                    candidate.downloadLink, help.videoDownloadData.userAgent, false)
                                if (size <= 0) return Resource.Error("Video server returned no playable media")
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { return Resource.Error(e.message ?: "Video link is unavailable") }
                        }
                        return parsed
                    }
                    var result = resolveAccessible()
                    val retries = Defaults.PAGE_LINK_RETRIES.int().coerceIn(1, 100)
                    val seconds = Defaults.PAGE_LINK_RETRY_DELAY_SECONDS.int().coerceIn(1, 100)
                    var attempt = 0
                    while (result.isFailed && result.errorCode != ErrorCode.FFMPEG_NOT_INSTALLED.code && attempt < retries) {
                        attempt++
                        for (left in seconds downTo 1) {
                            status("WCO returned no link. Retry $attempt/$retries in $left seconds. Cancel to choose another provider.")
                            delay(1000)
                        }
                        help.videoDownloadData.downloadDatas.clear()
                        help.videoDownloadData.resRetries = 0
                        status("Loading WCO player (retry $attempt/$retries)…")
                        result = resolveAccessible()
                    }
                    currentCoroutineContext().ensureActive()
                    val stream = result.data ?: error(result.message ?: "WCO did not return a video link. Try again or select HiAnime.")
                    userAgent = help.videoDownloadData.userAgent
                    if (stream.downloadLink.contains(".m3u8")) {
                        url = help.videoDownloadData.m3u8MasterLink ?: stream.downloadLink
                    } else {
                        val local = WcoPlaybackServer(connect = {
                            nobility.downloader.core.scraper.video_download.Functions.wcoConnection(
                                stream.downloadLink, help.videoDownloadData.userAgent, supportEncoding = false)
                        }, failure = { message ->
                            nobility.downloader.core.Core.taskScope.launch { status(message) }
                        })
                        bridge = local
                        url = local.url
                    }
                } finally {
                    withContext(NonCancellable) { help.videoDownloadData.base.killDriver() }
                }
            }
            currentCoroutineContext().ensureActive()
            status("Opening player…")
            val title = "${show.title} — ${entry.title}"
            val playbackBridge = bridge
            val player = VideoPlayer.playerForSetting()
            val started: (Process) -> Unit = { process ->
                process.onExit().thenRun {
                    playbackBridge?.close()
                    nobility.downloader.core.Core.taskScope.launch {
                        status(if (process.exitValue() == 0) "$player closed."
                            else "$player exited (code ${process.exitValue()}). See Error Console or select another player.")
                    }
                }
            }
            val result = if (VideoPlayer.playerForSetting() == VideoPlayer.FFPLAY)
                PlayVideoWithFfplay.play(url, userAgent, title, referer = referer, onStarted = started)
            else if (player == VideoPlayer.VLC)
                PlayVideoWithVLC.play(url, userAgent, referer, onStarted = started)
            else PlayVideoWithMpv.play(url, userAgent, title, referer = referer, onStarted = started)
            if (result.isFailed) error(result.message ?: "Unable to start video player")
            bridge = null // The player now owns cleanup, including when it is stopped on app shutdown.
        } finally { bridge?.close() }
    }
}
