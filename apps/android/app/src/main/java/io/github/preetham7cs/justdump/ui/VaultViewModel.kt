package io.github.preetham7cs.justdump.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.preetham7cs.justdump.data.VaultItem
import io.github.preetham7cs.justdump.data.VaultRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VaultUiState(
    val items: List<VaultItem> = emptyList(),
    val selectedItemId: String? = null,
    val selectedItem: VaultItem? = null,
    val isSaving: Boolean = false,
    val message: String? = null,
)

class VaultViewModel(private val repository: VaultRepository) : ViewModel() {
    private val state = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = state

    init {
        viewModelScope.launch {
            repository.observeNonTextItems().collect { items -> state.update { it.copy(items = items) } }
        }
    }

    fun saveImage(uri: Uri) = save { repository.saveImage(uri) }

    fun saveLink(url: String) = save { repository.saveLink(url) }

    fun requestSync() {
        repository.requestSync()
        state.update { it.copy(message = "Cloud upload requested for pending items.") }
    }

    fun openItem(id: String) {
        state.update { it.copy(selectedItemId = id, message = null) }
        observeSelectedItem(id)
    }

    fun closeItem() = state.update { it.copy(selectedItemId = null, selectedItem = null) }

    fun clearMessage() = state.update { it.copy(message = null) }

    private fun save(work: suspend () -> VaultItem) {
        if (state.value.isSaving) return
        state.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            runCatching { work() }
                .onSuccess { state.update { it.copy(isSaving = false, message = "Saved on this device") } }
                .onFailure { error ->
                    val message = error.message ?: "Could not save this item on this device."
                    state.update { it.copy(isSaving = false, message = message) }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSelectedItem(id: String) {
        viewModelScope.launch {
            repository.observeItem(id).collect { item ->
                if (state.value.selectedItemId == id) state.update { it.copy(selectedItem = item) }
            }
        }
    }

    companion object {
        fun factory(repository: VaultRepository) = viewModelFactory {
            initializer { VaultViewModel(repository) }
        }
    }
}
