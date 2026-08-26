package com.lamndt.smartmovie

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.PagedResult
import com.lamndt.smartmovie.model.TitleSummary
import org.junit.Test

class ProfileRecommendationsTest {
    @Test
    fun paginationFiltersAdultTitlesAndDeduplicatesByLibraryKey() {
        val safe = title(1)
        val restricted = title(2, adult = true)
        val first = recommendationsFromPage(
            existing = emptyList(),
            page = PagedResult(page = 1, totalPages = 2, results = listOf(safe, restricted)),
            includeAdult = false,
        )
        val second = recommendationsFromPage(
            existing = first.items,
            page = PagedResult(page = 2, totalPages = 2, results = listOf(safe, title(3))),
            includeAdult = false,
        )

        assertThat(first.items.map(TitleSummary::libraryKey)).containsExactly("movie:1")
        assertThat(second.items.map(TitleSummary::libraryKey)).containsExactly("movie:1", "movie:3").inOrder()
        assertThat(second.page).isEqualTo(2)
    }

    @Test
    fun unlockedAdultContentIsRetained() {
        val result = recommendationsFromPage(
            existing = emptyList(),
            page = PagedResult(page = 1, totalPages = 1, results = listOf(title(2, adult = true))),
            includeAdult = true,
        )

        assertThat(result.items.single().adult).isTrue()
    }

    @Test
    fun lockingBeforePaginationCompletesPurgesPreviouslyVisibleAdultTitles() {
        val result = recommendationsFromPage(
            existing = listOf(title(1), title(2, adult = true)),
            page = PagedResult(page = 2, totalPages = 2, results = listOf(title(3))),
            includeAdult = false,
        )

        assertThat(result.items.map(TitleSummary::libraryKey)).containsExactly("movie:1", "movie:3").inOrder()
    }

    private fun title(id: Int, adult: Boolean = false) = TitleSummary(
        id = id,
        mediaType = MediaType.MOVIE,
        title = "Title $id",
        originalTitle = "Title $id",
        overview = "",
        adult = adult,
    )
}
