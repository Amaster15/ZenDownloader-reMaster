package nobility.downloader.core.scraper.player

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.Executors

/** Private, fixed-playlist loopback bridge; never accepts remote URLs from HTTP requests. */
class HiAnimePlaybackServer(
    playlist: String,
    private val segmentCount: Int,
    private val loadSegment: suspend (Int) -> ByteArray
) : Closeable {
    private val token = UUID.randomUUID().toString()
    private val executor = Executors.newFixedThreadPool(2) { r -> Thread(r, "watch-stream").apply { isDaemon = true } }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val cache = object : LinkedHashMap<Int, ByteArray>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ByteArray>?) = size > 3
    }
    private val body: ByteArray
    val url: String get() = "http://127.0.0.1:${server.address.port}/$token/index.m3u8"
    init {
        var index = 0
        body = playlist.lines().joinToString("\n") { line ->
            if (line.isBlank() || line.trimStart().startsWith('#')) line
            else "/$token/segment/${index++}.ts"
        }.toByteArray()
        require(index == segmentCount && index > 0) { "Playlist changed while preparing playback" }
        server.executor = executor
        server.createContext("/") { exchange ->
            try {
                val path = exchange.requestURI.path
                if (exchange.requestMethod !in listOf("GET", "HEAD")) {
                    exchange.sendResponseHeaders(405, -1)
                } else if (path == "/$token/index.m3u8") {
                    exchange.responseHeaders.set("Content-Type", "application/vnd.apple.mpegurl")
                    exchange.sendResponseHeaders(200, if (exchange.requestMethod == "HEAD") -1 else body.size.toLong())
                    if (exchange.requestMethod != "HEAD") exchange.responseBody.write(body)
                } else {
                    val i = Regex("^/$token/segment/(\\d+)\\.ts$").matchEntire(path)?.groupValues?.get(1)?.toIntOrNull()
                    if (i == null || i !in 0 until segmentCount) exchange.sendResponseHeaders(404, -1)
                    else {
                        val payload = synchronized(cache) { cache[i] } ?: runBlocking { loadSegment(i) }.also {
                            require(it.size <= 24 * 1024 * 1024)
                            synchronized(cache) { cache[i] = it }
                        }
                        exchange.responseHeaders.set("Content-Type", "video/mp2t")
                        exchange.sendResponseHeaders(200, if (exchange.requestMethod == "HEAD") -1 else payload.size.toLong())
                        if (exchange.requestMethod != "HEAD") exchange.responseBody.write(payload)
                    }
                }
            } catch (_: Exception) {
                runCatching { exchange.sendResponseHeaders(502, -1) }
            } finally { exchange.close() }
        }
        server.start()
    }
    override fun close() {
        server.stop(0)
        executor.shutdownNow()
        synchronized(cache) { cache.clear() }
    }
}
