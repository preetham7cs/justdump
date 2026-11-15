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

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNote(id: String): NoteEntity?

    @Query("SELECT * FROM capture_draft WHERE id = 1 LIMIT 1")
    fun observeDraft(): Flow<CaptureDraftEntity?>

    @Query("SELECT * FROM capture_draft WHERE id = 1 LIMIT 1")
    suspend fun getDraft(): CaptureDraftEntity?

    @Insert
    suspend fun insertNote(note: NoteEntity)

    @Insert
    suspend fun insertVaultItem(item: VaultItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueSync(operation: SyncOutboxEntity)

    @Query(
        "UPDATE notes SET syncStatus = :status, syncError = :error, syncedAtEpochMillis = :syncedAtEpochMillis " +
            "WHERE id = :id",
    )
    suspend fun updateSyncState(
        id: String,
        status: String,
        error: String?,
        syncedAtEpochMillis: Long?,
    )

    @Query(
        "UPDATE vault_items SET syncStatus = :status, syncError = :error, syncedAtEpochMillis = :syncedAtEpochMillis " +
            "WHERE id = :id",
    )
    suspend fun updateVaultSyncState(
        id: String,
        status: String,
        error: String?,
        syncedAtEpochMillis: Long?,
    )

    @Query("DELETE FROM sync_outbox WHERE itemId = :itemId")
    suspend fun removeSyncOperation(itemId: String)

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
        insertVaultItem(
            VaultItemEntity(
                id = note.id,
                kind = "TEXT",
                textContent = note.content,
                sourceUrl = null,
                localFilePath = null,
                mimeType = "text/plain",
                createdAtEpochMillis = note.createdAtEpochMillis,
                updatedAtEpochMillis = note.updatedAtEpochMillis,
                syncStatus = note.syncStatus,
                syncError = note.syncError,
                syncedAtEpochMillis = note.syncedAtEpochMillis,
            ),
        )
        enqueueSync(
            SyncOutboxEntity(
                itemId = note.id,
                operation = "UPSERT",
                createdAtEpochMillis = note.updatedAtEpochMillis,
                attemptCount = 0,
                nextAttemptAtEpochMillis = null,
                lastError = null,
            ),
        )

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

    @Transaction
    suspend fun updateSyncStateAndOutbox(
        id: String,
        status: String,
        error: String?,
        syncedAtEpochMillis: Long?,
    ) {
        updateSyncState(id, status, error, syncedAtEpochMillis)
        updateVaultSyncState(id, status, error, syncedAtEpochMillis)
        if (status == "SYNCED") removeSyncOperation(id)
    }
}
