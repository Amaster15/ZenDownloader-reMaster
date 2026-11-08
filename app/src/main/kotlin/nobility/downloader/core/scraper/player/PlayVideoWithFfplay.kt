package nobility.downloader.core.scraper.player

import Resource
import nobility.downloader.core.Core
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.VideoUtil

object PlayVideoWithFfplay {

    //0, 9 volume keys
    @JvmOverloads
    fun play(
        url: String,
        userAgent: String,
        windowTitle: String = url,
        ffplayPath: String = VideoUtil.FfmpegPathHolder.ffplayPath,
        referer: String = "",
        onStarted: (Process) -> Unit = {},
        subtitle: java.io.File? = null,
        resumeSeconds: Double = 0.0,
        progress: ((Double, Double) -> Unit)? = null
    ): Resource<Boolean> {

        if (ffplayPath.isEmpty()) {
            return Resource.Error("ffplay not found.")
        }

        val command = mutableListOf(
            ffplayPath,
            "-volume", "50",
            "-window_title", windowTitle,
            "-headers", "User-Agent: $userAgent\r\n" +
                if (referer.isNotEmpty()) "Referer: $referer\r\n" else "",
            //"-autoexit",
            "-x", "800",
            "-y", "600",
            //"-loglevel", "info",
            url
        )

        if (resumeSeconds > 0) command.addAll(1, listOf("-ss", resumeSeconds.toString()))
        if (progress != null) command.add(1, "-stats")
        if (subtitle != null) {
            val escaped = subtitle.absolutePath.replace("\\", "/").replace(":", "\\:").replace("'", "'\\\\''")
            command.addAll(1, listOf("-vf", "subtitles=filename='$escaped'"))
        }
        val log = java.io.File.createTempFile("zen-player-", ".log")
        return try {

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start()

            if (process.waitFor(750, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                val detail = log.readText().takeLast(2000).trim()
                log.delete()
                return Resource.Error("FFplay could not start (exit ${process.exitValue()}).\n$detail")
            }
            process.onExit().thenRun {
                if (process.exitValue() != 0) {
                    val detail = runCatching { log.readText().takeLast(2000).trim() }.getOrDefault("")
                    nobility.downloader.utils.FrogLog.error("FFplay stopped (exit ${process.exitValue()}).", detail)
                }
                if (progress == null) log.delete()
            }
            if (progress != null) PlaybackTelemetry.log(process, log, false, progress)
            Core.child.runningProcesses.add(process)
            onStarted(process)

            Resource.Success(true)
        } catch (e: Exception) {
            log.delete()
            Resource.Error(e)
        }
    }
}