package com.lamndt.smartmovie.feature.detail

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.testing.FakeCatalogRepository
import com.lamndt.smartmovie.testing.FakeLibraryRepository
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
class DetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun togglesFavoriteAndWatchlistIndependently() = runTest(dispatcher) {
        val library = FakeLibraryRepository()
        val title = TitleSummary(42, MediaType.MOVIE, "Dune", "Dune", "")
        val viewModel = DetailViewModel(title, FakeCatalogRepository(), library, "en-US")
        advanceUntilIdle()

        viewModel.toggle(LibraryCollection.FAVORITES)
        viewModel.toggle(LibraryCollection.WATCHLIST)
        advanceUntilIdle()

        assertThat(library.toggles).containsExactly(
            title.libraryKey to LibraryCollection.FAVORITES,
            title.libraryKey to LibraryCollection.WATCHLIST,
        ).inOrder()
        assertThat(viewModel.state.value.membership.isFavorite).isTrue()
        assertThat(viewModel.state.value.membership.isWatchlisted).isTrue()
    }
}
