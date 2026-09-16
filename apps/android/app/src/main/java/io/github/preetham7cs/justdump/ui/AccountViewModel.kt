package io.github.preetham7cs.justdump.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.preetham7cs.justdump.auth.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val configured: Boolean,
    val email: String = "",
    val password: String = "",
    val signedInEmail: String? = null,
    val isWorking: Boolean = false,
    val error: String? = null,
)

class AccountViewModel(
    private val auth: SupabaseAuthRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        AccountUiState(
            configured = auth.configured,
            signedInEmail = auth.emailOrNull(),
        ),
    )
    val uiState: StateFlow<AccountUiState> = mutableUiState

    fun onEmailChanged(value: String) = mutableUiState.update { it.copy(email = value, error = null) }

    fun onPasswordChanged(value: String) = mutableUiState.update { it.copy(password = value, error = null) }

    fun signIn() {
        val state = mutableUiState.value
        if (state.isWorking) return
        if (!state.configured) {
            mutableUiState.update { it.copy(error = "Supabase is not configured on this development build.") }
            return
        }
        if (state.email.isBlank() || state.password.isBlank()) {
            mutableUiState.update { it.copy(error = "Enter your email and password.") }
            return
        }
        mutableUiState.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                auth.signIn(state.email, state.password)
                mutableUiState.update {
                    it.copy(
                        signedInEmail = auth.emailOrNull(),
                        password = "",
                        isWorking = false,
                    )
                }
            } catch (_: Exception) {
                mutableUiState.update {
                    it.copy(isWorking = false, error = "Could not sign in. Check the account and connection, then try again.")
                }
            }
        }
    }

    fun signOut() {
        if (mutableUiState.value.isWorking) return
        mutableUiState.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            runCatching { auth.signOut() }
            mutableUiState.update { it.copy(signedInEmail = null, password = "", isWorking = false) }
        }
    }

    companion object {
        fun factory(auth: SupabaseAuthRepository) = viewModelFactory {
            initializer { AccountViewModel(auth) }
        }
    }
}
