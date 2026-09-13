package io.github.preetham7cs.justdump.data.local

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val content: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
