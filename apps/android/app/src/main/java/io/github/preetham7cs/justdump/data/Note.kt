package io.github.preetham7cs.justdump.data

data class Note(
    val id: String,
    val content: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val syncStatus: NoteSyncStatus = NoteSyncStatus.PENDING_UPLOAD,
    val syncError: String? = null,
    val syncedAtEpochMillis: Long? = null,
)

enum class NoteSyncStatus {
    PENDING_UPLOAD,
    UPLOADING,
    SYNCED,
    FAILED,
}

data class CaptureDraft(
    val content: String,
    val generation: Long,
    val updatedAtEpochMillis: Long,
)
