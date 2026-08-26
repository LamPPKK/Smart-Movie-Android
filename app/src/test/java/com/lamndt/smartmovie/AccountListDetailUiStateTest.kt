package com.lamndt.smartmovie

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.model.UserList
import org.junit.Test

class AccountListDetailUiStateTest {
    @Test
    fun mixedPagesFilterAdultAndDeduplicateByLibraryKey() {
        val movie = title(1, MediaType.MOVIE)
        val adult = title(2, MediaType.MOVIE, adult = true)
        val series = title(3, MediaType.TV)
        val first = mergeAccountListPage(
            existing = null,
            page = UserList(7, "Mixed", page = 1, totalPages = 2, results = listOf(movie, adult)),
            includeAdult = false,
        )

        val result = mergeAccountListPage(
            existing = first.copy(results = first.results + adult),
            page = UserList(7, "Mixed", page = 2, totalPages = 2, results = listOf(movie, series)),
            includeAdult = false,
        )

        assertThat(result.results.map(TitleSummary::libraryKey)).containsExactly("movie:1", "tv:3").inOrder()
        assertThat(result.page).isEqualTo(2)
        assertThat(result.totalPages).isEqualTo(2)
    }

    @Test
    fun searchResultsExcludeExistingAdultAndDuplicates() {
        val existing = title(1, MediaType.MOVIE)
        val series = title(3, MediaType.TV)

        val result = filterAccountListSearchResults(
            candidates = listOf(existing, title(2, MediaType.MOVIE, adult = true), series, series),
            existing = listOf(existing),
            includeAdult = false,
        )

        assertThat(result.map(TitleSummary::libraryKey)).containsExactly("tv:3")
    }

    private fun title(id: Int, type: MediaType, adult: Boolean = false) = TitleSummary(
        id = id,
        mediaType = type,
        title = "Title $id",
        originalTitle = "Title $id",
        overview = "",
        adult = adult,
    )
}
