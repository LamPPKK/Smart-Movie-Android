package com.lamndt.smartmovie.testing

import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.CatalogV2Repository
import com.lamndt.smartmovie.model.CapabilitiesV2
import com.lamndt.smartmovie.model.CatalogEntity
import com.lamndt.smartmovie.model.CollectionDetail
import com.lamndt.smartmovie.model.DiscoverFilter
import com.lamndt.smartmovie.model.Genre
import com.lamndt.smartmovie.model.HomeFeed
import com.lamndt.smartmovie.model.ImageConfiguration
import com.lamndt.smartmovie.model.EntityKind
import com.lamndt.smartmovie.model.EpisodeDetail
import com.lamndt.smartmovie.model.ExternalIdFindResult
import com.lamndt.smartmovie.model.ExternalIdSource
import com.lamndt.smartmovie.model.KeywordDetail
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibraryMembership
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.model.LibrarySnapshot
import com.lamndt.smartmovie.model.LibrarySort
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.OrganizationDetail
import com.lamndt.smartmovie.model.PagedResult
import com.lamndt.smartmovie.model.SearchScope
import com.lamndt.smartmovie.model.SearchScopeV2
import com.lamndt.smartmovie.model.SeasonDetail
import com.lamndt.smartmovie.model.PersonDetail
import com.lamndt.smartmovie.model.TitleDetail
import com.lamndt.smartmovie.model.TitleDetailV2
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

class FakeCatalogV2Repository(
    private val legacy: FakeCatalogRepository = FakeCatalogRepository(),
) : CatalogV2Repository, CatalogRepository by legacy {
    val externalIdCalls = mutableListOf<Triple<String, ExternalIdSource, String>>()
    var externalIdResult: suspend (String, ExternalIdSource) -> ExternalIdFindResult = { id, source ->
        ExternalIdFindResult(source, id, emptyList())
    }

    override suspend fun capabilities(): CapabilitiesV2 = error("Not configured")
    override suspend fun trending(
        kind: String,
        window: String,
        page: Int,
        language: String,
        includeAdult: Boolean,
    ): PagedResult<CatalogEntity> = error("Not configured")

    override suspend fun searchEntities(
        query: String,
        scope: SearchScopeV2,
        page: Int,
        language: String,
        region: String?,
        includeAdult: Boolean,
    ): PagedResult<CatalogEntity> {
        val legacyScope = when (scope) {
            SearchScopeV2.MOVIE -> SearchScope.MOVIE
            SearchScopeV2.TV -> SearchScope.TV
            else -> SearchScope.ALL
        }
        val result = legacy.search(query, legacyScope, page, language)
        return PagedResult(result.page, result.totalPages, result.results.map(CatalogEntity::Title))
    }

    override suspend fun findExternalId(
        externalId: String,
        source: ExternalIdSource,
        language: String,
    ): ExternalIdFindResult {
        externalIdCalls += Triple(externalId, source, language)
        return externalIdResult(externalId, source)
    }

    override suspend fun deepDetail(
        mediaType: MediaType,
        id: Int,
        language: String,
        region: String?,
        includeAdult: Boolean,
    ): TitleDetailV2 = error("Not configured")
    override suspend fun person(id: Int, language: String): PersonDetail = error("Not configured")
    override suspend fun collection(id: Int, language: String): CollectionDetail = error("Not configured")
    override suspend fun organization(kind: EntityKind, id: Int, language: String, page: Int): OrganizationDetail = error("Not configured")
    override suspend fun keyword(id: Int, language: String, page: Int): KeywordDetail = error("Not configured")
    override suspend fun season(seriesId: Int, number: Int, language: String): SeasonDetail = error("Not configured")
    override suspend fun episode(seriesId: Int, season: Int, number: Int, language: String): EpisodeDetail = error("Not configured")
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
