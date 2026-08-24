package com.lamndt.smartmovie.data

import androidx.room.withTransaction
import com.lamndt.smartmovie.database.LibraryItemEntity
import com.lamndt.smartmovie.database.LibraryOutboxEntity
import com.lamndt.smartmovie.database.SmartMovieDatabase
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibraryMembership
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.model.LibrarySyncRepository
import com.lamndt.smartmovie.model.LibrarySnapshot
import com.lamndt.smartmovie.model.LibrarySort
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.PendingLibraryMutation
import com.lamndt.smartmovie.model.TitleSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class DefaultLibraryRepository(
    private val database: SmartMovieDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : LibrarySyncRepository {
    private val dao = database.libraryDao()
    private var activeAccountId: Int? = null

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
                adult = title.adult,
                isFavorite = false,
                isWatchlisted = false,
                favoritedAt = null,
                watchlistedAt = null,
                updatedAt = timestamp,
            )
            val favorite = if (collection == LibraryCollection.FAVORITES) !current.isFavorite else current.isFavorite
            val watchlist = if (collection == LibraryCollection.WATCHLIST) !current.isWatchlisted else current.isWatchlisted
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
                adult = title.adult,
                isFavorite = favorite,
                isWatchlisted = watchlist,
                favoritedAt = if (collection == LibraryCollection.FAVORITES) if (!current.isFavorite) timestamp else null else current.favoritedAt,
                watchlistedAt = if (collection == LibraryCollection.WATCHLIST) if (!current.isWatchlisted) timestamp else null else current.watchlistedAt,
                updatedAt = timestamp,
                syncOrigin = if (activeAccountId == null) current.syncOrigin else "merged",
                favoritePending = current.favoritePending || (activeAccountId != null && collection == LibraryCollection.FAVORITES),
                watchlistPending = current.watchlistPending || (activeAccountId != null && collection == LibraryCollection.WATCHLIST),
                accountId = activeAccountId ?: current.accountId,
            )
            dao.upsert(next)
            activeAccountId?.let { accountId ->
                dao.upsertOutbox(
                    LibraryOutboxEntity(
                        mutationId = UUID.randomUUID().toString(),
                        libraryKey = title.libraryKey,
                        mediaType = title.mediaType.wireValue,
                        mediaId = title.id,
                        collection = collection.wireValue,
                        enabled = if (collection == LibraryCollection.FAVORITES) favorite else watchlist,
                        accountId = accountId,
                        createdAt = timestamp,
                    ),
                )
            }
        }
    }

    override suspend fun activateAccount(accountId: Int) {
        database.withTransaction {
            activeAccountId = accountId
            dao.getAll().forEach { item -> if (item.accountId == null) dao.upsert(item.copy(accountId = accountId)) }
        }
    }

    override suspend fun deactivateAccount(removeAccountData: Boolean) {
        database.withTransaction {
            val accountId = activeAccountId
            activeAccountId = null
            if (removeAccountData && accountId != null) {
                dao.deleteAccountItems(accountId)
                dao.deleteAccountOutbox(accountId)
            } else {
                dao.getAll().forEach {
                    dao.upsert(it.copy(accountId = null, syncOrigin = "local", favoritePending = false, watchlistPending = false))
                }
                dao.clearOutbox()
            }
        }
    }

    override suspend fun mergeRemote(
        items: List<TitleSummary>,
        collection: LibraryCollection,
        mediaType: MediaType,
        accountId: Int,
    ) {
        database.withTransaction {
            activeAccountId = accountId
            val remoteKeys = items.mapTo(mutableSetOf(), TitleSummary::libraryKey)
            items.forEach { title ->
                val current = dao.get(title.libraryKey)
                val timestamp = now()
                dao.upsert(
                    (current ?: title.toEntity(timestamp)).copy(
                        title = title.title,
                        originalTitle = title.originalTitle,
                        overview = title.overview,
                        posterPath = title.posterPath,
                        backdropPath = title.backdropPath,
                        releaseDate = title.releaseDate,
                        voteAverage = title.voteAverage,
                        adult = title.adult,
                        isFavorite = if (collection == LibraryCollection.FAVORITES && current?.favoritePending != true) true else current?.isFavorite ?: false,
                        isWatchlisted = if (collection == LibraryCollection.WATCHLIST && current?.watchlistPending != true) true else current?.isWatchlisted ?: false,
                        syncOrigin = if (current?.syncOrigin == "local") "merged" else "tmdb",
                        accountId = accountId,
                    ),
                )
            }
            dao.getAll().filter { it.mediaType == mediaType.wireValue }.forEach { item ->
                val enabled = if (collection == LibraryCollection.FAVORITES) item.isFavorite else item.isWatchlisted
                val pending = if (collection == LibraryCollection.FAVORITES) item.favoritePending else item.watchlistPending
                if (enabled && item.libraryKey !in remoteKeys && !pending) {
                    dao.upsertOutbox(
                        LibraryOutboxEntity(
                            UUID.randomUUID().toString(), item.libraryKey, item.mediaType, item.tmdbId,
                            collection.wireValue, true, accountId, now(),
                        ),
                    )
                    dao.upsert(if (collection == LibraryCollection.FAVORITES) item.copy(favoritePending = true) else item.copy(watchlistPending = true))
                } else if (!pending && item.libraryKey !in remoteKeys) {
                    dao.upsert(if (collection == LibraryCollection.FAVORITES) item.copy(isFavorite = false) else item.copy(isWatchlisted = false))
                }
            }
        }
    }

    override suspend fun pendingMutations(limit: Int): List<PendingLibraryMutation> = dao.pendingOutbox(limit).map {
        PendingLibraryMutation(
            id = it.mutationId,
            libraryKey = it.libraryKey,
            mediaType = if (it.mediaType == "tv") MediaType.TV else MediaType.MOVIE,
            mediaId = it.mediaId,
            collection = if (it.collection == "favorites") LibraryCollection.FAVORITES else LibraryCollection.WATCHLIST,
            enabled = it.enabled,
            accountId = it.accountId,
            attemptCount = it.attemptCount,
        )
    }

    override suspend fun confirmMutation(id: String) {
        database.withTransaction {
            val mutation = dao.outbox(id) ?: return@withTransaction
            dao.deleteOutbox(mutation)
            val remaining = dao.outboxForKey(mutation.libraryKey)
            dao.get(mutation.libraryKey)?.let { item ->
                dao.upsert(
                    item.copy(
                        favoritePending = item.favoritePending && remaining.any { it.collection == "favorites" },
                        watchlistPending = item.watchlistPending && remaining.any { it.collection == "watchlist" },
                        remoteRevision = id,
                        syncOrigin = "tmdb",
                    ),
                )
            }
        }
    }

    override suspend fun failMutation(id: String, message: String) {
        dao.outbox(id)?.let {
            dao.upsertOutbox(it.copy(attemptCount = it.attemptCount + 1, lastAttemptAt = now(), lastError = message.take(500)))
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
        adult = adult,
    ),
    isFavorite = isFavorite,
    isWatchlisted = isWatchlisted,
    favoritedAt = favoritedAt,
    watchlistedAt = watchlistedAt,
    updatedAt = updatedAt,
)

private fun TitleSummary.toEntity(timestamp: Long) = LibraryItemEntity(
    libraryKey = libraryKey,
    tmdbId = id,
    mediaType = mediaType.wireValue,
    title = title,
    originalTitle = originalTitle,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    adult = adult,
    isFavorite = false,
    isWatchlisted = false,
    favoritedAt = null,
    watchlistedAt = null,
    updatedAt = timestamp,
)

private val LibraryCollection.wireValue: String
    get() = if (this == LibraryCollection.FAVORITES) "favorites" else "watchlist"
