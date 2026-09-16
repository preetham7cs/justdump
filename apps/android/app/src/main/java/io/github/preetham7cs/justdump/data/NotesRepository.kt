package io.github.preetham7cs.justdump.data

import io.github.preetham7cs.justdump.data.local.CaptureDraftEntity
import io.github.preetham7cs.justdump.data.local.NoteDao
import io.github.preetham7cs.justdump.data.local.NoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotesRepository(
    private val noteDao: NoteDao,
) : NotesDataSource {
    override fun observeRecentNotes(): Flow<List<Note>> =
        noteDao.observeRecentNotes().map { notes -> notes.map(NoteEntity::toNote) }

    override fun observeNote(id: String?): Flow<Note?> =
        if (id == null) {
            kotlinx.coroutines.flow.flowOf(null)
        } else {
            noteDao.observeNote(id).map { it?.toNote() }
        }

    override suspend fun getNote(id: String): Note? = noteDao.getNote(id)?.toNote()

    override fun observeDraft(): Flow<CaptureDraft> =
        noteDao.observeDraft().map { draft ->
            draft?.toDraft() ?: CaptureDraft(content = "", generation = 0, updatedAtEpochMillis = 0)
        }

    override suspend fun persistDraftIfNewer(draft: CaptureDraft) {
        noteDao.persistDraftIfNewer(draft.toEntity())
    }

    override suspend fun saveNoteAndAcknowledgeDraft(note: Note, savedDraftGeneration: Long) {
        noteDao.saveNoteAndAcknowledgeDraft(
            note = note.toEntity(),
            savedDraftGeneration = savedDraftGeneration,
            savedAtEpochMillis = note.updatedAtEpochMillis,
        )
    }

    override suspend fun updateSyncState(
        id: String,
        status: NoteSyncStatus,
        error: String?,
        syncedAtEpochMillis: Long?,
    ) {
        noteDao.updateSyncState(id, status.name, error, syncedAtEpochMillis)
    }
}

private fun NoteEntity.toNote() =
    Note(
        id = id,
        content = content,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        syncStatus = NoteSyncStatus.valueOf(syncStatus),
        syncError = syncError,
        syncedAtEpochMillis = syncedAtEpochMillis,
    )

private fun Note.toEntity() =
    NoteEntity(
        id = id,
        content = content,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        syncStatus = syncStatus.name,
        syncError = syncError,
        syncedAtEpochMillis = syncedAtEpochMillis,
    )

private fun CaptureDraftEntity.toDraft() =
    CaptureDraft(
        content = content,
        generation = generation,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

private fun CaptureDraft.toEntity() =
    CaptureDraftEntity(
        content = content,
        generation = generation,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
