package nobility.downloader.core.scraper.video_download

import kotlinx.coroutines.delay

object PageLinkRetry {
    suspend fun waitBeforeRetry(seconds: Int, running: () -> Boolean): Boolean {
        repeat(seconds) {
            if (!running()) return false
            delay(1000L)
        }
        return running()
    }

    suspend fun <T> run(
        retries: Int,
        delaySeconds: Int,
        running: () -> Boolean,
        attempt: suspend () -> T,
        shouldRetry: (T) -> Boolean,
        onRetry: (Int, Int, Int) -> Unit,
        pause: suspend (Long) -> Unit = { delay(it) }
    ): T? {
        for (index in 0..retries) {
            if (!running()) return null
            val result = attempt()
            if (!shouldRetry(result) || index == retries) return result
            onRetry(index + 1, retries, delaySeconds)
            // Check Stop each second while retaining coroutine cancellation support.
            repeat(delaySeconds) {
                if (!running()) return null
                pause(1000L)
            }
        }
        return null
    }
}
