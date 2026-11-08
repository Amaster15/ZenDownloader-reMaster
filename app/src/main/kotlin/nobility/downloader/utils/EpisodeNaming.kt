package nobility.downloader.utils

/** Source numbering wins over series numbering; unknown/special episodes keep their title. */
object EpisodeNaming {
    data class Numbering(val season: Int, val episode: Int) {
        val code: String get() = "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
    }
    private val compact = Regex("(?i)\\bS(\\d{1,3})[ ._-]*E(\\d{1,4})(?![\\d.]|[ -]*(?:E\\d|[-–]\\s*\\d))\\b")
    private val season = Regex("(?i)\\bseason[ .:_-]*(\\d{1,3})\\b")
    private val episode = Regex("(?i)\\b(?:episode|ep)[ .:_-]*(\\d{1,4})(?![\\d.]|\\s*[-–&]\\s*\\d)\\b")
    private val special = Regex("(?i)\\b(?:special|ova|ona|movie|film)\\b")

    /** Preserve WCO's labeled season metadata even when the episode title omits it. */
    @JvmStatic fun sourceTitle(element: org.jsoup.nodes.Element): String {
        val title = element.selectFirst("span")?.text()?.takeIf { it.isNotBlank() } ?: element.text()
        if (season.containsMatchIn(title) || compact.containsMatchIn(title) || !episode.containsMatchIn(title)) return title
        val group = element.attr("data-season")
        if (group.isBlank()) return title
        val label = element.ownerDocument()?.select("option[value]")?.firstOrNull { it.attr("value") == group }?.text()
        val number = label?.let { season.find(it)?.groupValues?.get(1) } ?: return title
        return "Season $number $title"
    }

    @JvmStatic fun numbering(name: String, slug: String, series: String, movie: Boolean = false): Numbering? {
        if (movie) return null
        for (text in listOf(name, slug)) {
            val matches = compact.findAll(text).toList()
            if (matches.size == 1) return Numbering(matches[0].groupValues[1].toInt(), matches[0].groupValues[2].toInt())
        }
        if (special.containsMatchIn(name)) return null
        // A decimal or multi-episode label must not be silently truncated to an integer.
        if (Regex("(?i)\\b(?:episode|ep)[ .:_-]*\\d+(?:\\.\\d|\\s*[-–&]\\s*\\d)").containsMatchIn(name)) return null
        val episodes = episode.findAll(name).toList().ifEmpty { episode.findAll(slug).toList() }
        if (episodes.size != 1) return null
        val seasons = listOf(name, slug, series).firstNotNullOfOrNull { text ->
            season.find(text)?.groupValues?.get(1)?.toIntOrNull()
        }
        return Numbering(seasons ?: 1, episodes.single().groupValues[1].toInt())
    }

    @JvmStatic fun downloadDirectory(root: java.io.File, series: String, name: String, slug: String,
                                     separateSeasons: Boolean, movie: Boolean): java.io.File {
        val safeSeries = series.fixForFiles().trim().takeUnless { it.isBlank() || it == "." || it == ".." } ?: "NoSeries"
        val seriesFolder = java.io.File(root, safeSeries)
        val number = numbering(name, slug, series, movie)
        return if (separateSeasons && number != null)
            java.io.File(seriesFolder, "Season ${number.season.toString().padStart(2, '0')}")
        else seriesFolder
    }

    @JvmStatic fun downloadFilename(name: String, slug: String, series: String, movie: Boolean = false): String {
        val formatted = filename(name, slug, series, movie)
        val title = series.trim()
        if (title.isEmpty() || numbering(name, slug, series, movie) == null) return formatted
        val code = compact.find(formatted) ?: return formatted
        val prefix = formatted.substring(0, code.range.first).trim().trim('-', ':').trim()
        val seriesWithoutSeason = season.replace(title, "").replace(Regex("\\s+"), " ").trim().trim('-', ':').trim()
        val extraTitle = if (prefix.equals(title, true) || prefix.equals(seriesWithoutSeason, true)) "" else prefix
        val suffix = formatted.substring(code.range.last + 1).trim()
        return buildString {
            append(title).append(" - ").append(code.value)
            if (extraTitle.isNotEmpty()) append(" - ").append(extraTitle)
            if (suffix.isNotEmpty()) append(' ').append(suffix)
        }
    }

    @JvmStatic fun filename(name: String, slug: String, series: String, movie: Boolean = false): String {
        val n = numbering(name, slug, series, movie) ?: return name
        if (compact.containsMatchIn(name)) return compact.replaceFirst(name, n.code)
        val withoutSeason = season.replace(name, "").replace(Regex("\\s+"), " ").trim().trim('-', ':').trim()
        return if (episode.containsMatchIn(withoutSeason)) episode.replaceFirst(withoutSeason, n.code)
        else "${n.code} - $name"
    }
}
