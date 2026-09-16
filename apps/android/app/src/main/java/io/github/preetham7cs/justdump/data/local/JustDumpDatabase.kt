package io.github.preetham7cs.justdump.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(
    entities = [NoteEntity::class, CaptureDraftEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class JustDumpDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        fun create(context: Context): JustDumpDatabase =
            Room.databaseBuilder<JustDumpDatabase>(
                context = context.applicationContext,
                name = "justdump.db",
            ).setDriver(AndroidSQLiteDriver())
                .addMigrations(MIGRATION_1_2)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE notes ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING_UPLOAD'",
                )
                connection.execSQL("ALTER TABLE notes ADD COLUMN syncError TEXT")
                connection.execSQL("ALTER TABLE notes ADD COLUMN syncedAtEpochMillis INTEGER")
            }
        }
    }
}
