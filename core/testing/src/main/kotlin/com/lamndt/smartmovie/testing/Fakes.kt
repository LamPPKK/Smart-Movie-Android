package com.lamndt.smartmovie.testing

import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.DiscoverFilter
import com.lamndt.smartmovie.model.Genre
import com.lamndt.smartmovie.model.HomeFeed
import com.lamndt.smartmovie.model.ImageConfiguration
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibraryMembership
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.model.LibrarySnapshot
import com.lamndt.smartmovie.model.LibrarySort
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.PagedResult
import com.lamndt.smartmovie.model.SearchScope
import com.lamndt.smartmovie.model.TitleDetail
import com.lamndt.smartmovie.model.TitleSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCatalogRepository : CatalogRepository {
    val homeCalls = mutableListOf<MediaType>()
    val searchCalls = mutableListOf<String>()
    var homeResult: suspend (MediaType) -> HomeFeed = { HomeFeed(it) }
    var genresResult: suspend (MediaType) -> List<Genre> = { emptyList() }
    var discoverResult: suspend (MediaType, DiscoverFilter, Int) -> PagedResult<TitleSummary> = { _, _, page -> PagedResult(page, page, emptyList()) }
    var searchResult: suspend (String, SearchScope, Int) -> PagedResult<TitleSummary> = { _, _, page -> PagedResult(page, page, emptyList()) }
    var detailResult: suspend (MediaType, Int) -> TitleDetail = { type, id -> TitleDetail(id, type, "Title", "Title", "") }

    override suspend fun home(mediaType: MediaType, language: String): HomeFeed {
        homeCalls += mediaType
        return homeResult(mediaType)
    }
    override suspend fun genres(mediaType: MediaType, language: String) = genresResult(mediaType)
    override suspend fun discover(mediaType: MediaType, filter: DiscoverFilter, page: Int, language: String) = discoverResult(mediaType, filter, page)
    override suspend fun search(query: String, scope: SearchScope, page: Int, language: String): PagedResult<TitleSummary> {
        searchCalls += query
        return searchResult(query, scope, page)
    }
    override suspend fun detail(mediaType: MediaType, id: Int, language: String) = detailResult(mediaType, id)
    override suspend fun imageConfiguration() = ImageConfiguration.Fallback
}

class FakeLibraryRepository : LibraryRepository {
    private val snapshots = MutableStateFlow<List<LibrarySnapshot>>(emptyList())
    val toggles = mutableListOf<Pair<String, LibraryCollection>>()

    override fun observeItems(collection: LibraryCollection, mediaType: MediaType?, sort: LibrarySort): Flow<List<LibrarySnapshot>> = snapshots.map { items ->
        items.filter { (collection == LibraryCollection.FAVORITES && it.isFavorite) || (collection == LibraryCollection.WATCHLIST && it.isWatchlisted) }
            .filter { mediaType == null || it.title.mediaType == mediaType }
    }

    override fun observeMembership(libraryKey: String): Flow<LibraryMembership> = snapshots.map { items ->
        items.firstOrNull { it.id == libraryKey }?.let { LibraryMembership(it.isFavorite, it.isWatchlisted) } ?: LibraryMembership()
    }

    override suspend fun toggle(title: TitleSummary, collection: LibraryCollection) {
        toggles += title.libraryKey to collection
        val current = snapshots.value.firstOrNull { it.id == title.libraryKey }
        val next = LibrarySnapshot(
            title.libraryKey, title,
            isFavorite = if (collection == LibraryCollection.FAVORITES) current?.isFavorite != true else current?.isFavorite == true,
            isWatchlisted = if (collection == LibraryCollection.WATCHLIST) current?.isWatchlisted != true else current?.isWatchlisted == true,
            favoritedAt = null, watchlistedAt = null, updatedAt = 0,
        )
        snapshots.value = snapshots.value.filterNot { it.id == title.libraryKey } + next
    }
}
