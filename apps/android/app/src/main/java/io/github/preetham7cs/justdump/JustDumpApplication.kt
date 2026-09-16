package io.github.preetham7cs.justdump

import android.app.Application
import io.github.preetham7cs.justdump.data.NotesRepository
import io.github.preetham7cs.justdump.data.local.JustDumpDatabase
import io.github.preetham7cs.justdump.auth.SupabaseAuthRepository
import io.github.preetham7cs.justdump.cloud.CloudNotesApi
import io.github.preetham7cs.justdump.cloud.NoteSyncCoordinator

class JustDumpApplication : Application() {
    val notesRepository: NotesRepository by lazy {
        NotesRepository(JustDumpDatabase.create(this).noteDao())
    }

    val authRepository: SupabaseAuthRepository by lazy { SupabaseAuthRepository() }

    val noteSyncCoordinator: NoteSyncCoordinator by lazy {
        NoteSyncCoordinator(notesRepository, authRepository, CloudNotesApi())
    }
}
