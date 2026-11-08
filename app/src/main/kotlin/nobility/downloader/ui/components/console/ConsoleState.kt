package nobility.downloader.ui.components.console

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import nobility.downloader.Page
import nobility.downloader.core.Core
import java.util.*

class ConsoleState(
    private val errorMode: Boolean
) {

    private val showTime = false
    private val lines = mutableStateListOf<String>()
    val consoleText get() = lines.joinToString("\n")
    var consolePoppedOut by mutableStateOf(false)

    private val sb = StringBuilder()
    var unreadErrors by mutableStateOf(false)

    var lineCount = 0
        private set
    private val progressRows = mutableMapOf<String, Int>()
    private val maxLines = 1000
    private val maxLineChars = 300

    @Synchronized
    fun appendChar(c: Char) {
        if (c == '\r') {
            return
        }
        if (c == '\n') {
            flushLine()
        } else {
            sb.append(c)
        }
        if (errorMode) {
            if (Core.Companion.currentPage != Page.ERROR_CONSOLE && !consolePoppedOut) {
                unreadErrors = true
            }
        }
    }

    private fun flushLine() {

        if (sb.isEmpty()) {
            return
        }

        val rawLine = sb.toString()
        sb.clear()

        val shouldFilter = filters.any { filter ->
            rawLine.contains(filter, ignoreCase = true)
        }

        if (shouldFilter) {
            return
        }

        // Keep each episode's popup timer in its own row, even when workers interleave.
        // A later status from that episode ends the timer, so a new attempt gets a new row.
        val countdown = countdownRegex.matchEntire(rawLine) ?: retryRegex.matchEntire(rawLine)
        if (countdown != null && rawLine.length <= maxLineChars) {
            val episodePrefix = countdown.groupValues[1]
            val previous = lines.indexOfLast { it.startsWith(episodePrefix) }
            if (previous >= 0 && (countdownRegex.matches(lines[previous]) || retryRegex.matches(lines[previous]))) {
                lines[previous] = rawLine
                return
            }
        }

        val prefix = if (!showTime || errorMode) "" else "[${time()}] "
        rawLine.chunked(maxLineChars).forEachIndexed { index, chunk ->
            val line = if (index == 0) "$prefix$chunk" else "→ $chunk"
            lines += line
            lineCount++
            if (lineCount > maxLines) {
                removeFirstLine()
                lineCount--
            }
        }
    }

    private fun removeFirstLine() {
        lines.removeFirst()
        progressRows.replaceAll { _, index -> index - 1 }
        progressRows.entries.removeAll { it.value < 0 }
    }

    @Synchronized
    fun updateDownloadProgress(key: String, text: String) {
        val line = text.take(maxLineChars)
        val previous = progressRows[key]
        if (previous != null) lines[previous] = line else {
            lines += line
            progressRows[key] = lines.lastIndex
            if (lines.size > maxLines) removeFirstLine()
            lineCount = lines.size
        }
    }

    @Synchronized
    fun clear() {
        lines.clear()
        progressRows.clear()
        lineCount = 0
        sb.clear()
        unreadErrors = false
    }

    private fun time(): String {
        val now = Calendar.getInstance()
        val h = now[Calendar.HOUR_OF_DAY].toString().padStart(2, '0')
        val m = now[Calendar.MINUTE].toString().padStart(2, '0')
        val s = now[Calendar.SECOND].toString().padStart(2, '0')
        return "$h:$m:$s"
    }

    companion object {
        private val retryRegex = Regex("^(.*)No video link found\\. Page retry \\d+ of \\d+ in \\d+ seconds\\.$")
        private val countdownRegex = Regex("^(.*)Player popup: (?:\\d+ seconds remaining before Close\\.|waiting for Close \\(\\d+ seconds elapsed\\)\\.)$")
        private val filters = listOf<String>(
            "Hint: use closeThreadResources() to avoid finalizing recycled transactions"
        )
    }
}
