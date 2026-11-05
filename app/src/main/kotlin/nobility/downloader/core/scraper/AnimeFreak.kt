package nobility.downloader.core.scraper

import kotlinx.coroutines.*
import nobility.downloader.core.driver.DriverBase
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.VideoUtil
import nobility.downloader.utils.EpisodeNaming
import org.jsoup.Jsoup
import org.openqa.selenium.By
import com.google.gson.JsonParser
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.TimeUnit

object AnimeFreak {
    data class Episode(val number: Int, val url: String)
    data class Series(val title: String, val episodes: List<Episode>)
    data class Stream(val url: String, val referer: String, val userAgent: String)

    fun seriesUrl(input: String): String {
        val uri = URI(input.trim())
        require(uri.scheme == "https" && uri.host == "animefreak.it.com" &&
            uri.path.matches(Regex("/anime/[a-zA-Z0-9-]+(?:/episode/\\d+)?/?"))) {
            "Paste an AnimeFreak series or episode URL from animefreak.it.com."
        }
        return "https://animefreak.it.com" + uri.path.substringBefore("/episode/").trimEnd('/')
    }

    fun parseSeries(html: String, url: String): Series {
        val base = seriesUrl(url)
        val doc = Jsoup.parse(html, base)
        val episodes = doc.select("a[href]").mapNotNull {
            val link = it.absUrl("href")
            val match = Regex(Regex.escape(base) + "/episode/(\\d+)/?").matchEntire(link)
            match?.groupValues?.get(1)?.toIntOrNull()?.let { n -> Episode(n, "$base/episode/$n") }
        }.distinctBy { it.number }.sortedBy { it.number }
        val title = doc.selectFirst("h1")?.text()?.replace("", "")?.trim().orEmpty()
        require(title.isNotBlank() && episodes.isNotEmpty()) { "No episodes found. AnimeFreak may still be verifying the browser or be unavailable." }
        return Series(title, episodes)
    }

    private suspend fun <T> browser(block: suspend (DriverBase) -> T): T = withContext(Dispatchers.IO) {
        val base = object : DriverBase() {}
        try {
            base.waitForSetup()
            base.driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(45))
            try { block(base) } catch (error: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                throw IllegalStateException("AnimeFreak timed out on ${base.driver.title}. Retry, or disable Headless Mode in Settings to check browser verification.", error)
            }
        } finally { base.killDriver() }
    }

    suspend fun catalogHtml(url: String): String = browser { base ->
        require(URI(url).host == "animefreak.it.com")
        if (URI(url).path == "/search") {
            base.undriver.go("https://animefreak.it.com/home", false)
            withTimeout(90_000) {
                while (base.driver.findElements(By.cssSelector("a[href*='/anime/']")).isEmpty()) delay(1000)
            }
        }
        base.undriver.go(url, false)
        withTimeout(90_000) {
            val selector = if (URI(url).path.startsWith("/anime/")) "a[href*='/episode/']" else "input[name='keyword']"
            while (base.driver.findElements(By.cssSelector(selector)).isEmpty()) {
                currentCoroutineContext().ensureActive()
                delay(1000)
            }
            base.driver.pageSource.orEmpty()
        }
    }

    suspend fun playback(stream: Stream): nobility.downloader.core.scraper.player.HiAnimePlaybackServer = withContext(Dispatchers.IO) {
        fun fetch(url: String, limit: Int) = Jsoup.connect(url).userAgent(stream.userAgent)
            .referrer(stream.referer).ignoreContentType(true).timeout(30_000).maxBodySize(limit).execute().bodyAsBytes()
        var url = stream.url
        var playlist = fetch(url, 1024 * 1024).toString(Charsets.UTF_8)
        if (playlist.contains("#EXT-X-STREAM-INF")) {
            val lines = playlist.lines()
            val variant = lines.indices.firstOrNull { lines[it].startsWith("#EXT-X-STREAM-INF") }
                ?: error("No playable AnimeFreak quality")
            url = URI(url).resolve(lines.drop(variant + 1).first { it.isNotBlank() && !it.startsWith('#') }.trim()).toString()
            playlist = fetch(url, 1024 * 1024).toString(Charsets.UTF_8)
        }
        require(playlist.startsWith("#EXTM3U") && playlist.contains("#EXTINF")) { "AnimeFreak returned no media playlist" }
        require(!playlist.contains("#EXT-X-KEY") && !playlist.contains("#EXT-X-MAP") && !playlist.contains("#EXT-X-BYTERANGE")) {
            "This AnimeFreak stream format is not supported for watching yet. Try Download."
        }
        val segments = playlist.lines().filter { it.isNotBlank() && !it.trimStart().startsWith('#') }
            .map { URI(url).resolve(it.trim()).toString() }
        nobility.downloader.core.scraper.player.HiAnimePlaybackServer(playlist, segments.size) { i ->
            withContext(Dispatchers.IO) { fetch(segments[i], 24 * 1024 * 1024) }
        }
    }

    suspend fun load(input: String): Series = browser { base ->
        val url = seriesUrl(input)
        base.undriver.go(url, false)
        withTimeout(90_000) {
            var found: Series? = null
            while (found == null) {
                currentCoroutineContext().ensureActive()
                found = runCatching { parseSeries(base.driver.pageSource.orEmpty(), url) }.getOrNull()
                if (found == null) delay(1000)
            }
            found
        }
    }

    suspend fun resolve(episode: Episode): Stream = browser { base ->
        val series = seriesUrl(episode.url)
        base.undriver.go(series, false)
        withTimeout(90_000) {
            while (base.driver.findElements(By.cssSelector("a[href*='/episode/']")).isEmpty()) {
                currentCoroutineContext().ensureActive()
                delay(1000)
            }
            // Establish the normal series session before requesting an individual episode.
            base.undriver.go(episode.url, false)

            var embed: String? = null
            while (embed == null) {
                currentCoroutineContext().ensureActive()
                embed = base.driver.findElements(By.cssSelector("#player iframe[src]")).mapNotNull { it.getAttribute("src") }
                    .firstOrNull { runCatching { URI(it).host == "play2.echovideo.ru" }.getOrDefault(false) }
                if (embed == null) delay(1000)
            }
            // Only visit the site's actual player frame, never advertising frames.
            nobility.downloader.utils.FrogLog.message("AnimeFreak: loading EchoVideo player.")
            base.undriver.go(embed, false)
            var inner: String? = null
            while (inner == null) {
                currentCoroutineContext().ensureActive()
                inner = base.driver.findElements(By.id("playerIframe")).firstOrNull()?.getAttribute("src")
                if (inner == null) delay(500)
            }
            require(URI(inner).host == "play2.echovideo.ru") { "AnimeFreak returned an unsupported player." }
            nobility.downloader.utils.FrogLog.message("AnimeFreak: loading stream details.")
            base.undriver.go(inner, false)
            var token: String? = null
            while (token.isNullOrBlank()) {
                currentCoroutineContext().ensureActive()
                token = base.driver.findElements(By.id("mg-player")).firstOrNull()?.getAttribute("data-id")
                if (token.isNullOrBlank()) delay(500)
            }
            val endpoint = URI(inner).resolve("getSources?id=" + java.net.URLEncoder.encode(token, Charsets.UTF_8)).toString()
            val body = Jsoup.connect(endpoint).userAgent(base.userAgent).referrer(inner)
                .ignoreContentType(true).timeout(30_000).maxBodySize(1024 * 1024).execute().body()
            val stream = JsonParser.parseString(body).asJsonObject.get("sources")?.asString
                ?: error("The AnimeFreak player returned no video link. Retry this episode later.")
            require(URI(stream).host.endsWith(".echovideo.to")) { "AnimeFreak returned an unsupported stream host." }
            Stream(stream, inner, base.userAgent)
        }
    }

    fun filename(series: Series, episode: Episode): String =
        EpisodeNaming.downloadFilename("Episode ${episode.number}", episode.url, series.title)
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(180) + ".mp4"

    suspend fun download(stream: Stream, output: File, status: (String) -> Unit) = withContext(Dispatchers.IO) {
        require(!output.exists()) { "File already exists: ${output.name}" }
        val ffmpeg = VideoUtil.loadFfmpegPath()
        require(ffmpeg.isNotBlank()) { "Install FFmpeg from Settings before downloading from AnimeFreak." }
        require(output.parentFile.isDirectory || output.parentFile.mkdirs()) { "Cannot create the download folder." }
        val temp = File.createTempFile(".animefreak-", ".mp4", output.parentFile)
        val log = File.createTempFile(".animefreak-", ".log", output.parentFile)
        var process: Process? = null
        try {
            val help = ProcessBuilder(ffmpeg, "-hide_banner", "-h", "demuxer=hls").redirectErrorStream(true).start()
            val options = help.inputStream.bufferedReader().use { it.readText() }
            help.waitFor()
            val command = mutableListOf(ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "warning", "-y",
                "-rw_timeout", "20000000", "-allowed_extensions", "ALL")
            if ("extension_picky" in options) command.addAll(listOf("-extension_picky", "0"))
            command.addAll(listOf("-user_agent", stream.userAgent, "-referer", stream.referer,
                "-i", stream.url, "-c", "copy", "-movflags", "+faststart", temp.absolutePath))
            process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start()
            while (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                currentCoroutineContext().ensureActive()
                status("Downloading ${output.name}: ${temp.length() / 1024 / 1024} MB")
            }
            currentCoroutineContext().ensureActive()
            require(process.exitValue() == 0 && temp.length() > 0) {
                "Download failed: " + log.readText().takeLast(1000)
            }
            Files.move(temp.toPath(), output.toPath())
        } finally {
            process?.let { if (it.isAlive) { it.destroy(); if (!it.waitFor(2, TimeUnit.SECONDS)) it.destroyForcibly().waitFor() } }
            temp.delete(); log.delete()
        }
    }
}
