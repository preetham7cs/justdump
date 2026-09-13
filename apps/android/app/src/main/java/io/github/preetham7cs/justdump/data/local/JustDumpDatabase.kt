package io.github.preetham7cs.justdump.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(
    entities = [NoteEntity::class, CaptureDraftEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class JustDumpDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        fun create(context: Context): JustDumpDatabase =
            Room.databaseBuilder<JustDumpDatabase>(
                context = context.applicationContext,
                name = "justdump.db",
            ).setDriver(AndroidSQLiteDriver()).build()
    }
}
