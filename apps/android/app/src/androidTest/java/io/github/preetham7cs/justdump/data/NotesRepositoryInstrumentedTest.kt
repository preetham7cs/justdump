package io.github.preetham7cs.justdump.data

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.preetham7cs.justdump.data.local.JustDumpDatabase
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesRepositoryInstrumentedTest {
    private lateinit var database: JustDumpDatabase
    private lateinit var repository: NotesRepository
    private val databaseName = "notes-test-${UUID.randomUUID()}.db"

    @Before
    fun createDatabase() {
        database =
            Room.databaseBuilder<JustDumpDatabase>(
                context = ApplicationProvider.getApplicationContext(),
                name = databaseName,
            )
                .setDriver(AndroidSQLiteDriver())
                .build()
        repository = NotesRepository(database.noteDao())
    }

    @After
    fun closeDatabase() {
        database.close()
        ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(databaseName)
    }

    @Test
    fun savedNoteSurvivesNewerDraftAndOldDraftCannotReturn() = runBlocking {
        repository.persistDraftIfNewer(CaptureDraft("saved snapshot", generation = 1, updatedAtEpochMillis = 1))
        repository.persistDraftIfNewer(CaptureDraft("new typing", generation = 2, updatedAtEpochMillis = 2))

        repository.saveNoteAndAcknowledgeDraft(
            Note("note-1", "saved snapshot", createdAtEpochMillis = 3, updatedAtEpochMillis = 3),
            savedDraftGeneration = 1,
        )
        // Simulates an already-running delayed write from before Save.
        repository.persistDraftIfNewer(CaptureDraft("saved snapshot", generation = 1, updatedAtEpochMillis = 4))

        assertEquals("new typing", repository.observeDraft().first().content)
        assertEquals(2, repository.observeDraft().first().generation)
        assertEquals(listOf("saved snapshot"), repository.observeRecentNotes().first().map(Note::content))
    }

    @Test
    fun failedDuplicateInsertLeavesDraftUntouched() = runBlocking {
        repository.persistDraftIfNewer(CaptureDraft("keep this draft", generation = 8, updatedAtEpochMillis = 8))
        val note = Note("duplicate-id", "first save", createdAtEpochMillis = 9, updatedAtEpochMillis = 9)
        repository.saveNoteAndAcknowledgeDraft(note, savedDraftGeneration = 7)

        val result = runCatching {
            repository.saveNoteAndAcknowledgeDraft(note, savedDraftGeneration = 8)
        }

        assertTrue(result.isFailure)
        assertEquals("keep this draft", repository.observeDraft().first().content)
        assertEquals(8, repository.observeDraft().first().generation)
        assertEquals(1, repository.observeRecentNotes().first().size)
    }
}
