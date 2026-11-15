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
    entities = [NoteEntity::class, CaptureDraftEntity::class, VaultItemEntity::class, SyncOutboxEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class JustDumpDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun vaultItemDao(): VaultItemDao

    companion object {
        fun create(context: Context): JustDumpDatabase =
            Room.databaseBuilder<JustDumpDatabase>(
                context = context.applicationContext,
                name = "justdump.db",
            ).setDriver(AndroidSQLiteDriver())
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vault_items` (" +
                        "`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `textContent` TEXT, " +
                        "`sourceUrl` TEXT, `localFilePath` TEXT, `mimeType` TEXT, " +
                        "`createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, " +
                        "`syncStatus` TEXT NOT NULL, `syncError` TEXT, `syncedAtEpochMillis` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vault_items_createdAtEpochMillis` " +
                        "ON `vault_items` (`createdAtEpochMillis`)",
                )
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_outbox` (" +
                        "`itemId` TEXT NOT NULL, `operation` TEXT NOT NULL, " +
                        "`createdAtEpochMillis` INTEGER NOT NULL, `attemptCount` INTEGER NOT NULL, " +
                        "`nextAttemptAtEpochMillis` INTEGER, `lastError` TEXT, PRIMARY KEY(`itemId`))",
                )
                connection.execSQL(
                    "INSERT OR IGNORE INTO vault_items " +
                        "(id, kind, textContent, sourceUrl, localFilePath, mimeType, createdAtEpochMillis, " +
                        "updatedAtEpochMillis, syncStatus, syncError, syncedAtEpochMillis) " +
                        "SELECT id, 'TEXT', content, NULL, NULL, 'text/plain', createdAtEpochMillis, " +
                        "updatedAtEpochMillis, syncStatus, syncError, syncedAtEpochMillis FROM notes",
                )
                connection.execSQL(
                    "INSERT OR IGNORE INTO sync_outbox " +
                        "(itemId, operation, createdAtEpochMillis, attemptCount, nextAttemptAtEpochMillis, lastError) " +
                        "SELECT id, 'UPSERT', updatedAtEpochMillis, 0, NULL, syncError FROM notes " +
                        "WHERE syncStatus != 'SYNCED'",
                )
            }
        }
    }
}
