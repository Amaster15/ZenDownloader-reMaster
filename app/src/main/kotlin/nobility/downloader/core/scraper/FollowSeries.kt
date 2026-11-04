package nobility.downloader.core.scraper

import AppInfo
import androidx.compose.runtime.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import nobility.downloader.core.Core
import nobility.downloader.utils.FrogLog
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object FollowSeries {
    data class Follow(
        val show: WatchCatalog.Show,
        val known: Set<String> = emptySet(),
        val unread: List<WatchCatalog.Entry> = emptyList(),
        val initialized: Boolean = false,
        val checkedAt: Long = 0,
        val error: String = ""
    )
    val items = mutableStateListOf<Follow>()
    var checking by mutableStateOf(false)
        private set
    val unreadCount get() = items.sumOf { it.unread.size }
    private val gson = Gson()
    private val file get() = File(AppInfo.databasePath, "my_data/follow-series.json")
    private var loaded = false
    private var loadFailed = false

    @JvmStatic fun key(show: WatchCatalog.Show): String = show.provider.name + ":" + URI(show.url).path.trimEnd('/')
    @JvmStatic fun episodeKey(entry: WatchCatalog.Entry) = URI(entry.url).path.trimEnd('/') + ":" + entry.language
    @JvmStatic fun refreshed(follow: Follow, entries: List<WatchCatalog.Entry>, now: Long): Follow {
        val unique = entries.distinctBy { episodeKey(it) }
        require(unique.isNotEmpty()) { "No episode list returned; keeping the previous list." }
        val added = if (follow.initialized) unique.filter { episodeKey(it) !in follow.known } else emptyList()
        return follow.copy(known = follow.known + unique.map { episodeKey(it) },
            unread = (follow.unread + added).distinctBy { episodeKey(it) }, initialized = true, checkedAt = now, error = "")
    }
    fun load() {
        if (loaded) return
        loaded = true
        try {
            if (file.exists()) items.addAll(gson.fromJson(file.readText(), Array<Follow>::class.java).toList().distinctBy { key(it.show) })
        } catch (e: Exception) {
            loadFailed = true
            FrogLog.error("Could not read followed series. The saved file has been preserved.", e)
        }
    }
    private fun save() {
        check(!loadFailed) { "The saved follow list could not be read; it has not been overwritten." }
        file.parentFile.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(gson.toJson(items.toList()))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    fun isFollowing(show: WatchCatalog.Show) = items.any { key(it.show) == key(show) }
    fun toggle(show: WatchCatalog.Show, baseline: List<WatchCatalog.Entry> = emptyList()) {
        load()
        if (loadFailed) { FrogLog.error("Unable to change followed series: saved list needs repair."); return }
        val existing = items.indexOfFirst { key(it.show) == key(show) }
        if (existing >= 0) items.removeAt(existing)
        else items.add(if (baseline.isEmpty()) Follow(show) else refreshed(Follow(show), baseline, System.currentTimeMillis()))
        try { save() } catch (e: Exception) { FrogLog.error("Could not save followed series.", e) }
        if (existing < 0 && baseline.isEmpty()) checkAll()
    }
    fun markSeen(show: WatchCatalog.Show) {
        val index = items.indexOfFirst { key(it.show) == key(show) }
        if (index >= 0) {
            items[index] = items[index].copy(unread = emptyList())
            try { save() } catch (e: Exception) { FrogLog.error("Could not save followed series.", e) }
        }
    }
    fun checkAll() {
        if (checking || loadFailed) return
        checking = true
        Core.taskScope.launch(Dispatchers.Main) {
            try {
                // New follows added during a check are initialized in this same pass.
                val processed = mutableSetOf<String>()
                while (true) {
                    val next = items.firstOrNull { key(it.show) !in processed } ?: break
                    processed.add(key(next.show))
                    try {
                        val result = withTimeout(120_000) { WatchCatalog.episodes(next.show) }
                        val index = items.indexOfFirst { key(it.show) == key(next.show) }
                        if (index >= 0) items[index] = refreshed(items[index], result.entries, result.fetchedAt)
                    } catch (e: CancellationException) {
                        if (e !is TimeoutCancellationException) throw e
                        val index = items.indexOfFirst { key(it.show) == key(next.show) }
                        if (index >= 0) items[index] = items[index].copy(error = "Check timed out. Try again later.")
                    } catch (e: Exception) {
                        val index = items.indexOfFirst { key(it.show) == key(next.show) }
                        if (index >= 0) items[index] = items[index].copy(error = e.message ?: "Check failed")
                    }
                    save()
                }
                if (unreadCount > 0) FrogLog.message("Follow Series: $unreadCount new episode(s) available. Open Follow Series to review.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { FrogLog.error("Could not save the follow check.", e) }
            finally { checking = false }
        }
    }
}
