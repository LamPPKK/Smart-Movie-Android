package com.lamndt.smartmovie.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_items")
data class LibraryItemEntity(
    @PrimaryKey val libraryKey: String,
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val voteAverage: Double,
    val isFavorite: Boolean,
    val isWatchlisted: Boolean,
    val favoritedAt: Long?,
    val watchlistedAt: Long?,
    val updatedAt: Long,
)
