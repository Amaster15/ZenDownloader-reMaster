package nobility.downloader.core.scraper.player

import kotlinx.coroutines.*
import nobility.downloader.core.Core
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

object PlaybackTelemetry {
    data class Sample(val position: Double, val duration: Double)
    @JvmStatic fun parse(text: String, mpv: Boolean, knownDuration: Double = 0.0): Sample? {
        if (mpv) {
            val match = Regex("ZEN_PROGRESS:([0-9.]+):([0-9.]+)").findAll(text).lastOrNull() ?: return null
            return Sample(match.groupValues[1].toDoubleOrNull() ?: return null, match.groupValues[2].toDoubleOrNull() ?: return null)
        }
        val durationMatch = Regex("Duration: (\\d+):(\\d+):([0-9.]+)").find(text)
        val duration = durationMatch?.let { it.groupValues[1].toDouble()*3600 + it.groupValues[2].toDouble()*60 + it.groupValues[3].toDouble() } ?: knownDuration
        val position = Regex("(?:^|[\\r\\n])\\s*([0-9.]+)\\s+(?:A-V|M-V|M-A):").findAll(text).lastOrNull()?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return Sample(position, duration)
    }
    fun log(process: Process, file: File, mpv: Boolean, progress: (Double, Double) -> Unit) {
        Core.taskScope.launch(Dispatchers.IO) {
            var offset = 0L; var duration = 0.0; var tail = ""
            try {
                while (isActive) {
                    if (file.isFile) RandomAccessFile(file, "r").use { reader ->
                        if (reader.length() < offset) offset = 0
                        reader.seek(offset)
                        val buffer = ByteArray(minOf(reader.length() - offset, 65536L).toInt())
                        reader.readFully(buffer); offset += buffer.size
                        tail = (tail + String(buffer)).takeLast(131072)
                        parse(tail, mpv, duration)?.let { duration = it.duration; progress(it.position, duration) }
                    }
                    if (!process.isAlive) break
                    delay(1000)
                }
            } finally { file.delete() }
        }
    }
    fun port(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
    fun vlc(process: Process, port: Int, progress: (Double, Double) -> Unit) {
        Core.taskScope.launch(Dispatchers.IO) {
            while (isActive && process.isAlive) {
                runCatching {
                    Socket().use { socket ->
                        socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 500)
                        socket.soTimeout = 1000
                        val input = socket.getInputStream().bufferedReader()
                        val output = socket.getOutputStream().bufferedWriter()
                        fun query(command: String): Double? {
                            output.write("$command\n"); output.flush()
                            repeat(30) {
                                val line = input.readLine() ?: return null
                                Regex("^(?:>\\s*)?(\\d+)\\s*$").matchEntire(line.trim())?.let { return it.groupValues[1].toDouble() }
                            }
                            return null
                        }
                        val position = query("get_time")
                        val duration = query("get_length")
                        if (position != null && duration != null) progress(position, duration)
                    }
                }
                delay(2000)
            }
        }
    }
}
