package com.lamndt.smartmovie.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.database.SmartMovieDatabase
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibrarySort
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.TitleSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultLibraryRepositoryTest {
    private lateinit var database: SmartMovieDatabase
    private var clock = 1_000L
    private lateinit var repository: DefaultLibraryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SmartMovieDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = DefaultLibraryRepository(database) { clock++ }
    }

    @After fun tearDown() = database.close()

    @Test
    fun favoriteAndWatchlist_areIndependentAndPersistSingleSnapshot() = runTest {
        repository.toggle(DUNE, LibraryCollection.FAVORITES)
        repository.toggle(DUNE, LibraryCollection.WATCHLIST)

        val membership = repository.observeMembership(DUNE.libraryKey).first()
        val favorites = repository.observeItems(LibraryCollection.FAVORITES, null, LibrarySort.RECENTLY_ADDED).first()
        val watchlist = repository.observeItems(LibraryCollection.WATCHLIST, null, LibrarySort.RECENTLY_ADDED).first()

        assertThat(membership.isFavorite).isTrue()
        assertThat(membership.isWatchlisted).isTrue()
        assertThat(favorites).hasSize(1)
        assertThat(watchlist).hasSize(1)
        assertThat(favorites.single().favoritedAt).isEqualTo(1_000L)
        assertThat(watchlist.single().watchlistedAt).isEqualTo(1_001L)
    }

    @Test
    fun togglingFavoriteOff_doesNotRemoveWatchlist() = runTest {
        repository.toggle(DUNE, LibraryCollection.FAVORITES)
        repository.toggle(DUNE, LibraryCollection.WATCHLIST)
        repository.toggle(DUNE, LibraryCollection.FAVORITES)

        val membership = repository.observeMembership(DUNE.libraryKey).first()
        assertThat(membership.isFavorite).isFalse()
        assertThat(membership.isWatchlisted).isTrue()
        assertThat(repository.observeItems(LibraryCollection.WATCHLIST, MediaType.MOVIE, LibrarySort.TITLE).first()).hasSize(1)
    }

    @Test
    fun filtersByMediaTypeAndSortsByTitle() = runTest {
        repository.toggle(DUNE.copy(id = 2, title = "Zulu"), LibraryCollection.FAVORITES)
        repository.toggle(DUNE.copy(id = 3, mediaType = MediaType.TV, title = "Alpha"), LibraryCollection.FAVORITES)

        val movies = repository.observeItems(LibraryCollection.FAVORITES, MediaType.MOVIE, LibrarySort.TITLE).first()
        assertThat(movies.map { it.title.displayTitle }).containsExactly("Zulu")
    }

    private companion object {
        val DUNE = TitleSummary(1, MediaType.MOVIE, "Dune", "Dune", "Desert", releaseDate = "2024-03-01", voteAverage = 8.4)
    }
}
