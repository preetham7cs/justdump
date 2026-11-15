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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: VaultItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueSync(operation: SyncOutboxEntity)

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
