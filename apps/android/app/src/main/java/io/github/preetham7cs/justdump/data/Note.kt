package io.github.preetham7cs.justdump.data

data class Note(
    val id: String,
    val content: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class CaptureDraft(
    val content: String,
    val generation: Long,
    val updatedAtEpochMillis: Long,
)
