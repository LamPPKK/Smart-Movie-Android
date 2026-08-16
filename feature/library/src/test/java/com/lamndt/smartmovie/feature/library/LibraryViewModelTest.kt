package com.lamndt.smartmovie.feature.library

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibraryMembership
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.model.LibrarySnapshot
import com.lamndt.smartmovie.model.LibrarySort
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.TitleSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun collectionMediaTypeAndSortDriveRepositoryQuery() = runTest(dispatcher) {
        val repository = RecordingLibraryRepository()
        val viewModel = LibraryViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.selectCollection(LibraryCollection.WATCHLIST)
        viewModel.selectMediaType(MediaType.TV)
        viewModel.selectSort(LibrarySort.TITLE)
        advanceUntilIdle()

        assertThat(viewModel.state.value.collection).isEqualTo(LibraryCollection.WATCHLIST)
        assertThat(viewModel.state.value.mediaType).isEqualTo(MediaType.TV)
        assertThat(viewModel.state.value.sort).isEqualTo(LibrarySort.TITLE)
        assertThat(repository.requests.last()).isEqualTo(
            Triple(LibraryCollection.WATCHLIST, MediaType.TV, LibrarySort.TITLE),
        )
        assertThat(viewModel.state.value.items.single().title.mediaType).isEqualTo(MediaType.TV)
    }
}

private class RecordingLibraryRepository : LibraryRepository {
    val requests = mutableListOf<Triple<LibraryCollection, MediaType?, LibrarySort>>()
    private val movie = snapshot(1, MediaType.MOVIE, "Dune")
    private val series = snapshot(2, MediaType.TV, "Shogun")
    private val items = MutableStateFlow(listOf(movie, series))

    override fun observeItems(
        collection: LibraryCollection,
        mediaType: MediaType?,
        sort: LibrarySort,
    ): Flow<List<LibrarySnapshot>> {
        requests += Triple(collection, mediaType, sort)
        return MutableStateFlow(
            items.value
                .filter { collection == LibraryCollection.FAVORITES && it.isFavorite || collection == LibraryCollection.WATCHLIST && it.isWatchlisted }
                .filter { mediaType == null || it.title.mediaType == mediaType },
        )
    }

    override fun observeMembership(libraryKey: String): Flow<LibraryMembership> = flowOf(LibraryMembership())
    override suspend fun toggle(title: TitleSummary, collection: LibraryCollection) = Unit
}

private fun snapshot(id: Int, type: MediaType, title: String) = LibrarySnapshot(
    id = "${type.wireValue}:$id",
    title = TitleSummary(id, type, title, title, ""),
    isFavorite = true,
    isWatchlisted = true,
    favoritedAt = id.toLong(),
    watchlistedAt = id.toLong(),
    updatedAt = id.toLong(),
)
