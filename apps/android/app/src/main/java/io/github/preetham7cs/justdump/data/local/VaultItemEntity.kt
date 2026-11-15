package io.github.preetham7cs.justdump.data.local

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * The durable, type-aware representation used by every future capture path.
 *
 * `notes` remains during the transition so a failed app update cannot make
 * existing text disappear. Text saves are mirrored into this table atomically.
 */
@Entity(
    tableName = "vault_items",
    indices = [Index(value = ["createdAtEpochMillis"])],
)
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val textContent: String?,
    val sourceUrl: String?,
    val localFilePath: String?,
    val mimeType: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val syncStatus: String,
    val syncError: String?,
    val syncedAtEpochMillis: Long?,
)

/**
 * A durable instruction to synchronise one logical item. Workers are added in
 * the next checkpoint; persisting the instruction now makes restarts safe.
 */
@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey val itemId: String,
    val operation: String,
    val createdAtEpochMillis: Long,
    val attemptCount: Int,
    val nextAttemptAtEpochMillis: Long?,
    val lastError: String?,
)
