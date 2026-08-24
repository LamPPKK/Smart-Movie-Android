package com.lamndt.smartmovie.multiplatform

import com.lamndt.smartmovie.multiplatform.model.MediaType
import com.lamndt.smartmovie.multiplatform.model.PagedResult
import com.lamndt.smartmovie.multiplatform.model.TitleSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountRecommendationsTest {
    @Test
    fun paginationFiltersAdultTitlesAndDeduplicates() {
        val safe = title(1)
        val restricted = title(2, adult = true)
        val first = mergeAccountRecommendations(
            emptyList(),
            PagedResult(page = 1, totalPages = 2, results = listOf(safe, restricted)),
            includeAdult = false,
        )
        val second = mergeAccountRecommendations(
            first,
            PagedResult(page = 2, totalPages = 2, results = listOf(safe, title(3))),
            includeAdult = false,
        )

        assertEquals(listOf("movie:1", "movie:3"), second.map(TitleSummary::libraryKey))
    }

    @Test
    fun unlockedAdultContentIsRetained() {
        val result = mergeAccountRecommendations(
            emptyList(),
            PagedResult(page = 1, totalPages = 1, results = listOf(title(2, adult = true))),
            includeAdult = true,
        )

        assertTrue(result.single().adult)
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
