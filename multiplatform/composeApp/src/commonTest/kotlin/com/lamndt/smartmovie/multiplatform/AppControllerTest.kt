package com.lamndt.smartmovie.multiplatform

import com.lamndt.smartmovie.multiplatform.data.CatalogApi
import com.lamndt.smartmovie.multiplatform.data.CatalogApiV2
import com.lamndt.smartmovie.multiplatform.data.MemoryStore
import com.lamndt.smartmovie.multiplatform.model.DiscoverFilter
import com.lamndt.smartmovie.multiplatform.model.CapabilitiesV2
import com.lamndt.smartmovie.multiplatform.model.CatalogEntity
import com.lamndt.smartmovie.multiplatform.model.CatalogSearchMode
import com.lamndt.smartmovie.multiplatform.model.CollectionDetail
import com.lamndt.smartmovie.multiplatform.model.Credit
import com.lamndt.smartmovie.multiplatform.model.CreditDetail
import com.lamndt.smartmovie.multiplatform.model.EntityKind
import com.lamndt.smartmovie.multiplatform.model.EpisodeDetail
import com.lamndt.smartmovie.multiplatform.model.ExternalIdFindResult
import com.lamndt.smartmovie.multiplatform.model.ExternalIdSource
import com.lamndt.smartmovie.multiplatform.model.Genre
import com.lamndt.smartmovie.multiplatform.model.HomeFeed
import com.lamndt.smartmovie.multiplatform.model.ImageConfiguration
import com.lamndt.smartmovie.multiplatform.model.KeywordDetail
import com.lamndt.smartmovie.multiplatform.model.MediaType
import com.lamndt.smartmovie.multiplatform.model.PagedResult
import com.lamndt.smartmovie.multiplatform.model.OrganizationDetail
import com.lamndt.smartmovie.multiplatform.model.PersonDetail
import com.lamndt.smartmovie.multiplatform.model.PersonSummary
import com.lamndt.smartmovie.multiplatform.model.SearchScope
import com.lamndt.smartmovie.multiplatform.model.SearchScopeV2
import com.lamndt.smartmovie.multiplatform.model.SeasonDetail
import com.lamndt.smartmovie.multiplatform.model.TitleDetail
import com.lamndt.smartmovie.multiplatform.model.TitleDetailV2
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

    @Test
    fun externalIdLookupUsesSelectedSourceAndPublishesMixedEntities() = runTest {
        val api = FakeCatalogApi()
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val controller = AppController(MemoryStore(), apiFactory = { api }, scope = appScope)
        advanceUntilIdle()
        controller.changeSearchMode(CatalogSearchMode.EXTERNAL_ID)
        controller.changeExternalIdSource(ExternalIdSource.WIKIDATA)
        controller.updateSearchQuery(" Q83495 ")

        controller.findExternalId()
        advanceUntilIdle()

        assertEquals(listOf(Triple("Q83495", ExternalIdSource.WIKIDATA, "en-US")), api.externalIdCalls)
        val result = assertIs<LoadState.Content<List<CatalogEntity>>>(controller.state.value.externalIdSearch)
        assertEquals(listOf(EntityKind.MOVIE, EntityKind.PERSON), result.value.map(CatalogEntity::entityKind))

        controller.changeSearchMode(CatalogSearchMode.EXTERNAL_ID)
        controller.changeExternalIdSource(ExternalIdSource.WIKIDATA)

        assertIs<LoadState.Content<List<CatalogEntity>>>(controller.state.value.externalIdSearch)
        assertEquals(" Q83495 ", controller.state.value.searchQuery)
        controller.close()
    }

    @Test
    fun creditDetailPublishesStablePersonAndTitleNavigation() = runTest {
        val api = FakeCatalogApi()
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val controller = AppController(MemoryStore(), apiFactory = { api }, scope = appScope)
        advanceUntilIdle()

        controller.openCredit(Credit(creditId = "credit-603", id = 6384, title = "Keanu Reeves"))
        advanceUntilIdle()

        assertEquals(listOf(Pair("credit-603", "en-US")), api.creditCalls)
        val result = assertIs<LoadState.Content<EntityDetail>>(controller.state.value.entityDetail)
        val credit = assertIs<EntityDetail.Credit>(result.value).value
        assertEquals(6384, credit.personSummary?.id)
        assertEquals("movie:603", credit.titleSummary?.libraryKey)
        controller.close()
    }
}

private class FakeCatalogApi : CatalogApiV2 {
    val searchQueries = mutableListOf<String>()
    val discoverFilters = mutableListOf<DiscoverFilter>()
    val externalIdCalls = mutableListOf<Triple<String, ExternalIdSource, String>>()
    val creditCalls = mutableListOf<Pair<String, String>>()

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

    override suspend fun capabilities(): CapabilitiesV2 = error("Not used")
    override suspend fun trending(
        kind: String,
        window: String,
        page: Int,
        language: String,
        includeAdult: Boolean,
    ): PagedResult<CatalogEntity> = error("Not used")

    override suspend fun searchEntities(
        query: String,
        scope: SearchScopeV2,
        page: Int,
        language: String,
        region: String?,
        includeAdult: Boolean,
    ): PagedResult<CatalogEntity> {
        searchQueries += query
        return PagedResult(
            page,
            1,
            listOf(CatalogEntity.Title(TitleSummary(1, MediaType.MOVIE, query, query, "Result"))),
        )
    }

    override suspend fun findExternalId(
        externalId: String,
        source: ExternalIdSource,
        language: String,
    ): ExternalIdFindResult {
        externalIdCalls += Triple(externalId, source, language)
        return ExternalIdFindResult(
            source,
            externalId,
            listOf(
                CatalogEntity.Title(TitleSummary(603, MediaType.MOVIE, "The Matrix", "The Matrix", "")),
                CatalogEntity.Person(PersonSummary(6384, "Keanu Reeves")),
            ),
        )
    }

    override suspend fun deepDetail(
        mediaType: MediaType,
        id: Int,
        language: String,
        region: String?,
        includeAdult: Boolean,
    ): TitleDetailV2 = error("Not used")
    override suspend fun person(id: Int, language: String): PersonDetail = error("Not used")
    override suspend fun collection(id: Int, language: String): CollectionDetail = error("Not used")
    override suspend fun organization(kind: EntityKind, id: Int, language: String, page: Int): OrganizationDetail = error("Not used")
    override suspend fun keyword(id: Int, language: String, page: Int): KeywordDetail = error("Not used")
    override suspend fun season(seriesId: Int, number: Int, language: String): SeasonDetail = error("Not used")
    override suspend fun episode(seriesId: Int, season: Int, number: Int, language: String): EpisodeDetail = error("Not used")
    override suspend fun credit(id: String, language: String): CreditDetail {
        creditCalls += id to language
        return CreditDetail(
            creditId = id,
            creditType = "cast",
            department = "Acting",
            job = "Actor",
            character = "Neo",
            personSummary = PersonSummary(6384, "Keanu Reeves"),
            titleSummary = TitleSummary(603, MediaType.MOVIE, "The Matrix", "The Matrix", ""),
        )
    }
}
