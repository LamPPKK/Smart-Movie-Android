package com.lamndt.smartmovie.feature.detail

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.Review
import com.lamndt.smartmovie.model.TitleSummary
import org.junit.Test

class CatalogEditorialPresentationTest {
    @Test
    fun `reviews remove blank content and duplicate ids without reordering`() {
        val values = listOf(
            review("first", "First review"),
            review("blank", " "),
            review("first", "Duplicate"),
            review("second", "Second review"),
            review("third", "Third review"),
            review("fourth", "Fourth review"),
            review("fifth", "Fifth review"),
        )

        assertThat(presentedReviews(values).map(Review::id))
            .containsExactly("first", "second", "third", "fourth")
            .inOrder()
    }

    @Test
    fun `titles exclude current title and deduplicate library keys`() {
        val values = listOf(
            title(10, MediaType.MOVIE),
            title(20, MediaType.MOVIE),
            title(20, MediaType.MOVIE),
            title(20, MediaType.TV),
            title(30, MediaType.MOVIE, adult = true),
        )

        assertThat(presentedEditorialTitles(values, "movie:10").map(TitleSummary::libraryKey))
            .containsExactly("movie:20", "tv:20")
            .inOrder()
        assertThat(presentedEditorialTitles(values, "movie:10", includeAdult = true).map(TitleSummary::libraryKey))
            .containsExactly("movie:20", "tv:20", "movie:30")
            .inOrder()
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
