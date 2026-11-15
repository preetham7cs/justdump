package io.github.preetham7cs.justdump.data

enum class VaultItemKind { TEXT, IMAGE, LINK }

data class VaultItem(
    val id: String,
    val kind: VaultItemKind,
    val textContent: String?,
    val sourceUrl: String?,
    val localFilePath: String?,
    val mimeType: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val syncStatus: NoteSyncStatus,
    val syncError: String? = null,
)
