package io.github.preetham7cs.justdump.data.local

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "capture_draft")
data class CaptureDraftEntity(
    @PrimaryKey val id: Int = SINGLE_DRAFT_ID,
    val content: String,
    val generation: Long,
    val updatedAtEpochMillis: Long,
) {
    companion object {
        const val SINGLE_DRAFT_ID = 1
    }
}
