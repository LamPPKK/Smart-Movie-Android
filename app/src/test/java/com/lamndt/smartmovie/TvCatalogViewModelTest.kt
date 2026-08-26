package com.lamndt.smartmovie

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.DiscoverConfiguration
import com.lamndt.smartmovie.model.DiscoverSort
import com.lamndt.smartmovie.model.Loadable
import com.lamndt.smartmovie.model.WatchMonetizationType
import com.lamndt.smartmovie.model.WatchProviderOption
import com.lamndt.smartmovie.model.WatchProviderOptions
import com.lamndt.smartmovie.testing.FakeCatalogV2Repository
import com.lamndt.smartmovie.testing.FakeCatalogRepository
import com.lamndt.smartmovie.testing.FakeLibraryRepository
import com.lamndt.smartmovie.testing.capabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvCatalogViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun applyNormalizesAdvancedFilterAndPublishesItToDiscover() = runTest(dispatcher) {
        val catalog = FakeCatalogV2Repository().apply {
            configurationResult = { _, region ->
                DiscoverConfiguration(
                    region = region,
                    watchProviders = WatchProviderOptions(
                        movie = listOf(WatchProviderOption(8, "Netflix")),
                    ),
                )
            }
        }
        val viewModel = TvCatalogViewModel(catalog, FakeLibraryRepository(), "vi-VN")
        viewModel.updateContext("vn", includeAdult = true)
        advanceUntilIdle()

        viewModel.showExploreFilters()
        viewModel.updateExploreFilter {
            it.copy(
                sort = DiscoverSort.RATING,
                releaseDateFrom = " 2026-01-01 ",
                originalLanguage = " VI ",
                originCountry = " vn ",
                minimumRuntime = 180,
                maximumRuntime = 80,
                minimumVoteCount = -5,
            )
        }
        viewModel.toggleWatchProvider(8)
        viewModel.toggleMonetization(WatchMonetizationType.SUBSCRIPTION)

        assertThat(viewModel.state.value.exploreFilter.watchProviderIds).isEmpty()
        viewModel.applyExploreFilter()
        advanceUntilIdle()

        val filter = catalog.legacy.discoverCalls.last().second
        assertThat(filter.region).isEqualTo("VN")
        assertThat(filter.certificationCountry).isEqualTo("VN")
        assertThat(filter.includeAdult).isTrue()
        assertThat(filter.releaseDateFrom).isEqualTo("2026-01-01")
        assertThat(filter.originalLanguage).isEqualTo("vi")
        assertThat(filter.originCountry).isEqualTo("VN")
        assertThat(filter.minimumRuntime).isEqualTo(80)
        assertThat(filter.maximumRuntime).isEqualTo(180)
        assertThat(filter.minimumVoteCount).isEqualTo(0)
        assertThat(filter.watchProviderIds).containsExactly(8)
        assertThat(filter.monetizationTypes).containsExactly(WatchMonetizationType.SUBSCRIPTION)
        assertThat(viewModel.state.value.showExploreFilters).isFalse()
        assertThat(catalog.configurationCalls).contains("vi-VN" to "VN")
    }

    @Test
    fun capabilitiesNilFalseAndTrueGateTvAdvancedDiscover() = runTest(dispatcher) {
        val unavailableCatalog = FakeCatalogRepository()
        val disabledCatalog = FakeCatalogV2Repository().apply {
            capabilitiesResult = { capabilities(advancedDiscover = false) }
        }
        val enabledCatalog = FakeCatalogV2Repository().apply {
            capabilitiesResult = { capabilities(advancedDiscover = true) }
        }
        val unavailable = TvCatalogViewModel(unavailableCatalog, FakeLibraryRepository(), "en-US")
        val disabled = TvCatalogViewModel(disabledCatalog, FakeLibraryRepository(), "en-US")
        val enabled = TvCatalogViewModel(enabledCatalog, FakeLibraryRepository(), "en-US")

        advanceUntilIdle()

        assertThat(unavailable.state.value.advancedDiscoverEnabled).isFalse()
        assertThat(unavailableCatalog.basicDiscoverCalls).isNotEmpty()
        assertThat(disabled.state.value.advancedDiscoverEnabled).isFalse()
        assertThat(disabledCatalog.configurationCalls).isEmpty()
        assertThat(enabled.state.value.advancedDiscoverEnabled).isTrue()
        assertThat(enabledCatalog.configurationCalls).isNotEmpty()
    }

    @Test
    fun dismissDiscardsDraftAndRegionChangeClearsProviderSelection() = runTest(dispatcher) {
        val catalog = FakeCatalogV2Repository()
        val viewModel = TvCatalogViewModel(catalog, FakeLibraryRepository(), "en-US")
        viewModel.updateContext("US", includeAdult = false)
        advanceUntilIdle()

        viewModel.showExploreFilters()
        viewModel.toggleWatchProvider(8)
        viewModel.dismissExploreFilters()

        assertThat(viewModel.state.value.exploreDraft).isEqualTo(viewModel.state.value.exploreFilter)
        assertThat(viewModel.state.value.exploreFilter.watchProviderIds).isEmpty()

        viewModel.showExploreFilters()
        viewModel.updateExploreFilter { it.copy(certificationMinimum = "PG-13", certificationMaximum = "R") }
        viewModel.toggleWatchProvider(8)
        viewModel.applyExploreFilter()
        advanceUntilIdle()
        viewModel.updateContext("CA", includeAdult = false)
        advanceUntilIdle()

        assertThat(viewModel.state.value.exploreFilter.region).isEqualTo("CA")
        assertThat(viewModel.state.value.exploreFilter.watchProviderIds).isEmpty()
        assertThat(viewModel.state.value.exploreDraft.watchProviderIds).isEmpty()
        assertThat(viewModel.state.value.exploreFilter.certificationMinimum).isNull()
        assertThat(viewModel.state.value.exploreFilter.certificationMaximum).isNull()
    }

    @Test
    fun failedOptionsRefreshKeepsLastGoodConfiguration() = runTest(dispatcher) {
        val catalog = FakeCatalogV2Repository().apply {
            configurationResult = { _, region ->
                DiscoverConfiguration(
                    region = region,
                    watchProviders = WatchProviderOptions(
                        movie = listOf(WatchProviderOption(8, "Netflix")),
                    ),
                )
            }
        }
        val viewModel = TvCatalogViewModel(catalog, FakeLibraryRepository(), "en-US")
        viewModel.updateContext("US", includeAdult = false)
        advanceUntilIdle()
        assertThat(viewModel.state.value.discoverConfiguration?.watchProviders?.movie?.single()?.id).isEqualTo(8)

        catalog.configurationResult = { _, _ -> error("temporary configuration failure") }
        viewModel.refreshExplore()
        advanceUntilIdle()

        assertThat(viewModel.state.value.discoverConfiguration?.watchProviders?.movie?.single()?.id).isEqualTo(8)
    }

    @Test
    fun staleExploreFailureCannotReplaceNewRegionResults() = runTest(dispatcher) {
        val catalog = FakeCatalogV2Repository().apply {
            legacy.discoverResult = { _, filter, page ->
                if (filter.region == "US") {
                    try {
                        delay(200)
                    } catch (_: CancellationException) {
                        // Simulate an upstream request that fails after cancellation.
                    }
                    error("stale US failure")
                }
                com.lamndt.smartmovie.model.PagedResult(page, page, emptyList())
            }
        }
        val viewModel = TvCatalogViewModel(catalog, FakeLibraryRepository(), "en-US")
        viewModel.updateContext("US", includeAdult = false)
        runCurrent()

        viewModel.updateContext("VN", includeAdult = false)
        advanceUntilIdle()

        assertThat(viewModel.state.value.exploreFilter.region).isEqualTo("VN")
        assertThat(viewModel.state.value.explore).isInstanceOf(Loadable.Loaded::class.java)
    }
}
