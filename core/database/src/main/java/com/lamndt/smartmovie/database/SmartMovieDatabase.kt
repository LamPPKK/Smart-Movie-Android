package com.lamndt.smartmovie.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LibraryItemEntity::class], version = 1, exportSchema = true)
abstract class SmartMovieDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        fun create(context: Context): SmartMovieDatabase = Room.databaseBuilder(
            context.applicationContext,
            SmartMovieDatabase::class.java,
            "smartmovie_library.db",
        ).build()
    }
}
