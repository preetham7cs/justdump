package io.github.preetham7cs.justdump.data

import kotlinx.coroutines.flow.Flow

interface NotesDataSource {
    fun observeRecentNotes(): Flow<List<Note>>

    fun observeNote(id: String?): Flow<Note?>

    fun observeDraft(): Flow<CaptureDraft>

    suspend fun persistDraftIfNewer(draft: CaptureDraft)

    suspend fun saveNoteAndAcknowledgeDraft(note: Note, savedDraftGeneration: Long)
}
