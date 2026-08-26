package com.lamndt.smartmovie.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.CatalogEntity
import com.lamndt.smartmovie.model.CatalogV2Repository
import com.lamndt.smartmovie.model.DiscoverFilter
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.SearchScope
import com.lamndt.smartmovie.model.SearchScopeV2
import com.lamndt.smartmovie.model.TitleSummary

class DiscoverPagingSource(
    private val catalog: CatalogRepository,
    private val mediaType: MediaType,
    private val filter: DiscoverFilter,
    private val language: String,
    private val advancedDiscoverEnabled: Boolean = false,
) : PagingSource<Int, TitleSummary>() {
    private val seenKeys = mutableSetOf<String>()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TitleSummary> = runCatching {
        val page = params.key ?: 1
        val response = if (advancedDiscoverEnabled) {
            catalog.discover(mediaType, filter, page, language)
        } else {
            catalog.discoverBasic(mediaType, filter, page, language)
        }
        LoadResult.Page(
            data = synchronized(seenKeys) { response.results.filter { seenKeys.add(it.libraryKey) } },
            prevKey = if (page > 1) page - 1 else null,
            nextKey = if (page < response.totalPages) page + 1 else null,
        )
    }.getOrElse { LoadResult.Error(it) }

    override fun getRefreshKey(state: PagingState<Int, TitleSummary>): Int? = state.anchorPosition?.let { anchor ->
        state.closestPageToPosition(anchor)?.let { page -> page.prevKey?.plus(1) ?: page.nextKey?.minus(1) }
    }
}

class EntitySearchPagingSource(
    private val catalog: CatalogRepository,
    private val query: String,
    private val scope: SearchScopeV2,
    private val language: String,
    private val region: String?,
    private val includeAdult: Boolean,
) : PagingSource<Int, CatalogEntity>() {
    private val seenKeys = mutableSetOf<String>()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CatalogEntity> = runCatching {
        val page = params.key ?: 1
        val response = if (catalog is CatalogV2Repository) {
            catalog.searchEntities(query, scope, page, language, region, includeAdult)
        } else {
            val legacyScope = when (scope) {
                SearchScopeV2.MOVIE -> SearchScope.MOVIE
                SearchScopeV2.TV -> SearchScope.TV
                else -> SearchScope.ALL
            }
            catalog.search(query, legacyScope, page, language).let {
                com.lamndt.smartmovie.model.PagedResult(it.page, it.totalPages, it.results.map(CatalogEntity::Title))
            }
        }
        LoadResult.Page(
            data = synchronized(seenKeys) { response.results.filter { seenKeys.add(it.stableKey) } },
            prevKey = if (page > 1) page - 1 else null,
            nextKey = if (page < response.totalPages) page + 1 else null,
        )
    }.getOrElse { LoadResult.Error(it) }

    override fun getRefreshKey(state: PagingState<Int, CatalogEntity>): Int? = state.anchorPosition?.let { anchor ->
        state.closestPageToPosition(anchor)?.let { page -> page.prevKey?.plus(1) ?: page.nextKey?.minus(1) }
    }
}

class SearchPagingSource(
    private val catalog: CatalogRepository,
    private val query: String,
    private val scope: SearchScope,
    private val language: String,
) : PagingSource<Int, TitleSummary>() {
    private val seenKeys = mutableSetOf<String>()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TitleSummary> = runCatching {
        val page = params.key ?: 1
        val response = catalog.search(query, scope, page, language)
        LoadResult.Page(
            data = synchronized(seenKeys) { response.results.filter { seenKeys.add(it.libraryKey) } },
            prevKey = if (page > 1) page - 1 else null,
            nextKey = if (page < response.totalPages) page + 1 else null,
        )
    }.getOrElse { LoadResult.Error(it) }

    override fun getRefreshKey(state: PagingState<Int, TitleSummary>): Int? = state.anchorPosition?.let { anchor ->
        state.closestPageToPosition(anchor)?.let { page -> page.prevKey?.plus(1) ?: page.nextKey?.minus(1) }
    }
}
