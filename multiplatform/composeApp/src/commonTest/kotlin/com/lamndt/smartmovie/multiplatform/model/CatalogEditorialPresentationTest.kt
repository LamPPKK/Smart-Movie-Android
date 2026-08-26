package com.lamndt.smartmovie.multiplatform.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogEditorialPresentationTest {
    @Test
    fun reviewsRemoveBlankContentAndDuplicateIdsWithoutReordering() {
        val values = listOf(
            review("first", "First review"),
            review("blank", " "),
            review("first", "Duplicate"),
            review("second", "Second review"),
            review("third", "Third review"),
            review("fourth", "Fourth review"),
            review("fifth", "Fifth review"),
        )

        assertEquals(
            listOf("first", "second", "third", "fourth"),
            presentedReviews(values).map(Review::id),
        )
    }

    @Test
    fun titlesExcludeCurrentTitleAndDeduplicateLibraryKeys() {
        val values = listOf(
            title(10, MediaType.MOVIE),
            title(20, MediaType.MOVIE),
            title(20, MediaType.MOVIE),
            title(20, MediaType.TV),
            title(30, MediaType.MOVIE, adult = true),
        )

        assertEquals(
            listOf("movie:20", "tv:20"),
            presentedEditorialTitles(values, "movie:10").map(TitleSummary::libraryKey),
        )
        assertEquals(
            listOf("movie:20", "tv:20", "movie:30"),
            presentedEditorialTitles(values, "movie:10", includeAdult = true).map(TitleSummary::libraryKey),
        )
    }

    private fun review(id: String, content: String) = Review(id, "Reviewer", content)

    private fun title(id: Int, type: MediaType, adult: Boolean = false) = TitleSummary(
        id = id,
        mediaType = type,
        title = "Title $id",
        originalTitle = "Title $id",
        overview = "",
        adult = adult,
    )
}
