package io.github.preetham7cs.justdump.cloud

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.preetham7cs.justdump.JustDumpApplication
import io.github.preetham7cs.justdump.data.NoteSyncStatus
import io.github.preetham7cs.justdump.data.VaultItem
import io.github.preetham7cs.justdump.data.VaultItemKind
import java.io.IOException

/**
 * Drains locally persisted work. It never deletes the local original: success
 * means the API acknowledged the item and a caller-authorised read-back matched.
 */
class VaultSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as JustDumpApplication
        val token = app.authRepository.accessTokenOrNull() ?: return Result.success()
        val pending = app.vaultRepository.pendingItems()
        var retry = false
        pending.forEach { item ->
            when (item.kind) {
                VaultItemKind.TEXT -> Unit // Text has its existing explicit, verified upload action.
                VaultItemKind.IMAGE, VaultItemKind.LINK -> {
                    val result = uploadMediaItem(app, item, token)
                    retry = retry || result
                }
            }
        }
        return if (retry) Result.retry() else Result.success()
    }

    private suspend fun uploadMediaItem(app: JustDumpApplication, item: VaultItem, token: String): Boolean {
        app.vaultRepository.updateSyncState(item.id, NoteSyncStatus.UPLOADING, null, null)
        return try {
            val readBack = app.cloudVaultApi.uploadAndReadBack(item, token)
            if (readBack.clientItemId == item.id && readBack.kind == item.kind.name) {
                app.vaultRepository.updateSyncState(item.id, NoteSyncStatus.SYNCED, null, System.currentTimeMillis())
                false
            } else {
                app.vaultRepository.updateSyncState(
                    item.id,
                    NoteSyncStatus.FAILED,
                    "Cloud read-back did not match this local item.",
                    null,
                )
                false
            }
        } catch (error: CloudRequestException) {
            app.vaultRepository.updateSyncState(
                item.id,
                NoteSyncStatus.FAILED,
                error.safeDetail ?: "Cloud request was rejected (HTTP ${error.statusCode}).",
                null,
            )
            error.statusCode >= 500
        } catch (error: IOException) {
            app.vaultRepository.updateSyncState(
                item.id,
                NoteSyncStatus.FAILED,
                "Upload paused by a connection problem. The local item is still available.",
                null,
            )
            true
        } catch (_: Exception) {
            app.vaultRepository.updateSyncState(
                item.id,
                NoteSyncStatus.FAILED,
                "Upload failed. The local item is still available.",
                null,
            )
            false
        }
    }
}
