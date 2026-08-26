package com.lamndt.smartmovie.data

import androidx.paging.PagingSource
import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.DiscoverFilter
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.PagedResult
import com.lamndt.smartmovie.model.SearchScope
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.testing.FakeCatalogRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CatalogPagingSourcesTest {
    @Test
    fun discoverDeduplicatesByMediaTypeAndTmdbIdAcrossPages() = runTest {
        val movie = title(7, MediaType.MOVIE)
        val series = title(7, MediaType.TV)
        val nextMovie = title(8, MediaType.MOVIE)
        val catalog = FakeCatalogRepository().apply {
            discoverResult = { _, _, page ->
                if (page == 1) PagedResult(1, 2, listOf(movie, movie, series))
                else PagedResult(2, 2, listOf(movie, nextMovie))
            }
        }
        val source = DiscoverPagingSource(catalog, MediaType.MOVIE, DiscoverFilter(), "en-US")

        val first = source.load(PagingSource.LoadParams.Refresh(null, 20, false)) as PagingSource.LoadResult.Page<Int, TitleSummary>
        val second = source.load(PagingSource.LoadParams.Append(2, 20, false)) as PagingSource.LoadResult.Page<Int, TitleSummary>

        assertThat(first.data.map(TitleSummary::libraryKey)).containsExactly("movie:7", "tv:7").inOrder()
        assertThat(second.data.map(TitleSummary::libraryKey)).containsExactly("movie:8")
        assertThat(catalog.basicDiscoverCalls).hasSize(2)
        assertThat(catalog.discoverCalls).isEmpty()
    }

    @Test
    fun discoverUsesAdvancedRouteOnlyWhenCapabilityEnablesIt() = runTest {
        val catalog = FakeCatalogRepository()
        val source = DiscoverPagingSource(
            catalog,
            MediaType.MOVIE,
            DiscoverFilter(releaseDateFrom = "2026-01-01"),
            "en-US",
            advancedDiscoverEnabled = true,
        )

        source.load(PagingSource.LoadParams.Refresh(null, 20, false))

        assertThat(catalog.discoverCalls).hasSize(1)
        assertThat(catalog.basicDiscoverCalls).isEmpty()
    }

    @Test
    fun searchDeduplicatesRepeatedResultsAcrossPages() = runTest {
        val firstTitle = title(12, MediaType.TV)
        val secondTitle = title(13, MediaType.TV)
        val catalog = FakeCatalogRepository().apply {
            searchResult = { _, _, page ->
                if (page == 1) PagedResult(1, 2, listOf(firstTitle))
                else PagedResult(2, 2, listOf(firstTitle, secondTitle))
            }
        }
        val source = SearchPagingSource(catalog, "story", SearchScope.TV, "en-US")

        source.load(PagingSource.LoadParams.Refresh(null, 20, false))
        val second = source.load(PagingSource.LoadParams.Append(2, 20, false)) as PagingSource.LoadResult.Page<Int, TitleSummary>

        assertThat(second.data.map(TitleSummary::libraryKey)).containsExactly("tv:13")
    }

    private fun title(id: Int, type: MediaType) = TitleSummary(id, type, "Title $id", "Title $id", "")
}
