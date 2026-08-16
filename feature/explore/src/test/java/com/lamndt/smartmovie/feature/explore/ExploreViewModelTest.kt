package com.lamndt.smartmovie.feature.explore

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.DiscoverSort
import com.lamndt.smartmovie.testing.FakeCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
}
