package com.lamndt.smartmovie.data

import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.CatalogV2Repository
import com.lamndt.smartmovie.model.CapabilitiesV2
import com.lamndt.smartmovie.model.CatalogEntity
import com.lamndt.smartmovie.model.CollectionDetail
import com.lamndt.smartmovie.model.DiscoverFilter
import com.lamndt.smartmovie.model.Genre
import com.lamndt.smartmovie.model.HomeFeed
import com.lamndt.smartmovie.model.ImageConfiguration
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.EntityKind
import com.lamndt.smartmovie.model.EpisodeDetail
import com.lamndt.smartmovie.model.ExternalIdFindResult
import com.lamndt.smartmovie.model.ExternalIdSource
import com.lamndt.smartmovie.model.KeywordDetail
import com.lamndt.smartmovie.model.OrganizationDetail
import com.lamndt.smartmovie.model.PagedResult
import com.lamndt.smartmovie.model.PersonDetail
import com.lamndt.smartmovie.model.SearchScope
import com.lamndt.smartmovie.model.SearchScopeV2
import com.lamndt.smartmovie.model.SeasonDetail
import com.lamndt.smartmovie.model.TitleDetail
import com.lamndt.smartmovie.model.TitleDetailV2
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.network.CatalogRemoteDataSource
import com.lamndt.smartmovie.network.CatalogRemoteDataSourceV2

class DefaultCatalogRepository(private val network: CatalogRemoteDataSource) : CatalogV2Repository {
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

    override suspend fun capabilities(): CapabilitiesV2 = v2().capabilities()
    override suspend fun trending(kind: String, window: String, page: Int, language: String, includeAdult: Boolean): PagedResult<CatalogEntity> =
        v2().trending(kind, window, page, language, includeAdult)
    override suspend fun searchEntities(
        query: String,
        scope: SearchScopeV2,
        page: Int,
        language: String,
        region: String?,
        includeAdult: Boolean,
    ): PagedResult<CatalogEntity> = v2().searchEntities(query, scope, page, language, region, includeAdult)
    override suspend fun findExternalId(externalId: String, source: ExternalIdSource, language: String): ExternalIdFindResult =
        v2().findExternalId(externalId, source, language)
    override suspend fun deepDetail(mediaType: MediaType, id: Int, language: String, region: String?, includeAdult: Boolean): TitleDetailV2 =
        v2().deepDetail(mediaType, id, language, region, includeAdult)
    override suspend fun person(id: Int, language: String): PersonDetail = v2().person(id, language)
    override suspend fun collection(id: Int, language: String): CollectionDetail = v2().collection(id, language)
    override suspend fun organization(kind: EntityKind, id: Int, language: String, page: Int): OrganizationDetail =
        v2().organization(kind, id, language, page)
    override suspend fun keyword(id: Int, language: String, page: Int): KeywordDetail = v2().keyword(id, language, page)
    override suspend fun season(seriesId: Int, number: Int, language: String): SeasonDetail = v2().season(seriesId, number, language)
    override suspend fun episode(seriesId: Int, season: Int, number: Int, language: String): EpisodeDetail =
        v2().episode(seriesId, season, number, language)

    private fun v2(): CatalogRemoteDataSourceV2 = network as? CatalogRemoteDataSourceV2
        ?: error("The configured catalog data source does not support /v2")
}
