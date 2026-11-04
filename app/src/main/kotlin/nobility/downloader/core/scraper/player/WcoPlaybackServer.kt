package nobility.downloader.core.scraper.player

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Streams one resolved video using the downloader's connection policy; supports player seeking. */
class WcoPlaybackServer(
    private val connect: suspend () -> HttpURLConnection,
    private val failure: (String) -> Unit = {}
) : Closeable {
    private val path = "/${UUID.randomUUID()}/video.mp4"
    private val active = ConcurrentHashMap.newKeySet<HttpURLConnection>()
    private val executor = Executors.newFixedThreadPool(3) { r -> Thread(r, "wco-playback").apply { isDaemon = true } }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val url get() = "http://127.0.0.1:${server.address.port}$path"
    init {
        server.executor = executor
        server.createContext("/") { exchange ->
            var upstream: HttpURLConnection? = null
            var headersSent = false
            try {
                if (exchange.requestURI.path != path) {
                    exchange.sendResponseHeaders(404, -1)
                } else if (exchange.requestMethod !in listOf("HEAD", "GET")) {
                    exchange.sendResponseHeaders(405, -1)
                } else {
                    val head = exchange.requestMethod == "HEAD"
                    upstream = runBlocking { connect() }
                    active.add(upstream)
                    val range = exchange.requestHeaders.getFirst("Range")
                    require(range == null || Regex("bytes=\\d*-\\d*").matches(range)) { "Unsupported seek range" }
                    if (range != null) upstream.setRequestProperty("Range", range)
                    else upstream.setRequestProperty("Range", if (head) "bytes=0-0" else "bytes=0-")
                    val code = upstream.responseCode
                    if (code !in listOf(200, 206, 416)) error("WCO video server returned HTTP $code. Please retry for a fresh link.")
                    for (header in listOf("Content-Type", "Content-Range", "Accept-Ranges")) {
                        upstream.getHeaderField(header)?.let { exchange.responseHeaders.set(header, it) }
                    }
                    exchange.responseHeaders.set("Cache-Control", "no-store")
                    val length = upstream.contentLengthLong
                    if (head) {
                        val total = upstream.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: length
                        exchange.responseHeaders.remove("Content-Range")
                        if (total >= 0) exchange.responseHeaders.set("Content-Length", total.toString())
                        exchange.sendResponseHeaders(if (code == 206) 200 else code, -1)
                    } else {
                        exchange.sendResponseHeaders(code, if (code == 416) -1 else if (length > 0) length else 0)
                        headersSent = true
                        if (code != 416) upstream.inputStream.use { it.copyTo(exchange.responseBody, 64 * 1024) }
                    }
                }
            } catch (e: Exception) {
                // A player closing or seeking can close an already-started response normally.
                if (!headersSent) {
                    failure(e.message ?: "WCO playback connection failed")
                    runCatching { exchange.sendResponseHeaders(502, -1) }
                }
            } finally {
                upstream?.let { active.remove(it); it.disconnect() }
                exchange.close()
            }
        }
        server.start()
    }
    override fun close() {
        server.stop(0)
        active.forEach { it.disconnect() }
        active.clear()
        executor.shutdownNow()
    }
}
