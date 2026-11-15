package io.github.preetham7cs.justdump.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import io.github.preetham7cs.justdump.data.local.VaultItemDao
import io.github.preetham7cs.justdump.data.local.VaultItemEntity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class VaultRepository(
    private val context: Context,
    private val vaultItemDao: VaultItemDao,
) {
    fun observeNonTextItems(): Flow<List<VaultItem>> =
        vaultItemDao.observeNonTextItems().map { items -> items.map(VaultItemEntity::toVaultItem) }

    fun observeItem(id: String?): Flow<VaultItem?> =
        if (id == null) flowOf(null) else vaultItemDao.observeItem(id).map { it?.toVaultItem() }

    suspend fun saveImage(source: Uri): VaultItem = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(source)?.takeIf { it.startsWith("image/") }
            ?: throw IllegalArgumentException("Choose an image file.")
        val id = UUID.randomUUID().toString()
        val extension = mimeType.substringAfter('/', "jpg").replace(Regex("[^a-zA-Z0-9]"), "")
        val itemDirectory = File(context.filesDir, "vault-images").apply { mkdirs() }
        val destination = File(itemDirectory, "$id.$extension")
        val temporary = File(itemDirectory, ".$id.uploading")
        try {
            resolver.openInputStream(source)?.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalArgumentException("The selected image could not be opened.")
            if (!temporary.renameTo(destination)) throw IllegalStateException("The selected image could not be stored.")
            val now = System.currentTimeMillis()
            val item = VaultItemEntity(
                id = id,
                kind = VaultItemKind.IMAGE.name,
                textContent = null,
                sourceUrl = null,
                localFilePath = destination.absolutePath,
                mimeType = mimeType,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                syncStatus = NoteSyncStatus.PENDING_UPLOAD.name,
                syncError = null,
                syncedAtEpochMillis = null,
            )
            vaultItemDao.insertLocalItemAndEnqueue(item)
            item.toVaultItem()
        } catch (error: Exception) {
            temporary.delete()
            destination.delete()
            throw error
        }
    }

    suspend fun saveLink(value: String): VaultItem {
        val url = value.trim()
        if (!URLUtil.isHttpUrl(url) && !URLUtil.isHttpsUrl(url)) {
            throw IllegalArgumentException("Enter a public http or https link.")
        }
        val now = System.currentTimeMillis()
        val item = VaultItemEntity(
            id = UUID.randomUUID().toString(),
            kind = VaultItemKind.LINK.name,
            textContent = null,
            sourceUrl = url,
            localFilePath = null,
            mimeType = "text/uri-list",
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            syncStatus = NoteSyncStatus.PENDING_UPLOAD.name,
            syncError = null,
            syncedAtEpochMillis = null,
        )
        vaultItemDao.insertLocalItemAndEnqueue(item)
        return item.toVaultItem()
    }
}

private fun VaultItemEntity.toVaultItem() = VaultItem(
    id = id,
    kind = VaultItemKind.valueOf(kind),
    textContent = textContent,
    sourceUrl = sourceUrl,
    localFilePath = localFilePath,
    mimeType = mimeType,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    syncStatus = NoteSyncStatus.valueOf(syncStatus),
    syncError = syncError,
)
