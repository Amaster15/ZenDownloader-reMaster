package nobility.downloader.core.scraper

import com.google.gson.JsonParser
import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.EpisodeNaming
import org.jsoup.Jsoup
import java.io.File
import java.net.URI
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object KimCartoon {
    const val HOST = "kimcartoon.com.co"
    private const val AGENT = "Mozilla/5.0"
    data class Resolved(val media: AnimeFreak.Stream, val subtitle: String?, val height: Int)
    class MissingPlayer(message: String) : IllegalStateException(message)

    fun validate(url: String): String {
        val uri = URI(url.trim())
        require(uri.scheme == "https" && uri.host == HOST && uri.userInfo == null &&
            (uri.path.matches(Regex("/anime/[^/]+/?")) || uri.path.matches(Regex("/[^/]+-episode-[0-9]+(?:\\.[0-9]+)?/?")))) {
            "Paste a KimCartoon series or episode URL from $HOST."
        }
        return URI("https", HOST, uri.path.trimEnd('/') + "/", null).toString()
    }
    suspend fun read(url: String): String = withContext(Dispatchers.IO) {
        require(URI(url).host == HOST && URI(url).scheme == "https")
        Jsoup.connect(url).userAgent(AGENT).timeout(30_000).maxBodySize(4 * 1024 * 1024).get().outerHtml()
    }
    suspend fun show(input: String): WatchCatalog.Show {
        var url = validate(input)
        var doc = Jsoup.parse(read(url), url)
        if (!URI(url).path.startsWith("/anime/")) {
            url = doc.select("a[href]").firstOrNull { it.text().trim() == "All Episodes" }?.absUrl("href")
                ?: error("KimCartoon did not provide a series link")
            url = validate(url)
            require(URI(url).path.startsWith("/anime/"))
            doc = Jsoup.parse(read(url), url)
        }
        val title = doc.selectFirst("h1.entry-title")?.text().orEmpty()
        require(title.isNotBlank()) { "KimCartoon returned no series title" }
        return WatchCatalog.Show(title, url, WatchCatalog.Provider.KIMCARTOON)
    }
    fun parseSearch(html: String, base: String): List<WatchCatalog.Show> = Jsoup.parse(html, base)
        .select(".postbody .listupd .bsx > a[href]").mapNotNull { a ->
            val url = runCatching { validate(a.absUrl("href")) }.getOrNull() ?: return@mapNotNull null
            if (!URI(url).path.startsWith("/anime/")) return@mapNotNull null
            val title = a.selectFirst("h2")?.text()?.ifBlank { null } ?: a.attr("title")
            if (title.isBlank()) null else WatchCatalog.Show(title, url, WatchCatalog.Provider.KIMCARTOON)
        }.distinctBy { it.url }
    fun parseEpisodes(html: String, base: String): List<WatchCatalog.Entry> = Jsoup.parse(html, base)
        .select(".episodes-ul a.ep-item[href][data-number]").flatMap { a ->
            val url = runCatching { validate(a.absUrl("href")) }.getOrNull() ?: return@flatMap emptyList()
            val number = a.attr("data-number")
            if (!number.matches(Regex("[0-9]+(?:\\.[0-9]+)?")) || URI(url).path.startsWith("/anime/")) return@flatMap emptyList()
            // The site advertises both buttons; actual availability is checked when resolving.
            listOf("sub", "dub").map { WatchCatalog.Entry("Episode $number (${it.uppercase()})", url, it) }
        }.distinctBy { it.url to it.language }

    fun playerUrl(html: String, base: String, language: String): String {
        require(language in listOf("sub", "dub"))
        val doc = Jsoup.parse(html, base)
        val button = doc.select("a[data-hash]").firstOrNull { it.text().trim().equals("GogoAnime $language", true) }
            ?: throw MissingPlayer("KimCartoon does not list a $language player for this episode")
        val frame = Jsoup.parse(String(Base64.getDecoder().decode(button.attr("data-hash")), Charsets.UTF_8))
            .selectFirst("iframe[src]")?.attr("src") ?: error("KimCartoon player markup changed")
        val uri = URI(frame)
        require(uri.scheme == "https" && uri.host == "gogoanime.com.by" && uri.path == "/streaming.php") { "Unsupported KimCartoon player" }
        return frame
    }
    fun decodeSource(encoded: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("i?LMTAx0Q6,:}50U".toByteArray().copyOf(32), "AES"),
            IvParameterSpec("W0;27ToaUpl_P%'c".toByteArray()))
        return JsonParser.parseString(String(cipher.doFinal(Base64.getUrlDecoder().decode(encoded)), Charsets.UTF_8))
            .asJsonObject["file"].asString
    }
    fun selectQuality(playlist: String, base: String, requested: Int): Pair<String, Int> {
        val lines = playlist.lines()
        val variants = lines.indices.filter { lines[it].startsWith("#EXT-X-STREAM-INF:") }.mapNotNull { i ->
            val height = Regex("RESOLUTION=\\d+x(\\d+)").find(lines[i])?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val path = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith('#') } ?: return@mapNotNull null
            URI(base).resolve(path.trim()).toString() to height
        }
        require(variants.isNotEmpty()) { "KimCartoon did not return video qualities" }
        return variants.filter { it.second <= requested }.maxByOrNull { it.second } ?: variants.minBy { it.second }
    }
    private fun request(url: String, referer: String) = Jsoup.connect(url).userAgent(AGENT).referrer(referer)
        .ignoreContentType(true).timeout(30_000).maxBodySize(4 * 1024 * 1024)
    private fun mediaUrl(url: String): String {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null) { "Invalid KimCartoon media URL" }
        return url
    }
    suspend fun resolveOnce(entry: WatchCatalog.Entry, quality: Quality): Resolved = withContext(Dispatchers.IO) {
        val page = validate(entry.url)
        val wrapper = playerUrl(read(page), page, entry.language)
        val wrapperDoc = Jsoup.parse(request(wrapper, page).execute().body(), wrapper)
        val embed = wrapperDoc.select("iframe[src]").map { it.absUrl("src") }.firstOrNull {
            val uri = URI(it); uri.scheme == "https" && uri.host == "megaplay.buzz" && uri.path.startsWith("/stream/")
        } ?: throw MissingPlayer("KimCartoon's ${entry.language} server returned no player")
        val doc = Jsoup.parse(request(embed, wrapper).execute().body(), embed)
        val id = doc.selectFirst("#megaplay-player[data-id]")?.attr("data-id")
            ?: throw MissingPlayer("KimCartoon's ${entry.language} file is unavailable or removed. ${doc.body().text().take(180)}")
        require(id.matches(Regex("\\d+"))) { "KimCartoon player identifier changed" }
        val json = JsonParser.parseString(request("https://megaplay.buzz/stream/getSourcesNew?id=$id", embed).execute().body()).asJsonObject
        val encoded = json["enc"]?.asString ?: throw MissingPlayer("KimCartoon's ${entry.language} server returned no video link")
        val master = mediaUrl(decodeSource(encoded))
        val (video, height) = selectQuality(request(master, "https://megaplay.buzz/").execute().body(), master,
            if (quality == Quality.LOW) 360 else quality.resolution)
        val subtitle = json.getAsJsonArray("tracks")?.firstOrNull {
            val track = it.asJsonObject
            track["kind"]?.asString == "captions" && track["label"]?.asString?.startsWith("English", true) == true
        }?.asJsonObject?.get("file")?.asString?.let { mediaUrl(it) }
        Resolved(AnimeFreak.Stream(mediaUrl(video), "https://megaplay.buzz/", AGENT), subtitle, height)
    }
    suspend fun resolve(entry: WatchCatalog.Entry, quality: Quality, status: suspend (String) -> Unit): Resolved {
        val retries = Defaults.PAGE_LINK_RETRIES.int().coerceIn(0, 100)
        for (attempt in 0..retries) {
            try { return resolveOnce(entry, quality) }
            catch (e: CancellationException) { throw e }
            catch (e: MissingPlayer) { throw e }
            catch (e: Exception) {
                if (attempt == retries) throw e
                for (left in Defaults.PAGE_LINK_RETRY_DELAY_SECONDS.int().coerceIn(1, 100) downTo 1) {
                    status("KimCartoon: retry ${attempt + 1}/$retries in $left seconds (${e.message?.take(100)}).")
                    delay(1000)
                }
            }
        }
        error("Unable to resolve KimCartoon")
    }
    suspend fun subtitleFile(stream: Resolved): File? = withContext(Dispatchers.IO) {
        val url = stream.subtitle ?: return@withContext null
        val text = request(url, stream.media.referer).execute().body()
        require(text.trimStart('\uFEFF', ' ', '\n', '\r').startsWith("WEBVTT")) { "The subtitle server returned invalid captions" }
        File.createTempFile("zen-kim-captions-", ".vtt").apply { writeText(text) }
    }
    fun filename(show: WatchCatalog.Show, entry: WatchCatalog.Entry): String =
        EpisodeNaming.downloadFilename(entry.title.removeSuffix(" (SUB)").removeSuffix(" (DUB)"), entry.url, show.title)
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(170) + " - ${entry.language.uppercase()}.mp4"
    suspend fun download(show: WatchCatalog.Show, entry: WatchCatalog.Entry, quality: Quality, folder: File, includeSubtitles: Boolean, status: suspend (String) -> Unit): File {
        require(folder.path.isNotBlank()) { "Choose a download folder in Settings" }
        val output = File(folder, filename(show, entry))
        require(!output.exists()) { "File already exists: ${output.name}" }
        status("Resolving ${entry.title}…")
        val stream = resolve(entry, quality, status)
        status("Downloading ${stream.height}p${if (includeSubtitles && stream.subtitle != null) " with English subtitles" else ""}…")
        val subtitle = if (includeSubtitles) subtitleFile(stream) else null
        try {
            AnimeFreak.downloadWithSubtitle(stream.media, output, subtitle) { message ->
                // FFmpeg polls from IO; callers receive serialized updates on their coroutine.
                kotlinx.coroutines.runBlocking { status(message) }
            }
            return output
        } finally { subtitle?.delete() }
    }
}
