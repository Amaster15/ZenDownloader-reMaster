package nobility.downloader.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Invalidates database views after a persisted library change. */
object LibraryChanges {
    val revision = MutableStateFlow(0L)
    fun refresh() { revision.update { it + 1 } }
}
