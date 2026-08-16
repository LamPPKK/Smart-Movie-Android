package com.lamndt.smartmovie.data

import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.DiscoverFilter
import com.lamndt.smartmovie.model.Genre
import com.lamndt.smartmovie.model.HomeFeed
import com.lamndt.smartmovie.model.ImageConfiguration
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.PagedResult
import com.lamndt.smartmovie.model.SearchScope
import com.lamndt.smartmovie.model.TitleDetail
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.network.CatalogRemoteDataSource

class DefaultCatalogRepository(private val network: CatalogRemoteDataSource) : CatalogRepository {
    private var cachedImageConfiguration: ImageConfiguration? = null

    override suspend fun home(mediaType: MediaType, language: String): HomeFeed = network.home(mediaType, language)
    override suspend fun genres(mediaType: MediaType, language: String): List<Genre> = network.genres(mediaType, language)
    override suspend fun discover(mediaType: MediaType, filter: DiscoverFilter, page: Int, language: String): PagedResult<TitleSummary> =
        network.discover(mediaType, filter, page, language)
    override suspend fun search(query: String, scope: SearchScope, page: Int, language: String): PagedResult<TitleSummary> =
        network.search(query, scope, page, language)
    override suspend fun detail(mediaType: MediaType, id: Int, language: String): TitleDetail = network.detail(mediaType, id, language)

    override suspend fun imageConfiguration(): ImageConfiguration {
        cachedImageConfiguration?.let { return it }
        return runCatching { network.imageConfiguration() }
            .getOrDefault(ImageConfiguration.Fallback)
            .also { cachedImageConfiguration = it }
    }
}
