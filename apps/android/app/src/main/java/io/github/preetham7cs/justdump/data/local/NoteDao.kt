package io.github.preetham7cs.justdump.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAtEpochMillis DESC")
    fun observeRecentNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observeNote(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM capture_draft WHERE id = 1 LIMIT 1")
    fun observeDraft(): Flow<CaptureDraftEntity?>

    @Query("SELECT * FROM capture_draft WHERE id = 1 LIMIT 1")
    suspend fun getDraft(): CaptureDraftEntity?

    @Insert
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceDraft(draft: CaptureDraftEntity)

    @Transaction
    suspend fun persistDraftIfNewer(draft: CaptureDraftEntity) {
        val storedDraft = getDraft()
        if (storedDraft == null || draft.generation > storedDraft.generation) {
            replaceDraft(draft)
        }
    }

    @Transaction
    suspend fun saveNoteAndAcknowledgeDraft(
        note: NoteEntity,
        savedDraftGeneration: Long,
        savedAtEpochMillis: Long,
    ) {
        insertNote(note)

        val storedDraft = getDraft()
        if (storedDraft == null || storedDraft.generation <= savedDraftGeneration) {
            replaceDraft(
                CaptureDraftEntity(
                    content = "",
                    generation = savedDraftGeneration,
                    updatedAtEpochMillis = savedAtEpochMillis,
                ),
            )
        }
    }
}
