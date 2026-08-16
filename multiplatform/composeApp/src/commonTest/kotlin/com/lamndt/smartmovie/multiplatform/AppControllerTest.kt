package com.lamndt.smartmovie.multiplatform

import com.lamndt.smartmovie.multiplatform.data.CatalogApi
import com.lamndt.smartmovie.multiplatform.data.MemoryStore
import com.lamndt.smartmovie.multiplatform.model.DiscoverFilter
import com.lamndt.smartmovie.multiplatform.model.Genre
import com.lamndt.smartmovie.multiplatform.model.HomeFeed
import com.lamndt.smartmovie.multiplatform.model.ImageConfiguration
import com.lamndt.smartmovie.multiplatform.model.MediaType
import com.lamndt.smartmovie.multiplatform.model.PagedResult
import com.lamndt.smartmovie.multiplatform.model.SearchScope
import com.lamndt.smartmovie.multiplatform.model.TitleDetail
import com.lamndt.smartmovie.multiplatform.model.TitleSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AppControllerTest {
    @Test
    fun searchDebouncesAndCancelsThePreviousQuery() = runTest {
        val api = FakeCatalogApi()
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val controller = AppController(MemoryStore(), apiFactory = { api }, scope = appScope)
        advanceUntilIdle()

        controller.updateSearchQuery("fig")
        advanceTimeBy(200)
        controller.updateSearchQuery("fight")
        advanceTimeBy(349)
        assertEquals(emptyList(), api.searchQueries)

        advanceUntilIdle()
        assertEquals(listOf("fight"), api.searchQueries)
        val result = assertIs<LoadState.Content<List<TitleSummary>>>(controller.state.value.search)
        assertEquals("fight", result.value.single().title)
        controller.close()
    }

    @Test
    fun exploreYearIsForwardedToTheWorkerFilter() = runTest {
        val api = FakeCatalogApi()
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val controller = AppController(MemoryStore(), apiFactory = { api }, scope = appScope)
        advanceUntilIdle()

        controller.setExploreYear(1999)
        advanceUntilIdle()

        assertEquals(1999, api.discoverFilters.last().year)
        controller.close()
    }
}

private class FakeCatalogApi : CatalogApi {
    val searchQueries = mutableListOf<String>()
    val discoverFilters = mutableListOf<DiscoverFilter>()

    override suspend fun home(mediaType: MediaType, language: String) = HomeFeed(mediaType)
    override suspend fun genres(mediaType: MediaType, language: String): List<Genre> = emptyList()
    override suspend fun discover(mediaType: MediaType, filter: DiscoverFilter, page: Int, language: String): PagedResult<TitleSummary> {
        discoverFilters += filter
        return PagedResult(page, 1, emptyList())
    }

    override suspend fun search(query: String, scope: SearchScope, page: Int, language: String): PagedResult<TitleSummary> {
        searchQueries += query
        return PagedResult(
            page,
            1,
            listOf(TitleSummary(1, MediaType.MOVIE, query, query, "Result")),
        )
    }

    override suspend fun detail(mediaType: MediaType, id: Int, language: String): TitleDetail = error("Not used")
    override suspend fun imageConfiguration(): ImageConfiguration = ImageConfiguration.Fallback
}
