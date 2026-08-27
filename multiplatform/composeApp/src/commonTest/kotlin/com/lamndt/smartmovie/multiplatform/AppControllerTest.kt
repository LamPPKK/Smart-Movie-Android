package com.lamndt.smartmovie.multiplatform

import com.lamndt.smartmovie.multiplatform.data.CatalogApi
import com.lamndt.smartmovie.multiplatform.data.CatalogApiV2
import com.lamndt.smartmovie.multiplatform.data.MemoryStore
import com.lamndt.smartmovie.multiplatform.model.DiscoverFilter
import com.lamndt.smartmovie.multiplatform.model.DiscoverConfiguration
import com.lamndt.smartmovie.multiplatform.model.CapabilitiesV2
import com.lamndt.smartmovie.multiplatform.model.AdultContentCapability
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
import com.lamndt.smartmovie.multiplatform.model.UserList
import com.lamndt.smartmovie.multiplatform.model.WatchMonetizationType
import com.lamndt.smartmovie.multiplatform.model.supportsAccountAuthentication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppControllerTest {
    @Test
    fun adultConfigurationRequiresAgeConfirmationAndSixDigits() = runTest {
        val api = FakeCatalogApi()
        val controller = AppController(
            MemoryStore(),
            apiFactory = { api },
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )
        advanceUntilIdle()

        assertFalse(controller.configureAdultPin("123456", "123456", ageConfirmed = false))
        assertFalse(controller.state.value.adultConfigured)
        assertFalse(controller.configureAdultPin("12345", "12345", ageConfirmed = true))
        assertFalse(controller.configureAdultPin("123456", "654321", ageConfirmed = true))
        assertTrue(controller.configureAdultPin("123456", "123456", ageConfirmed = true))
        assertTrue(controller.state.value.adultConfigured)
        assertTrue(controller.state.value.adultUnlocked)
        controller.close()
    }

    @Test
    fun fiveFailedAdultUnlocksResetAttemptsAndLockForFiveMinutes() = runTest {
        val api = FakeCatalogApi()
        var currentTime = 1_800_000_000_000L
        val controller = AppController(
            MemoryStore(),
            apiFactory = { api },
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            nowMillis = { currentTime },
        )
        advanceUntilIdle()
        assertTrue(controller.configureAdultPin("123456", "123456", ageConfirmed = true))
        controller.lockAdult()

        repeat(4) {
            assertFalse(controller.unlockAdult("000000"))
            assertEquals(it + 1, controller.state.value.adultFailures)
            assertEquals(0, controller.state.value.adultLockUntil)
        }
        assertFalse(controller.unlockAdult("000000"))
        assertEquals(0, controller.state.value.adultFailures)
        assertEquals(currentTime + 5 * 60 * 1_000L, controller.state.value.adultLockUntil)
        assertFalse(controller.unlockAdult("123456"))

        currentTime += 5 * 60 * 1_000L + 1
        assertTrue(controller.unlockAdult("123456"))
        assertTrue(controller.state.value.adultUnlocked)
        controller.close()
    }

    @Test
    fun disablingAdultContentRemovesThePinConfiguration() = runTest {
        val controller = AppController(
            MemoryStore(),
            apiFactory = { FakeCatalogApi() },
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )
        advanceUntilIdle()
        assertTrue(controller.configureAdultPin("123456", "123456", ageConfirmed = true))

        controller.disableAdult()

        assertFalse(controller.state.value.adultConfigured)
        assertFalse(controller.state.value.adultUnlocked)
        assertFalse(controller.unlockAdult("123456"))
        controller.close()
    }

    @Test
    fun lockingAdultContentPurgesCachedSearchResults() = runTest {
        val api = FakeCatalogApi().apply { searchAdult = true }
        val controller = AppController(
            MemoryStore(),
            apiFactory = { api },
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )
        advanceUntilIdle()
        assertTrue(controller.configureAdultPin("123456", "123456", ageConfirmed = true))
        controller.updateSearchQuery("restricted")
        advanceUntilIdle()
        assertTrue(assertIs<LoadState.Content<List<TitleSummary>>>(controller.state.value.search).value.single().adult)

        controller.lockAdult()

        assertTrue(assertIs<LoadState.Content<List<TitleSummary>>>(controller.state.value.search).value.isEmpty())
        assertTrue(assertIs<LoadState.Content<List<CatalogEntity>>>(controller.state.value.entitySearch).value.isEmpty())
        controller.close()
    }

    @Test
    fun lockedAccountPaginationRefiltersPreviouslyVisibleAdultTitles() {
        val adult = titleSummary(2, adult = true)
        val safe = titleSummary(1)
        val next = titleSummary(3)

        val recommendations = mergeAccountRecommendations(
            existing = listOf(safe, adult),
            page = PagedResult(page = 2, totalPages = 2, results = listOf(next)),
            includeAdult = false,
        )
        val list = mergeAccountListPage(
            existing = UserList(id = 7, name = "Mixed", page = 1, totalPages = 2, results = listOf(safe, adult)),
            page = UserList(id = 7, name = "Mixed", page = 2, totalPages = 2, results = listOf(next)),
            includeAdult = false,
        )

        assertEquals(listOf("movie:1", "movie:3"), recommendations.map(TitleSummary::libraryKey))
        assertEquals(listOf("movie:1", "movie:3"), list.results.map(TitleSummary::libraryKey))
    }

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
    fun advancedDiscoverNormalizesFiltersAndRefreshesRegionalConfiguration() = runTest {
        val api = FakeCatalogApi()
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val controller = AppController(MemoryStore(), apiFactory = { api }, scope = appScope)
        advanceUntilIdle()

        controller.setRegion("vn")
        controller.updateExploreFilter {
            it.copy(
                releaseDateFrom = " 2026-01-01 ",
                originalLanguage = " VI ",
                originCountry = " vn ",
                minimumRuntime = 180,
                maximumRuntime = 80,
                minimumVoteCount = -4,
            )
        }
        controller.toggleWatchProvider(8)
        controller.toggleMonetization(WatchMonetizationType.SUBSCRIPTION)
        controller.applyExploreFilters()
        advanceUntilIdle()

        val filter = api.discoverFilters.last()
        assertEquals("VN", filter.region)
        assertEquals("VN", filter.certificationCountry)
        assertEquals("vi", filter.originalLanguage)
        assertEquals("VN", filter.originCountry)
        assertEquals("2026-01-01", filter.releaseDateFrom)
        assertEquals(80, filter.minimumRuntime)
        assertEquals(180, filter.maximumRuntime)
        assertEquals(0, filter.minimumVoteCount)
        assertEquals(setOf(8), filter.watchProviderIds)
        assertEquals(setOf(WatchMonetizationType.SUBSCRIPTION), filter.monetizationTypes)
        assertEquals("VN", api.configurationRequests.last().second)
        controller.close()
    }

    @Test
    fun advancedDiscoverKeepsDraftOutOfPaginationAndUsesDeviceRegion() = runTest {
        val api = FakeCatalogApi()
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val controller = AppController(
            MemoryStore(),
            apiFactory = { api },
            scope = appScope,
            deviceRegion = "CA",
        )
        advanceUntilIdle()

        assertEquals("CA", controller.contentRegion())
        assertEquals("CA", controller.state.value.exploreFilter.region)
        assertEquals("CA", controller.state.value.exploreFilter.certificationCountry)
        assertEquals("CA", api.configurationRequests.last().second)
        controller.updateExploreFilter { it.copy(watchProviderIds = setOf(8)) }

        assertEquals(emptySet(), controller.state.value.exploreFilter.watchProviderIds)
        assertEquals(setOf(8), controller.state.value.exploreDraftFilter.watchProviderIds)
        assertEquals(emptySet(), api.discoverFilters.last().watchProviderIds)
        controller.setRegion("vn")
        assertEquals("VN", controller.contentRegion())
        controller.close()
    }

    @Test
    fun capabilitiesNilFalseAndTrueGateAdvancedDiscoverRoutes() = runTest {
        val unavailableApi = FakeCatalogApi().apply { capabilitiesResult = { error("unavailable") } }
        val disabledApi = FakeCatalogApi().apply { capabilitiesResult = { capabilities(false) } }
        val enabledApi = FakeCatalogApi().apply { capabilitiesResult = { capabilities(true) } }
        val unavailable = AppController(
            MemoryStore(),
            apiFactory = { unavailableApi },
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )
        val disabled = AppController(
            MemoryStore(),
            apiFactory = { disabledApi },
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )
        val enabled = AppController(
            MemoryStore(),
            apiFactory = { enabledApi },
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )

        advanceUntilIdle()

        assertEquals(null, unavailable.state.value.capabilities)
        assertEquals(0, unavailableApi.discoverFilters.size)
        assertEquals(false, disabled.state.value.capabilities?.supportsCatalog("advanced_discover"))
        assertEquals(0, disabledApi.discoverFilters.size)
        assertEquals(true, enabled.state.value.capabilities?.supportsCatalog("advanced_discover"))
        assertEquals(true, enabledApi.discoverFilters.isNotEmpty())
        assertEquals(true, unavailableApi.basicDiscoverFilters.isNotEmpty())
        assertEquals(true, disabledApi.basicDiscoverFilters.isNotEmpty())
        unavailable.close()
        disabled.close()
        enabled.close()
    }

    @Test
    fun accountAuthenticationCapabilitiesFailClosed() {
        val unavailable: CapabilitiesV2? = null
        val disabled = capabilities(advancedDiscover = true, browserAuth = false)
        val enabled = capabilities(advancedDiscover = true, browserAuth = true)

        assertEquals(false, unavailable.supportsAccountAuthentication())
        assertEquals(false, disabled.supportsAccountAuthentication())
        assertEquals(true, enabled.supportsAccountAuthentication())
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
        assertEquals(listOf(false), api.externalIdAdultFlags)
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
        assertEquals(listOf(false), api.creditAdultFlags)
        val result = assertIs<LoadState.Content<EntityDetail>>(controller.state.value.entityDetail)
        val credit = assertIs<EntityDetail.Credit>(result.value).value
        assertEquals(6384, credit.personSummary?.id)
        assertEquals("movie:603", credit.titleSummary?.libraryKey)
        controller.close()
    }

    @Test
    fun creditDetailForwardsAdultGateAndFailsClosedLocally() = runTest {
        val api = FakeCatalogApi().apply { creditAdultTitle = true }
        val controller = AppController(
            MemoryStore(),
            apiFactory = { api },
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )
        advanceUntilIdle()

        controller.openCredit(Credit(creditId = "restricted-credit"))
        advanceUntilIdle()
        val locked = assertIs<EntityDetail.Credit>(
            assertIs<LoadState.Content<EntityDetail>>(controller.state.value.entityDetail).value,
        )
        assertEquals(null, locked.value.titleSummary)
        assertEquals(false, api.creditAdultFlags.last())

        assertTrue(controller.configureAdultPin("123456", "123456", ageConfirmed = true))
        controller.openCredit(Credit(creditId = "restricted-credit"))
        advanceUntilIdle()
        val unlocked = assertIs<EntityDetail.Credit>(
            assertIs<LoadState.Content<EntityDetail>>(controller.state.value.entityDetail).value,
        )
        assertEquals(true, unlocked.value.titleSummary?.adult)
        assertEquals(true, api.creditAdultFlags.last())
        controller.close()
    }
}

private class FakeCatalogApi : CatalogApiV2 {
    val searchQueries = mutableListOf<String>()
    val discoverFilters = mutableListOf<DiscoverFilter>()
    val basicDiscoverFilters = mutableListOf<DiscoverFilter>()
    val externalIdCalls = mutableListOf<Triple<String, ExternalIdSource, String>>()
    val externalIdAdultFlags = mutableListOf<Boolean>()
    val creditCalls = mutableListOf<Pair<String, String>>()
    val creditAdultFlags = mutableListOf<Boolean>()
    val configurationRequests = mutableListOf<Pair<String, String?>>()
    var capabilitiesResult: suspend () -> CapabilitiesV2 = { capabilities(true) }
    var searchAdult = false
    var creditAdultTitle = false

    override suspend fun home(mediaType: MediaType, language: String) = HomeFeed(mediaType)
    override suspend fun genres(mediaType: MediaType, language: String): List<Genre> = emptyList()
    override suspend fun discover(mediaType: MediaType, filter: DiscoverFilter, page: Int, language: String): PagedResult<TitleSummary> {
        discoverFilters += filter
        return PagedResult(page, 1, emptyList())
    }

    override suspend fun discoverBasic(
        mediaType: MediaType,
        filter: DiscoverFilter,
        page: Int,
        language: String,
    ): PagedResult<TitleSummary> {
        basicDiscoverFilters += filter
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

    override suspend fun capabilities(): CapabilitiesV2 = capabilitiesResult()
    override suspend fun discoverConfiguration(language: String, region: String?): DiscoverConfiguration {
        configurationRequests += language to region
        return DiscoverConfiguration(region = region)
    }
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
            listOf(CatalogEntity.Title(TitleSummary(1, MediaType.MOVIE, query, query, "Result", adult = searchAdult))),
        )
    }

    override suspend fun findExternalId(
        externalId: String,
        source: ExternalIdSource,
        language: String,
        includeAdult: Boolean,
    ): ExternalIdFindResult {
        externalIdCalls += Triple(externalId, source, language)
        externalIdAdultFlags += includeAdult
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
    override suspend fun person(id: Int, language: String, includeAdult: Boolean): PersonDetail = error("Not used")
    override suspend fun collection(id: Int, language: String, includeAdult: Boolean): CollectionDetail = error("Not used")
    override suspend fun organization(
        kind: EntityKind,
        id: Int,
        language: String,
        page: Int,
        includeAdult: Boolean,
    ): OrganizationDetail = error("Not used")
    override suspend fun keyword(id: Int, language: String, page: Int, includeAdult: Boolean): KeywordDetail = error("Not used")
    override suspend fun season(seriesId: Int, number: Int, language: String): SeasonDetail = error("Not used")
    override suspend fun episode(seriesId: Int, season: Int, number: Int, language: String): EpisodeDetail = error("Not used")
    override suspend fun credit(id: String, language: String, includeAdult: Boolean): CreditDetail {
        creditCalls += id to language
        creditAdultFlags += includeAdult
        return CreditDetail(
            creditId = id,
            creditType = "cast",
            department = "Acting",
            job = "Actor",
            character = "Neo",
            personSummary = PersonSummary(6384, "Keanu Reeves"),
            titleSummary = TitleSummary(603, MediaType.MOVIE, "The Matrix", "The Matrix", "", adult = creditAdultTitle),
        )
    }
}

private fun capabilities(
    advancedDiscover: Boolean,
    browserAuth: Boolean = false,
) = CapabilitiesV2(
    apiVersion = "v2",
    releaseTrain = "3.0.0",
    catalog = mapOf("advanced_discover" to advancedDiscover),
    account = mapOf("browser_auth" to browserAuth),
    supportedLanguages = emptyList(),
    supportedEntityKinds = emptyList(),
    adultContent = AdultContentCapability(false, false, true),
)

private fun titleSummary(id: Int, adult: Boolean = false) = TitleSummary(
    id = id,
    mediaType = MediaType.MOVIE,
    title = "Title $id",
    originalTitle = "Title $id",
    overview = "",
    adult = adult,
)
