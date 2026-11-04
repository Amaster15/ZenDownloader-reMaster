package nobility.downloader.utils

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.jetbrains.skia.Image

/** Cache complete, decodable images; never append a failed HTML response to an image. */
object ThumbnailCache {
    private val slots = Semaphore(4)
    @JvmStatic fun valid(file: File): Boolean = runCatching {
        file.isFile && file.length() in 1..(10L * 1024 * 1024) &&
            Image.makeFromEncoded(file.readBytes()).use { it.width > 0 && it.height > 0 }
    }.getOrDefault(false)

    suspend fun ensure(file: File, source: String?): Boolean = slots.withPermit {
        if (valid(file)) return@withPermit true
        val archive = "https://raw.githubusercontent.com/NobilityDeviant/ZenDownloaderSeriesImages/master/series_images/" +
            java.net.URLEncoder.encode(file.name, Charsets.UTF_8).replace("+", "%20")
        listOfNotNull(source?.takeIf { it.isNotBlank() }, archive).any { fetch(file, it) }
    }

    private fun fetch(file: File, source: String): Boolean {
        val url = runCatching { URI(source).let { if (it.scheme == null) URI("https:$source") else it } }.getOrNull()
            ?: return false
        if (url.scheme !in listOf("https", "http")) return false
        var connection: HttpURLConnection? = null
        var temp: File? = null
        return try {
            connection = url.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.setRequestProperty("Referer", "https://www.wcoflix.tv/")
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.5")
            if (connection.responseCode != 200) return false
            file.parentFile.mkdirs()
            temp = File.createTempFile("thumbnail-", ".tmp", file.parentFile)
            connection.inputStream.use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 10 * 1024 * 1024) { "Thumbnail too large" }
                    output.write(buffer, 0, count)
                }
            } }
            if (!valid(temp)) return false
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (_: Exception) { false }
        finally { temp?.delete(); connection?.disconnect() }
    }
}
