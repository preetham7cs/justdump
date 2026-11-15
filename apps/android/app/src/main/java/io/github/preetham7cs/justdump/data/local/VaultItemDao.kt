package io.github.preetham7cs.justdump.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultItemDao {
    @Query("SELECT * FROM vault_items WHERE kind != 'TEXT' ORDER BY createdAtEpochMillis DESC")
    fun observeNonTextItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    fun observeItem(id: String): Flow<VaultItemEntity?>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getItem(id: String): VaultItemEntity?

    @Query(
        "SELECT vault_items.* FROM vault_items " +
            "INNER JOIN sync_outbox ON sync_outbox.itemId = vault_items.id " +
            "ORDER BY sync_outbox.createdAtEpochMillis ASC LIMIT :limit",
    )
    suspend fun pendingItems(limit: Int): List<VaultItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: VaultItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueSync(operation: SyncOutboxEntity)

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

    @Query(
        "UPDATE notes SET syncStatus = :status, syncError = :error, syncedAtEpochMillis = :syncedAtEpochMillis " +
            "WHERE id = :id",
    )
    suspend fun updateTextNoteSyncState(
        id: String,
        status: String,
        error: String?,
        syncedAtEpochMillis: Long?,
    )

    @Query("DELETE FROM sync_outbox WHERE itemId = :itemId")
    suspend fun removeSyncOperation(itemId: String)

    @Transaction
    suspend fun updateSyncStateAndOutbox(
        id: String,
        status: String,
        error: String?,
        syncedAtEpochMillis: Long?,
    ) {
        updateVaultSyncState(id, status, error, syncedAtEpochMillis)
        updateTextNoteSyncState(id, status, error, syncedAtEpochMillis)
        if (status == "SYNCED") removeSyncOperation(id)
    }

    @Transaction
    suspend fun insertLocalItemAndEnqueue(item: VaultItemEntity) {
        insertItem(item)
        enqueueSync(
            SyncOutboxEntity(
                itemId = item.id,
                operation = "UPSERT",
                createdAtEpochMillis = item.updatedAtEpochMillis,
                attemptCount = 0,
                nextAttemptAtEpochMillis = null,
                lastError = null,
            ),
        )
    }
}
