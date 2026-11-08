package nobility.downloader.core.scraper.player

import Resource
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.driver.undetected_chrome.SysUtil
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.fileExists
import java.io.IOException


@Suppress("UNUSED")
object PlayVideoWithVLC {

    @JvmOverloads
    fun play(
        videoUrl: String,
        userAgent: String,
        referrer: String,
        onStarted: (Process) -> Unit = {},
        subtitle: java.io.File? = null,
        resumeSeconds: Double = 0.0,
        progress: ((Double, Double) -> Unit)? = null
    ): Resource<Boolean> {

        val vlcPath = findVlcPath()

        if (vlcPath == null) {
            return Resource.Error("VLC was not found. Install VLC or set its executable path in Settings.")
        }

        val command = mutableListOf(
            vlcPath,
            "--no-one-instance",
            "--http-user-agent=$userAgent",
            "--http-referrer=$referrer",
            "--no-video-title-show",
            //"--file-logging",
            //"--logfile=${File("vlc.log").absolutePath}",
            //"--verbose=2",
            //"--play-and-exit",
            videoUrl
        )

        if (resumeSeconds > 0) command.add(1, "--start-time=$resumeSeconds")
        val port = if (progress != null) PlaybackTelemetry.port() else 0
        if (progress != null) command.addAll(1, listOf("--extraintf=rc", "--rc-host=127.0.0.1:$port"))
        if (subtitle != null) command.add(1, "--sub-file=${subtitle.absolutePath}")
        return try {
            val process = ProcessBuilder(command).inheritIO().start()
            if (process.waitFor(750, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                return Resource.Error("VLC exited during startup (exit ${process.exitValue()}). Check its executable path and Error Console.")
            }
            nobility.downloader.core.Core.child.runningProcesses.add(process)
            if (progress != null) PlaybackTelemetry.vlc(process, port, progress)
            onStarted(process)
            Resource.Success(true)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    fun findVlcPath(
        customVlcPath: String? = Defaults.VLC_PATH.string()
    ): String? {

        if (customVlcPath?.fileExists() == true) {
            return customVlcPath
        }

        if (isVlcCommandAvailable()) {
            return "vlc"
        }

        val possibles = mutableSetOf<String>()

        if (SysUtil.isWindows) {
            possibles.add("C:\\Program Files\\VideoLAN\\VLC\\vlc.exe")
            possibles.add("C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe")
        } else if (SysUtil.isMacOs) {
            possibles.add("/Applications/VLC.app/Contents/MacOS/VLC")
        } else {
            possibles.add("/usr/bin/vlc")
            possibles.add("/usr/local/bin/vlc")
        }
        return possibles.firstOrNull { it.fileExists() }
    }

    private fun isVlcCommandAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("vlc", "--version")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else process.exitValue() == 0
        } catch (_: IOException) {
            false
        } catch (_: InterruptedException) {
            false
        }
    }
}