package io.github.preetham7cs.justdump.cloud

import io.github.preetham7cs.justdump.auth.SupabaseAuthRepository
import io.github.preetham7cs.justdump.data.NoteSyncStatus
import io.github.preetham7cs.justdump.data.NotesDataSource

class NoteSyncCoordinator(
    private val notes: NotesDataSource,
    private val auth: SupabaseAuthRepository,
    private val api: CloudNotesApi,
) {
    suspend fun uploadAndVerify(noteId: String): SyncAttemptResult {
        val note = notes.getNote(noteId) ?: return SyncAttemptResult.Failure("This local note is unavailable.")
        val token = auth.accessTokenOrNull()
            ?: return SyncAttemptResult.Failure("Sign in before uploading notes.")

        notes.updateSyncState(noteId, NoteSyncStatus.UPLOADING, error = null, syncedAtEpochMillis = null)
        return try {
            val uploaded = api.upload(note, token)
            val readBack = api.readBack(note.id, token)
            if (uploaded.matches(note) && readBack.matches(note)) {
                notes.updateSyncState(
                    noteId,
                    NoteSyncStatus.SYNCED,
                    error = null,
                    syncedAtEpochMillis = System.currentTimeMillis(),
                )
                SyncAttemptResult.Success
            } else {
                notes.updateSyncState(
                    noteId,
                    NoteSyncStatus.FAILED,
                    error = "Cloud read-back did not match this local note.",
                    syncedAtEpochMillis = null,
                )
                SyncAttemptResult.Failure("Cloud read-back did not match this local note.")
            }
        } catch (error: CloudRequestException) {
            val message = "Cloud request was rejected (HTTP ${error.statusCode}). The local note is still available."
            notes.updateSyncState(
                noteId,
                NoteSyncStatus.FAILED,
                error = message,
                syncedAtEpochMillis = null,
            )
            SyncAttemptResult.Failure(message)
        } catch (_: Exception) {
            notes.updateSyncState(
                noteId,
                NoteSyncStatus.FAILED,
                error = "Upload failed. The local note is still available; try again when ready.",
                syncedAtEpochMillis = null,
            )
            SyncAttemptResult.Failure("Upload failed. The local note is still available; try again when ready.")
        }
    }
}

private fun CloudNotePayload.matches(note: io.github.preetham7cs.justdump.data.Note): Boolean =
    clientNoteId == note.id &&
        content == note.content

sealed interface SyncAttemptResult {
    data object Success : SyncAttemptResult

    data class Failure(val message: String) : SyncAttemptResult
}
