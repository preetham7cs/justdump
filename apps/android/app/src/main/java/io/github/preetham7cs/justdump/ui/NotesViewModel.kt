package io.github.preetham7cs.justdump.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.preetham7cs.justdump.data.CaptureDraft
import io.github.preetham7cs.justdump.data.Note
import io.github.preetham7cs.justdump.data.NotesDataSource
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotesUiState(
    val draftText: String = "",
    val draftReady: Boolean = false,
    val recentNotes: List<Note> = emptyList(),
    val selectedNoteId: String? = null,
    val selectedNote: Note? = null,
    val isSaving: Boolean = false,
    val inputError: String? = null,
    val saveError: String? = null,
    val saveNotice: SaveNotice? = null,
)

data class SaveNotice(
    val id: Long,
    val message: String,
)

class NotesViewModel(
    private val repository: NotesDataSource,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = mutableUiState

    private var currentDraftGeneration = 0L
    private var nextNoticeId = 0L
    private var draftWriteJob: Job? = null
    private var saveJob: Job? = null

    init {
        observeRecentNotes()
        observeDraft()
        observeSelectedNote()
    }

    fun onDraftChanged(content: String) {
        if (!mutableUiState.value.draftReady) return

        currentDraftGeneration += 1
        mutableUiState.update {
            it.copy(
                draftText = content,
                inputError = null,
                saveError = null,
            )
        }

        draftWriteJob?.cancel()
        val generation = currentDraftGeneration
        draftWriteJob = viewModelScope.launch {
            delay(DRAFT_DEBOUNCE_MILLIS)
            if (generation == currentDraftGeneration) {
                persistDraft(generation, content)
            }
        }
    }

    fun saveDraftAsNote() {
        val state = mutableUiState.value
        if (!state.draftReady || state.isSaving || saveJob?.isActive == true) return

        if (state.draftText.isBlank()) {
            mutableUiState.update { it.copy(inputError = "Write or paste a note before saving.") }
            return
        }

        val capturedText = state.draftText
        val capturedGeneration = currentDraftGeneration
        draftWriteJob?.cancel()
        mutableUiState.update { it.copy(isSaving = true, inputError = null, saveError = null) }

        saveJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            val note = Note(
                id = UUID.randomUUID().toString(),
                content = capturedText,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )

            try {
                repository.saveNoteAndAcknowledgeDraft(note, capturedGeneration)
                mutableUiState.update { current ->
                    if (currentDraftGeneration == capturedGeneration) {
                        current.copy(
                            draftText = "",
                            isSaving = false,
                            saveNotice = SaveNotice(nextNoticeId++, "Saved on this device"),
                        )
                    } else {
                        current.copy(
                            isSaving = false,
                            saveNotice = SaveNotice(nextNoticeId++, "Saved on this device"),
                        )
                    }
                }
            } catch (_: Exception) {
                // Keep the on-screen text, then make one immediate best-effort draft write.
                val latestText = mutableUiState.value.draftText
                val latestGeneration = currentDraftGeneration
                runCatching { persistDraft(latestGeneration, latestText) }
                mutableUiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = "Could not save on this device. Your draft is still open.",
                    )
                }
            }
        }
    }

    fun openNote(noteId: String) {
        savedStateHandle[SELECTED_NOTE_ID] = noteId
        mutableUiState.update { it.copy(selectedNoteId = noteId) }
    }

    fun returnToCapture() {
        savedStateHandle[SELECTED_NOTE_ID] = null
        mutableUiState.update { it.copy(selectedNoteId = null, selectedNote = null) }
    }

    fun onSaveNoticeShown(noticeId: Long) {
        mutableUiState.update { current ->
            if (current.saveNotice?.id == noticeId) current.copy(saveNotice = null) else current
        }
    }

    private fun observeRecentNotes() {
        viewModelScope.launch {
            repository.observeRecentNotes().collect { notes ->
                mutableUiState.update { it.copy(recentNotes = notes) }
            }
        }
    }

    private fun observeDraft() {
        viewModelScope.launch {
            repository.observeDraft().collect { storedDraft ->
                val state = mutableUiState.value
                if (!state.draftReady || storedDraft.generation > currentDraftGeneration) {
                    currentDraftGeneration = storedDraft.generation
                    mutableUiState.update {
                        it.copy(
                            draftText = storedDraft.content,
                            draftReady = true,
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSelectedNote() {
        viewModelScope.launch {
            savedStateHandle
                .getStateFlow<String?>(SELECTED_NOTE_ID, null)
                .flatMapLatest(repository::observeNote)
                .collect { note ->
                    mutableUiState.update {
                        it.copy(
                            selectedNoteId = savedStateHandle[SELECTED_NOTE_ID],
                            selectedNote = note,
                        )
                    }
                }
        }
    }

    private suspend fun persistDraft(generation: Long, content: String) {
        repository.persistDraftIfNewer(
            CaptureDraft(
                content = content,
                generation = generation,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    companion object {
        const val DRAFT_DEBOUNCE_MILLIS = 500L
        private const val SELECTED_NOTE_ID = "selected_note_id"

        fun factory(repository: NotesDataSource) =
            viewModelFactory {
                initializer {
                    NotesViewModel(
                        repository = repository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
