package nobility.downloader.core.scraper

import AppInfo
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import nobility.downloader.utils.FrogLog

/** External-provider favorites stay separate from the WCO database IDs. */
object ProviderFavorites {
    val items = mutableStateListOf<WatchCatalog.Show>()
    private var loaded = false
    private var failed = false
    private val file get() = File(AppInfo.databasePath, "my_data/provider-favorites.json")
    fun load() {
        if (loaded) return
        loaded = true
        try {
            if (file.exists()) items.addAll(Gson().fromJson(file.readText(), Array<WatchCatalog.Show>::class.java).toList().distinctBy { FollowSeries.key(it) })
        } catch (e: Exception) { failed = true; FrogLog.error("Could not read provider favorites; saved file preserved.", e) }
    }
    fun contains(show: WatchCatalog.Show) = items.any { FollowSeries.key(it) == FollowSeries.key(show) }
    fun toggle(show: WatchCatalog.Show) {
        load()
        if (failed) { FrogLog.error("Cannot update provider favorites until the saved file is repaired."); return }
        val next = items.toMutableList()
        if (contains(show)) next.removeAll { FollowSeries.key(it) == FollowSeries.key(show) } else next.add(show)
        try {
            file.parentFile.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(Gson().toJson(next))
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            items.clear(); items.addAll(next)
        } catch (e: Exception) { FrogLog.error("Could not save provider favorites.", e) }
    }
}
