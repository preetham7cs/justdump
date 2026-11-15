package io.github.preetham7cs.justdump

import android.app.Application
import io.github.preetham7cs.justdump.data.NotesRepository
import io.github.preetham7cs.justdump.data.VaultRepository
import io.github.preetham7cs.justdump.data.local.JustDumpDatabase
import io.github.preetham7cs.justdump.auth.SupabaseAuthRepository
import io.github.preetham7cs.justdump.cloud.CloudNotesApi
import io.github.preetham7cs.justdump.cloud.CloudVaultApi
import io.github.preetham7cs.justdump.cloud.NoteSyncCoordinator

class JustDumpApplication : Application() {
    private val database by lazy { JustDumpDatabase.create(this) }

    val notesRepository: NotesRepository by lazy {
        NotesRepository(this, database.noteDao())
    }

    val vaultRepository: VaultRepository by lazy {
        VaultRepository(this, database.vaultItemDao())
    }

    val authRepository: SupabaseAuthRepository by lazy { SupabaseAuthRepository() }

    val noteSyncCoordinator: NoteSyncCoordinator by lazy {
        NoteSyncCoordinator(notesRepository, authRepository, CloudNotesApi())
    }

    val cloudVaultApi: CloudVaultApi by lazy { CloudVaultApi() }
}
