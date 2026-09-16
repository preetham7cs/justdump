package io.github.preetham7cs.justdump.auth

import io.github.preetham7cs.justdump.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient

class SupabaseAuthRepository {
    private val client by lazy {
        require(configured) { "Supabase sign-in is not configured on this development build." }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Auth)
        }
    }

    val configured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() {
        if (configured) client.auth.signOut()
    }

    fun accessTokenOrNull(): String? =
        if (configured) client.auth.currentSessionOrNull()?.accessToken else null

    fun emailOrNull(): String? =
        if (configured) client.auth.currentUserOrNull()?.email else null
}
