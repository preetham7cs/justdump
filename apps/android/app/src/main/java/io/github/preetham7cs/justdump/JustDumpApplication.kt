package io.github.preetham7cs.justdump

import android.app.Application
import io.github.preetham7cs.justdump.data.NotesRepository
import io.github.preetham7cs.justdump.data.local.JustDumpDatabase

class JustDumpApplication : Application() {
    val notesRepository: NotesRepository by lazy {
        NotesRepository(JustDumpDatabase.create(this).noteDao())
    }
}
