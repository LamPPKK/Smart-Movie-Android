package com.lamndt.smartmovie.data

import com.google.common.truth.Truth.assertThat
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
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultCatalogRepositoryTest {
    @Test
    fun imageConfigurationFallsBackAndCachesWhenWorkerIsUnavailable() = runTest {
        val remote = FailingConfigurationRemote()
        val repository = DefaultCatalogRepository(remote)

        assertThat(repository.imageConfiguration()).isEqualTo(ImageConfiguration.Fallback)
        assertThat(repository.imageConfiguration()).isEqualTo(ImageConfiguration.Fallback)
        assertThat(remote.configurationCalls).isEqualTo(1)
    }
}

private class FailingConfigurationRemote : CatalogRemoteDataSource {
    var configurationCalls = 0
    override suspend fun imageConfiguration(): ImageConfiguration {
        configurationCalls++
        error("offline")
    }

    override suspend fun home(mediaType: MediaType, language: String) = HomeFeed(mediaType)
    override suspend fun genres(mediaType: MediaType, language: String): List<Genre> = emptyList()
    override suspend fun discover(mediaType: MediaType, filter: DiscoverFilter, page: Int, language: String) =
        PagedResult<TitleSummary>(page, page, emptyList())
    override suspend fun search(query: String, scope: SearchScope, page: Int, language: String) =
        PagedResult<TitleSummary>(page, page, emptyList())
    override suspend fun detail(mediaType: MediaType, id: Int, language: String) =
        TitleDetail(id, mediaType, "", "", "")
}
