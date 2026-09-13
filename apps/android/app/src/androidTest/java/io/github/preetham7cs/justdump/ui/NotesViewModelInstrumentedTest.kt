package io.github.preetham7cs.justdump.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.preetham7cs.justdump.data.CaptureDraft
import io.github.preetham7cs.justdump.data.Note
import io.github.preetham7cs.justdump.data.NotesDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesViewModelInstrumentedTest {
    @Test
    fun repeatedSaveTapsStartOnlyOneSave() = runBlocking {
        val repository = BlockingRepository()
        val viewModel = NotesViewModel(repository, SavedStateHandle())
        awaitCondition { viewModel.uiState.value.draftReady }

        viewModel.onDraftChanged("one note")
        viewModel.saveDraftAsNote()
        viewModel.saveDraftAsNote()

        repository.saveStarted.await()
        assertEquals(1, repository.saveCalls)
        repository.allowSave.complete(Unit)
        awaitCondition { !viewModel.uiState.value.isSaving }
    }

    @Test
    fun failedSaveKeepsTextAndPersistsCurrentDraft() = runBlocking {
        val repository = FailingRepository()
        val viewModel = NotesViewModel(repository, SavedStateHandle())
        awaitCondition { viewModel.uiState.value.draftReady }

        viewModel.onDraftChanged("do not lose this")
        viewModel.saveDraftAsNote()

        awaitCondition { !viewModel.uiState.value.isSaving }
        assertEquals("do not lose this", viewModel.uiState.value.draftText)
        assertTrue(viewModel.uiState.value.saveError != null)
        assertEquals("do not lose this", repository.draft.value.content)
    }

    @Test
    fun typingDuringSaveRemainsTheNextDraft() = runBlocking {
        val repository = BlockingRepository()
        val viewModel = NotesViewModel(repository, SavedStateHandle())
        awaitCondition { viewModel.uiState.value.draftReady }

        viewModel.onDraftChanged("first note")
        viewModel.saveDraftAsNote()
        repository.saveStarted.await()
        viewModel.onDraftChanged("next draft")
        repository.allowSave.complete(Unit)

        awaitCondition { !viewModel.uiState.value.isSaving }
        delay(NotesViewModel.DRAFT_DEBOUNCE_MILLIS + 100)
        assertEquals("next draft", viewModel.uiState.value.draftText)
        assertEquals("next draft", repository.draft.value.content)
        assertFalse(repository.draft.value.generation <= repository.savedGeneration)
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) {
                delay(10)
            }
        }
    }
}

private open class FakeRepository : NotesDataSource {
    val notes = MutableStateFlow<List<Note>>(emptyList())
    val draft = MutableStateFlow(CaptureDraft(content = "", generation = 0, updatedAtEpochMillis = 0))
    var savedGeneration = -1L

    override fun observeRecentNotes(): Flow<List<Note>> = notes

    override fun observeNote(id: String?): Flow<Note?> = flowOf(notes.value.firstOrNull { it.id == id })

    override fun observeDraft(): Flow<CaptureDraft> = draft

    override suspend fun persistDraftIfNewer(draft: CaptureDraft) {
        if (draft.generation > this.draft.value.generation) {
            this.draft.value = draft
        }
    }

    override suspend fun saveNoteAndAcknowledgeDraft(note: Note, savedDraftGeneration: Long) {
        notes.value = listOf(note) + notes.value
        savedGeneration = savedDraftGeneration
        if (draft.value.generation <= savedDraftGeneration) {
            draft.value = CaptureDraft("", savedDraftGeneration, note.updatedAtEpochMillis)
        }
    }
}

private class BlockingRepository : FakeRepository() {
    val saveStarted = CompletableDeferred<Unit>()
    val allowSave = CompletableDeferred<Unit>()
    var saveCalls = 0

    override suspend fun saveNoteAndAcknowledgeDraft(note: Note, savedDraftGeneration: Long) {
        saveCalls += 1
        saveStarted.complete(Unit)
        allowSave.await()
        super.saveNoteAndAcknowledgeDraft(note, savedDraftGeneration)
    }
}

private class FailingRepository : FakeRepository() {
    override suspend fun saveNoteAndAcknowledgeDraft(note: Note, savedDraftGeneration: Long) {
        throw IllegalStateException("test database failure")
    }
}
