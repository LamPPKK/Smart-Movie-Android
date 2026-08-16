package com.lamndt.smartmovie.data

import androidx.room.withTransaction
import com.lamndt.smartmovie.database.LibraryItemEntity
import com.lamndt.smartmovie.database.SmartMovieDatabase
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibraryMembership
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.model.LibrarySnapshot
import com.lamndt.smartmovie.model.LibrarySort
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.TitleSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultLibraryRepository(
    private val database: SmartMovieDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : LibraryRepository {
    private val dao = database.libraryDao()

    override fun observeItems(
        collection: LibraryCollection,
        mediaType: MediaType?,
        sort: LibrarySort,
    ): Flow<List<LibrarySnapshot>> = dao.observeAll().map { entities ->
        entities.asSequence()
            .filter { if (collection == LibraryCollection.FAVORITES) it.isFavorite else it.isWatchlisted }
            .filter { mediaType == null || it.mediaType == mediaType.wireValue }
            .map(LibraryItemEntity::toSnapshot)
            .sortedWith(comparator(collection, sort))
            .toList()
    }

    override fun observeMembership(libraryKey: String): Flow<LibraryMembership> = dao.observe(libraryKey).map {
        LibraryMembership(it?.isFavorite == true, it?.isWatchlisted == true)
    }

    override suspend fun toggle(title: TitleSummary, collection: LibraryCollection) {
        database.withTransaction {
            val timestamp = now()
            val current = dao.get(title.libraryKey) ?: LibraryItemEntity(
                libraryKey = title.libraryKey,
                tmdbId = title.id,
                mediaType = title.mediaType.wireValue,
                title = title.title,
                originalTitle = title.originalTitle,
                overview = title.overview,
                posterPath = title.posterPath,
                backdropPath = title.backdropPath,
                releaseDate = title.releaseDate,
                voteAverage = title.voteAverage,
                isFavorite = false,
                isWatchlisted = false,
                favoritedAt = null,
                watchlistedAt = null,
                updatedAt = timestamp,
            )
            val next = current.copy(
                tmdbId = title.id,
                mediaType = title.mediaType.wireValue,
                title = title.title,
                originalTitle = title.originalTitle,
                overview = title.overview,
                posterPath = title.posterPath,
                backdropPath = title.backdropPath,
                releaseDate = title.releaseDate,
                voteAverage = title.voteAverage,
                isFavorite = if (collection == LibraryCollection.FAVORITES) !current.isFavorite else current.isFavorite,
                isWatchlisted = if (collection == LibraryCollection.WATCHLIST) !current.isWatchlisted else current.isWatchlisted,
                favoritedAt = if (collection == LibraryCollection.FAVORITES) if (!current.isFavorite) timestamp else null else current.favoritedAt,
                watchlistedAt = if (collection == LibraryCollection.WATCHLIST) if (!current.isWatchlisted) timestamp else null else current.watchlistedAt,
                updatedAt = timestamp,
            )
            dao.upsert(next)
        }
    }

    private fun comparator(collection: LibraryCollection, sort: LibrarySort): Comparator<LibrarySnapshot> = when (sort) {
        LibrarySort.RECENTLY_ADDED -> compareByDescending {
            if (collection == LibraryCollection.FAVORITES) it.favoritedAt else it.watchlistedAt
        }
        LibrarySort.TITLE -> compareBy<LibrarySnapshot> { it.title.displayTitle.lowercase() }
        LibrarySort.RELEASE_DATE -> compareByDescending { it.title.releaseDate.orEmpty() }
    }
}

private fun LibraryItemEntity.toSnapshot(): LibrarySnapshot = LibrarySnapshot(
    id = libraryKey,
    title = TitleSummary(
        id = tmdbId,
        mediaType = if (mediaType == "tv") MediaType.TV else MediaType.MOVIE,
        title = title,
        originalTitle = originalTitle,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate,
        voteAverage = voteAverage,
    ),
    isFavorite = isFavorite,
    isWatchlisted = isWatchlisted,
    favoritedAt = favoritedAt,
    watchlistedAt = watchlistedAt,
    updatedAt = updatedAt,
)
