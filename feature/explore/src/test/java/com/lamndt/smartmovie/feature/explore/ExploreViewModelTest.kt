package com.lamndt.smartmovie.feature.explore

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.DiscoverSort
import com.lamndt.smartmovie.model.DiscoverConfiguration
import com.lamndt.smartmovie.model.Genre
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.WatchProviderOption
import com.lamndt.smartmovie.model.WatchProviderOptions
import com.lamndt.smartmovie.testing.FakeCatalogRepository
import com.lamndt.smartmovie.testing.FakeCatalogV2Repository
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
class ExploreViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun resetAndApplyKeepsDraftSeparateFromPublishedFilter() = runTest(dispatcher) {
        val viewModel = ExploreViewModel(FakeCatalogRepository(), "en-US")
        advanceUntilIdle()
        viewModel.showFilters()
        viewModel.updateDraft { it.copy(genres = setOf(18, 878), year = 2026, minimumRating = 7.5, sort = DiscoverSort.RATING) }

        assertThat(viewModel.state.value.appliedFilter.genres).isEmpty()
        viewModel.applyFilters()

        assertThat(viewModel.state.value.appliedFilter.genres).containsExactly(18, 878)
        assertThat(viewModel.state.value.appliedFilter.minimumRating).isEqualTo(7.5)
        assertThat(viewModel.state.value.showFilters).isFalse()
    }

    @Test
    fun contextLoadsRegionalProvidersAndClearsStaleSelections() = runTest(dispatcher) {
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
        val viewModel = ExploreViewModel(catalog, "vi-VN")
        viewModel.updateContext("vn", includeAdult = true)
        advanceUntilIdle()

        assertThat(viewModel.state.value.appliedFilter.region).isEqualTo("VN")
        assertThat(viewModel.state.value.appliedFilter.includeAdult).isTrue()
        assertThat(viewModel.state.value.configuration?.watchProviders?.movie?.single()?.id).isEqualTo(8)

        viewModel.updateDraft {
            it.copy(
                watchProviderIds = setOf(8),
                certificationMinimum = "PG-13",
                certificationMaximum = "R",
            )
        }
        viewModel.applyFilters()
        viewModel.updateContext("us", includeAdult = false)
        advanceUntilIdle()

        assertThat(viewModel.state.value.appliedFilter.watchProviderIds).isEmpty()
        assertThat(viewModel.state.value.appliedFilter.certificationCountry).isEqualTo("US")
        assertThat(viewModel.state.value.appliedFilter.certificationMinimum).isNull()
        assertThat(viewModel.state.value.appliedFilter.certificationMaximum).isNull()
        viewModel.resetDraft()
        assertThat(viewModel.state.value.draftFilter.region).isEqualTo("US")
        assertThat(viewModel.state.value.draftFilter.includeAdult).isFalse()
    }

    @Test
    fun applyNormalizesCodesWhitespaceAndRuntimeBounds() = runTest(dispatcher) {
        val viewModel = ExploreViewModel(FakeCatalogV2Repository(), "en-US")
        advanceUntilIdle()
        viewModel.showFilters()
        viewModel.updateDraft {
            it.copy(
                originalLanguage = " VI ",
                originCountry = " vn ",
                releaseDateFrom = " 2026-01-01 ",
                minimumRuntime = 180,
                maximumRuntime = 80,
                minimumVoteCount = -5,
            )
        }

        viewModel.applyFilters()

        val filter = viewModel.state.value.appliedFilter
        assertThat(filter.originalLanguage).isEqualTo("vi")
        assertThat(filter.originCountry).isEqualTo("VN")
        assertThat(filter.releaseDateFrom).isEqualTo("2026-01-01")
        assertThat(filter.minimumRuntime).isEqualTo(80)
        assertThat(filter.maximumRuntime).isEqualTo(180)
        assertThat(filter.minimumVoteCount).isEqualTo(0)
    }

    @Test
    fun capabilitiesNilFalseAndTrueKeepAdvancedDiscoverFailClosed() = runTest(dispatcher) {
        val unavailable = ExploreViewModel(FakeCatalogRepository(), "en-US")
        val disabledCatalog = FakeCatalogV2Repository().apply {
            capabilitiesResult = { capabilities(advancedDiscover = false) }
        }
        val disabled = ExploreViewModel(disabledCatalog, "en-US")
        val enabledCatalog = FakeCatalogV2Repository().apply {
            capabilitiesResult = { capabilities(advancedDiscover = true) }
        }
        val enabled = ExploreViewModel(enabledCatalog, "en-US")

        advanceUntilIdle()

        assertThat(unavailable.state.value.advancedDiscoverEnabled).isFalse()
        assertThat(disabled.state.value.advancedDiscoverEnabled).isFalse()
        assertThat(disabledCatalog.configurationCalls).isEmpty()
        assertThat(enabled.state.value.advancedDiscoverEnabled).isTrue()
        assertThat(enabledCatalog.configurationCalls).isNotEmpty()
    }

    @Test
    fun mediaTypeChangeRejectsStaleGenres() = runTest(dispatcher) {
        val catalog = FakeCatalogRepository().apply {
            genresResult = { type ->
                if (type == MediaType.MOVIE) {
                    try {
                        delay(200)
                    } catch (_: CancellationException) {
                        // Simulate an upstream request that still completes after cancellation.
                    }
                    listOf(Genre(1, "Stale movie"))
                } else {
                    listOf(Genre(2, "Current TV"))
                }
            }
        }
        val viewModel = ExploreViewModel(catalog, "en-US")
        runCurrent()

        viewModel.selectMediaType(MediaType.TV)
        advanceUntilIdle()

        assertThat(viewModel.state.value.genres.map(Genre::name)).containsExactly("Current TV")
    }
}
