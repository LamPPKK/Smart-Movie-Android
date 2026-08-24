package com.lamndt.smartmovie.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LibraryItemEntity::class, LibraryOutboxEntity::class], version = 2, exportSchema = true)
abstract class SmartMovieDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        fun create(context: Context): SmartMovieDatabase = Room.databaseBuilder(
            context.applicationContext,
            SmartMovieDatabase::class.java,
            "smartmovie_library.db",
        ).addMigrations(MIGRATION_1_2).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_items ADD COLUMN adult INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE library_items ADD COLUMN syncOrigin TEXT NOT NULL DEFAULT 'local'")
                db.execSQL("ALTER TABLE library_items ADD COLUMN favoritePending INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE library_items ADD COLUMN watchlistPending INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE library_items ADD COLUMN remoteRevision TEXT")
                db.execSQL("ALTER TABLE library_items ADD COLUMN accountId INTEGER")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS library_outbox (
                        mutationId TEXT NOT NULL PRIMARY KEY,
                        libraryKey TEXT NOT NULL,
                        mediaType TEXT NOT NULL,
                        mediaId INTEGER NOT NULL,
                        collection TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        accountId INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        attemptCount INTEGER NOT NULL DEFAULT 0,
                        lastAttemptAt INTEGER,
                        lastError TEXT
                    )""".trimIndent(),
                )
            }
        }
    }
}
