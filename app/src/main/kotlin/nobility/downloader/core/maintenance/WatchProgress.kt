package nobility.downloader.core.maintenance

import AppInfo
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object WatchProgress {
    data class Position(val seconds: Double = 0.0, val duration: Double = 0.0, val watched: Boolean = false)
    private data class Record(val key: String, val position: Position)
    private val gson = Gson()
    private val entries = mutableStateMapOf<String, Position>()
    private val file get() = File(AppInfo.databasePath, "my_data/watch-progress.json")
    private var loaded = false
    @Synchronized private fun load() {
        if (loaded) return
        loaded = true
        runCatching { gson.fromJson(file.readText(), Array<Record>::class.java).forEach { entries[it.key] = it.position } }
    }
    @JvmStatic @Synchronized fun get(key: String): Position { load(); return entries[key] ?: Position() }
    @JvmStatic @Synchronized fun update(key: String, seconds: Double, duration: Double) {
        load()
        if (!seconds.isFinite() || seconds < 0 || !duration.isFinite() || duration < 0) return
        val old = entries[key] ?: Position()
        val total = duration.takeIf { it > 0 } ?: old.duration
        if (total > 0 && seconds > total + 5) return
        val watched = old.watched || (total > 0 && seconds >= total * 0.95)
        if (kotlin.math.abs(seconds - old.seconds) < 5 && watched == old.watched) return
        entries[key] = Position(seconds, total, watched)
        save()
    }
    @Synchronized fun reset(key: String) { load(); entries.remove(key); save() }
    @Synchronized fun mark(key: String, watched: Boolean) { load(); entries[key] = get(key).copy(watched = watched); save() }
    private fun save() {
        file.parentFile.mkdirs(); val temp = File(file.path + ".tmp")
        temp.writeText(gson.toJson(entries.map { Record(it.key, it.value) }))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
