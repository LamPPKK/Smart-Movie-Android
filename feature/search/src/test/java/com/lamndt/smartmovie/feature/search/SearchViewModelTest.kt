package com.lamndt.smartmovie.feature.search

import androidx.paging.testing.asSnapshot
import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.PagedResult
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.testing.FakeCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun waits350MillisecondsThenLoadsLatestQuery() = runTest(dispatcher) {
        val catalog = FakeCatalogRepository().apply {
            searchResult = { query, _, page ->
                PagedResult(page, page, listOf(TitleSummary(1, MediaType.MOVIE, query, query, "")))
            }
        }
        val viewModel = SearchViewModel(catalog, "en-US")
        viewModel.setQuery("Dune")
        val snapshot = async { viewModel.results.asSnapshot() }

        advanceTimeBy(349)
        assertThat(catalog.searchCalls).isEmpty()
        advanceTimeBy(1)
        advanceUntilIdle()

        assertThat(catalog.searchCalls).containsExactly("Dune")
        assertThat(snapshot.await().single().displayTitle).isEqualTo("Dune")
    }

    @Test
    fun changingQueryCancelsInFlightPagingRequest() = runTest(dispatcher) {
        var firstCancelled = false
        val catalog = FakeCatalogRepository().apply {
            searchResult = { query, _, page ->
                if (query == "Dune") {
                    try {
                        awaitCancellation()
                    } finally {
                        firstCancelled = true
                    }
                }
                PagedResult(page, page, listOf(TitleSummary(2, MediaType.MOVIE, query, query, "")))
            }
        }
        val viewModel = SearchViewModel(catalog, "en-US")
        val snapshot = async { viewModel.results.asSnapshot() }
        viewModel.setQuery("Dune")
        advanceTimeBy(350)
        viewModel.setQuery("Arrival")
        advanceTimeBy(350)
        advanceUntilIdle()

        assertThat(firstCancelled).isTrue()
        assertThat(catalog.searchCalls).containsExactly("Dune", "Arrival").inOrder()
        assertThat(snapshot.await().single().displayTitle).isEqualTo("Arrival")
    }
}
