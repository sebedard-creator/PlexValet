package com.seb.sdpr.domain.model

enum class LibrarySyncStage {
    CONNECTING, MOVIES, COLLECTIONS, SAVING, CATALOG, FINISHING
}

/** Counts refer to the current stage and section, rather than the entire refresh. */
data class LibrarySyncProgress(
    val stage: LibrarySyncStage = LibrarySyncStage.CONNECTING,
    val completed: Int = 0,
    val total: Int? = null,
    val sectionIndex: Int = 1,
    val sectionCount: Int = 1
) {
    val fraction: Float?
        get() = total?.takeIf { it > 0 }?.let { (completed.toFloat() / it).coerceIn(0f, 1f) }
}
