package nobility.downloader.core.scraper.video_download

import java.io.IOException
import java.net.HttpURLConnection
import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLHandshakeException

class MediaTlsException(host: String, cause: SSLHandshakeException) : IOException(
    generateSequence<Throwable>(cause) { it.cause }
        .filterIsInstance<CertificateExpiredException>().firstOrNull()?.let {
            "The video server $host has an expired HTTPS certificate (${it.message}). " +
                "The server operator must renew it before this download can connect."
        } ?: "HTTPS certificate validation failed for video server $host: ${cause.message}",
    cause
)

object MediaSizeProbe {
    @Throws(IOException::class)
    fun read(connection: HttpURLConnection, headMode: Boolean): Long {
        try {
            connection.requestMethod = if (headMode) "HEAD" else "GET"
            connection.useCaches = false
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (!headMode) connection.setRequestProperty("Range", "bytes=0-0")
            // Unlike getContentLengthLong(), this exposes TLS and HTTP failures.
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("Video server ${connection.url.host} returned HTTP $status (${connection.requestMethod}).")
            }
            if (connection.contentType.orEmpty().substringBefore(';').trim().equals("text/html", true)) {
                throw IOException("Video server ${connection.url.host} returned an HTML page instead of a video.")
            }
            val length = if (status == HttpURLConnection.HTTP_PARTIAL) {
                val range = connection.getHeaderField("Content-Range").orEmpty()
                Regex("""bytes\s+\d+-\d+/(\d+)""", RegexOption.IGNORE_CASE)
                    .matchEntire(range.trim())?.groupValues?.get(1)?.toLongOrNull() ?: -1L
            } else connection.contentLengthLong
            if (length <= 0) throw IOException("Video server ${connection.url.host} did not provide a valid total file size (${connection.requestMethod}).")
            return length
        } catch (e: SSLHandshakeException) {
            throw MediaTlsException(connection.url.host, e)
        } finally {
            connection.disconnect()
        }
    }
}
