package nobility.downloader.core.scraper

import AppInfo
import com.google.gson.Gson
import java.io.File
import java.net.URI
import java.text.Normalizer
import java.util.Locale

/** Only learns names attached to the same provider URL; never guesses cross-provider matches. */
object AnimeTitleIndex {
    data class Record(val url: String, val title: String, val aliases: List<String>)
    private val gson = Gson()
    private fun file() = File(AppInfo.databasePath, "my_data/watch-cache/animefreak-titles.json")
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun alternateTitle(show: WatchCatalog.Show): String {
        if (show.provider != WatchCatalog.Provider.ANIMEFREAK) return ""
        val title = URI(show.url).path.trimEnd('/').substringAfterLast('/').replace('-', ' ')
        return title.takeUnless { normalize(it) == normalize(show.title) }.orEmpty()
    }

    fun matches(record: Record, query: String): Boolean {
        val words = normalize(query).split(' ').filter { it.isNotBlank() }
        return words.isNotEmpty() && (record.aliases + record.title).any { alias ->
            val name = normalize(alias)
            words.all { name.contains(it) }
        }
    }

    private fun read(): List<Record> = runCatching {
        val file = file()
        if (!file.isFile || file.length() > 4 * 1024 * 1024) emptyList()
        else gson.fromJson(file.readText(), Array<Record>::class.java).toList()
    }.getOrDefault(emptyList())

    @Synchronized fun remember(shows: List<WatchCatalog.Show>) {
        if (shows.isEmpty()) return
        val records = read().associateBy { it.url }.toMutableMap()
        shows.filter { it.provider == WatchCatalog.Provider.ANIMEFREAK }.forEach { show ->
            val old = records.remove(show.url)
            val aliases = (old?.aliases.orEmpty() + listOfNotNull(old?.title) + show.title + alternateTitle(show))
                .filter { it.isNotBlank() }.distinct().takeLast(12)
            records[show.url] = Record(show.url, show.title, aliases)
        }
        val file = file()
        file.parentFile.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(gson.toJson(records.values.toList().takeLast(3000)))
        java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    @Synchronized fun search(query: String): List<WatchCatalog.Show> = read().filter { matches(it, query) }
        .map { WatchCatalog.Show(it.title, it.url, WatchCatalog.Provider.ANIMEFREAK) }
}
