package nobility.downloader.core.maintenance

import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Download
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.EpisodeNaming
import java.io.File
import java.nio.file.Files

object DownloadRepair {
    data class Move(val record: Download, val source: File, val target: File,
                    val size: Long, val modified: Long, val companions: List<Pair<File, File>>) {
        val conflict get() = (listOf(source to target) + companions).any { it.second.exists() }
    }
    fun preview(): List<Move> {
        val root = File(Defaults.SAVE_FOLDER.string()).canonicalFile
        return Core.child.downloadList.toList().mapNotNull { record ->
            val source = File(record.downloadPath).canonicalFile
            if (!source.isFile || source.parentFile != root || record.downloading || record.queued) return@mapNotNull null
            val series = if (record.slug.startsWith("https://")) record.seriesSlug
                else BoxHelper.seriesForSlug(record.seriesSlug)?.name ?: record.seriesName
            val directory = EpisodeNaming.downloadDirectory(root, series, source.name, record.slug,
                Defaults.SEPARATE_SEASONS.boolean(), record.isMovie)
            val target = File(directory, source.name)
            val companions = listOf("srt", "vtt", "ass").map { ext -> File(root, source.nameWithoutExtension + "." + ext) }
                .filter { it.isFile }.map { it to File(directory, it.name) }
            Move(record, source, target, source.length(), source.lastModified(), companions)
        }.distinctBy { it.source }
    }
    fun apply(moves: List<Move>): String {
        check(!Core.child.hasActiveDownloads && !VideoValidation.isBusy) { "Wait for downloads and validation to finish first." }
        var moved = 0; val problems = mutableListOf<String>()
        for (move in moves) {
            val completed = mutableListOf<Pair<File, File>>()
            try {
                check(!move.conflict) { "Destination already exists" }
                check(move.source.isFile && move.source.length() == move.size && move.source.lastModified() == move.modified) { "File changed since preview" }
                check(move.target.parentFile.isDirectory || move.target.parentFile.mkdirs()) { "Cannot create destination" }
                val validation = VideoValidation.result(move.source)
                for ((source, target) in listOf(move.source to move.target) + move.companions) {
                    Files.move(source.toPath(), target.toPath()) // Never replace another file.
                    completed += source to target
                }
                val oldPath = move.record.downloadPath
                try {
                    move.record.downloadPath = move.target.absolutePath
                    BoxHelper.shared.downloadBox.put(move.record)
                } catch (e: Exception) { move.record.downloadPath = oldPath; throw e }
                moved++
                if (validation != null) runCatching { VideoValidation.remember(move.target, validation.valid, validation.detail) }
                runCatching { move.record.updateProgress() }
            } catch (e: Exception) {
                for ((source, target) in completed.asReversed()) runCatching { Files.move(target.toPath(), source.toPath()) }
                problems += "${move.source.name}: ${e.message}"
            }
        }
        return "Moved $moved episode(s). Skipped ${problems.size}." + if (problems.isEmpty()) "" else "\n" + problems.joinToString("\n")
    }
}
