package com.lamndt.smartmovie.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_items")
    fun observeAll(): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items WHERE libraryKey = :key LIMIT 1")
    fun observe(key: String): Flow<LibraryItemEntity?>

    @Query("SELECT * FROM library_items WHERE libraryKey = :key LIMIT 1")
    suspend fun get(key: String): LibraryItemEntity?

    @Upsert
    suspend fun upsert(item: LibraryItemEntity)
}
