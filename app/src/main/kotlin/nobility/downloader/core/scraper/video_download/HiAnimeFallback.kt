package nobility.downloader.core.scraper.video_download

import com.google.gson.JsonParser
import kotlinx.coroutines.*
import nobility.downloader.core.settings.Quality
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.time.Duration
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Independent provider: never shares WCO TLS exceptions or partial media files. */
class HiAnimeFallback {
    data class Match(val page: String, val animeId: Long, val episode: Int, val language: String, val player: String)
    data class Stream(val playlist: String, val quality: Quality, val seconds: Double, val segments: List<String>)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL).build()
    private val agent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36"

    private suspend fun bytes(url: String, referer: String = "https://hianime.ms/"): ByteArray {
        require(URI(url).scheme == "https") { "HiAnime returned a non-HTTPS URL" }
        currentCoroutineContext().ensureActive()
        val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(45))
            .header("User-Agent", agent).header("Referer", referer).GET().build()
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        val response = try {
            while (!future.isDone) { currentCoroutineContext().ensureActive(); delay(100) }
            future.get()
        } finally { if (!future.isDone) future.cancel(true) }
        if (response.statusCode() !in 200..299) throw IOException("HiAnime request failed: HTTP ${response.statusCode()} on ${URI(url).host}")
        val result = response.body()
        require(result.size <= 24 * 1024 * 1024) { "HiAnime response is too large" }
        return result
    }
    private suspend fun text(url: String, referer: String = "https://hianime.ms/") = bytes(url, referer).toString(Charsets.UTF_8)

    suspend fun find(series: String, episodeName: String): Match {
        val number = episodeNumber(episodeName) ?: error("Cannot identify an unambiguous episode number")
        val language = language(episodeName) ?: error("Episode does not explicitly identify dub or sub language")
        val search = "https://hianime.ms/search?q=" + URLEncoder.encode(series, Charsets.UTF_8)
        val candidates = matchingSeries(text(search), series)
        require(candidates.size == 1) { "HiAnime has no unique exact match for $series" }
        val details = candidates.single()
        val doc = Jsoup.parse(text(details), details)
        val links = doc.select("a[href]").map { it.absUrl("href") }.filter {
            URI(it).host == "hianime.ms" && it.substringAfterLast('/').startsWith("watch-")
        }.distinct()
        val landing = links.firstOrNull { it.contains("-episode-$number-") } ?: links.firstOrNull()
            ?: error("HiAnime has no episode page for the matched show")
        val page = Jsoup.parse(text(landing), landing)
        val episodeLink = page.select("a[data-episode]").singleOrNull { it.attr("data-episode") == number.toString() }
            ?: error("Episode $number is not listed on HiAnime")
        require(episodeLink.attr("data-$language") == "1") { "HiAnime does not list episode $number in $language" }
        val target = episodeLink.absUrl("href")
        require(URI(target).host == "hianime.ms")
        val episodeDoc = if (target == landing) page else Jsoup.parse(text(target), target)
        val about = episodeDoc.select("a[href]").any {
            it.absUrl("href").trimEnd('/') == details.trimEnd('/') && normalize(it.text()) == normalize(series)
        }
        require(about) { "HiAnime episode identity did not match the selected series" }
        val cfg = JsonParser.parseString(episodeDoc.selectFirst("[data-hk-watch]")?.attr("data-hk-watch")
            ?: error("HiAnime player metadata is missing")).asJsonObject
        require(!cfg.get("movie").asBoolean && cfg.get("ep").asInt == number)
        require(episodeDoc.select("button[data-server=vidnest-pahe][data-version=$language]").isNotEmpty()) {
            "Ayame player is unavailable in $language"
        }
        val id = cfg.get("al").asLong
        require(id > 0)
        val player = cfg.getAsJsonObject("mirror").get("vidnest-pahe").asString
            .replace("{al}", id.toString()).replace("{ep}", number.toString()).replace("{lang}", language)
        require(URI(player).scheme == "https" && URI(player).host == "vidnest.fun")
        return Match(target, id, number, language, player)
    }

    /** Resolve only the explicit episode/language selected in Watch. */
    suspend fun watchMatch(url: String, language: String): Match {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host == "hianime.ms" && uri.path.startsWith("/watch-"))
        require(language in listOf("dub", "sub"))
        val doc = Jsoup.parse(text(url), url)
        val cfg = JsonParser.parseString(doc.selectFirst("[data-hk-watch]")?.attr("data-hk-watch")
            ?: error("HiAnime player metadata is missing")).asJsonObject
        require(!cfg.get("movie").asBoolean) { "Movie playback is not supported yet" }
        require(doc.select("button[data-server=vidnest-pahe][data-version=$language]").isNotEmpty()) {
            "Ayame player is unavailable in $language"
        }
        val id = cfg.get("al").asLong
        val ep = cfg.get("ep").asInt
        require(id > 0 && ep > 0)
        val player = cfg.getAsJsonObject("mirror").get("vidnest-pahe").asString
            .replace("{al}", id.toString()).replace("{ep}", ep.toString()).replace("{lang}", language)
        require(URI(player).scheme == "https" && URI(player).host == "vidnest.fun")
        return Match(url, id, ep, language, player)
    }

    suspend fun resolve(match: Match, requested: Quality): Stream {
        // Discover the public player response configuration rather than storing a rotating key.
        val page = Jsoup.parse(text(match.player), match.player)
        var responseKey: String? = null
        val keyPattern = Regex("let a=\"([^\"]+)\",i=\"https://new\\.vidnest\\.fun\"")
        for (script in page.select("script[src]")) {
            val url = script.absUrl("src")
            if (URI(url).host != "vidnest.fun" || !URI(url).path.startsWith("/_next/static/chunks/")) continue
            val source = text(url, "https://vidnest.fun/")
            responseKey = keyPattern.find(source)?.groupValues?.get(1)
            if (responseKey != null) break
        }
        val key = responseKey ?: error("Ayame's player configuration has changed")
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val api = "https://new.vidnest.fun/hianime/anime/${match.animeId}/${match.episode}/${match.language}/hd-2?_n=" +
            Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
        var result = JsonParser.parseString(text(api, "https://vidnest.fun/")).asJsonObject
        if (result.has("v") && result.get("v").asInt == 2) {
            val data = Base64.getUrlDecoder().decode(result.get("d").asString)
            require(data.size > 28)
            val secret = MessageDigest.getInstance("SHA-256").digest((result.get("n").asString + "|" + key).toByteArray())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, data.copyOfRange(0, 12)))
            result = JsonParser.parseString(String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)).asJsonObject
        }
        require(result.get("success")?.asBoolean != false) { "Ayame has no stream for this episode" }
        val sources = result.getAsJsonArray("sources") ?: error("Ayame returned no sources")
        val master = sources.firstOrNull()?.asJsonObject?.get("file")?.asString ?: error("Ayame returned an empty source list")
        val variants = variants(text(master, "https://megaplay.buzz/"), master)
        val chosen = variants.filter { it.first <= requested.resolution }.maxByOrNull { it.first }
            ?: variants.minByOrNull { it.first } ?: error("No supported HiAnime quality")
        val media = text(chosen.second, "https://megaplay.buzz/")
        val parts = segments(media, chosen.second)
        val seconds = Regex("#EXTINF:([0-9.]+)").findAll(media).sumOf { it.groupValues[1].toDouble() }
        require(seconds > 0 && parts.isNotEmpty())
        return Stream(chosen.second, Quality.qualityForResolution(chosen.first), seconds, parts)
    }

    suspend fun playbackPlaylist(stream: Stream): String {
        val playlist = text(stream.playlist, "https://megaplay.buzz/")
        require(segments(playlist, stream.playlist) == stream.segments) { "Playlist changed; please press Play again" }
        return playlist
    }

    suspend fun playbackSegment(url: String): ByteArray = tsPayload(bytes(url, "https://megaplay.buzz/"))

    suspend fun download(stream: Stream, output: File, ffmpeg: String, running: () -> Boolean, progress: (Int) -> Unit) = coroutineScope {
        require(!output.exists()) { "HiAnime output already exists; refusing to overwrite it" }
        output.parentFile.mkdirs()
        val folder = Files.createTempDirectory(output.parentFile.toPath(), ".hianime-").toFile()
        val tempVideo = File(folder, "episode.mp4")
        var process: Process? = null
        val owner = currentCoroutineContext().job
        val monitor = launch { while (isActive) { if (!running()) { owner.cancel(CancellationException("Download stopped")); break }; delay(200) } }
        try {
            val joined = File(folder, "episode.ts")
            joined.outputStream().buffered().use { out ->
                stream.segments.forEachIndexed { index, url ->
                    ensureActive()
                    if (!running()) throw CancellationException("Download stopped")
                    var payload: ByteArray? = null
                    for (attempt in 0..2) {
                        try { payload = tsPayload(bytes(url, "https://megaplay.buzz/")); break }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { if (attempt == 2) throw e; delay(1000) }
                    }
                    out.write(requireNotNull(payload))
                    progress((index + 1) * 95 / stream.segments.size)
                }
            }
            ensureActive()
            process = ProcessBuilder(ffmpeg, "-nostdin", "-v", "error", "-n", "-i", joined.absolutePath,
                "-map", "0:v:0", "-map", "0:a:0", "-c", "copy", "-movflags", "+faststart", tempVideo.absolutePath)
                .redirectErrorStream(true).redirectOutput(File(folder, "ffmpeg.log")).start()
            while (process.isAlive) { ensureActive(); delay(200) }
            require(process.exitValue() == 0 && tempVideo.length() > 1024) { "HiAnime MP4 conversion failed" }
            // All segments must be present and muxing must succeed before publishing the file.
            ensureActive()
            if (!running()) throw CancellationException("Download stopped")
            Files.move(tempVideo.toPath(), output.toPath())
            progress(100)
        } finally {
            monitor.cancel()
            process?.let { if (it.isAlive) { it.destroyForcibly(); it.waitFor() } }
            folder.deleteRecursively()
        }
    }

    companion object {
        @JvmStatic fun normalize(title: String) = Normalizer.normalize(title, Normalizer.Form.NFKC)
            .lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
        @JvmStatic fun episodeNumber(name: String): Int? {
            val matches = Regex("(?i)\\bepisode[ -]+(\\d+)(?![\\d.])\\b").findAll(name).toList()
            return matches.singleOrNull()?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
        }
        @JvmStatic fun language(name: String): String? = when {
            name.contains("English Dubbed", true) -> "dub"
            name.contains("English Subbed", true) || name.contains("English Subtitled", true) -> "sub"
            else -> null
        }
        @JvmStatic fun matchingSeries(html: String, title: String): List<String> = Jsoup.parse(html, "https://hianime.ms/")
            .select("a[href]").filter {
                val uri = URI(it.absUrl("href"))
                uri.host == "hianime.ms" && uri.path.startsWith("/details/") && normalize(it.text()) == normalize(title)
            }
            .map { it.absUrl("href") }.distinct()
        @JvmStatic fun variants(source: String, base: String): List<Pair<Int, String>> {
            require(source.startsWith("#EXTM3U")) { "HiAnime returned an invalid playlist" }
            val lines = source.lines().map { it.trim() }
            return lines.mapIndexedNotNull { i, line ->
                if (!line.startsWith("#EXT-X-STREAM-INF:")) return@mapIndexedNotNull null
                val height = Regex("RESOLUTION=\\d+x(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                val path = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith('#') }
                if (height !in listOf(576, 720, 1080) || path == null) null else height!! to URI(base).resolve(path).toString()
            }
        }
        @JvmStatic fun segments(source: String, base: String): List<String> {
            require(source.startsWith("#EXTM3U") && source.contains("#EXT-X-ENDLIST")) { "HiAnime did not return a complete VOD playlist" }
            require(!source.contains("#EXT-X-KEY") && !source.contains("#EXT-X-MAP") && !source.contains("#EXT-X-BYTERANGE")) {
                "This HiAnime stream format is not supported"
            }
            return source.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith('#') }
                .map { URI(base).resolve(it).also { u -> require(u.scheme == "https") }.toString() }
                .also { require(it.size in 1..5000) }
        }
        @JvmStatic fun tsPayload(bytes: ByteArray): ByteArray {
            val start = (0..minOf(4096, bytes.size - 188 * 5)).firstOrNull { offset ->
                (0..4).all { bytes[offset + it * 188] == 0x47.toByte() }
            } ?: error("HiAnime segment has no MPEG-TS payload")
            require((bytes.size - start) % 188 == 0 && (start until bytes.size step 188).all { bytes[it] == 0x47.toByte() }) {
                "HiAnime segment is truncated or corrupt"
            }
            return bytes.copyOfRange(start, bytes.size)
        }
    }
}
