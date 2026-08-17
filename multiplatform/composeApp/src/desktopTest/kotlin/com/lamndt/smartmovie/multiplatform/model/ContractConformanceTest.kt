package com.lamndt.smartmovie.multiplatform.model

import com.lamndt.smartmovie.multiplatform.data.ErrorEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContractConformanceTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun canonicalSuccessFixturesDecodeIntoMultiplatformModels() {
        val home = decode<HomeFeed>("home")
        assertEquals("movie:42", home.hero?.libraryKey)

        val page = decode<PagedResult<TitleSummary>>("title-page")
        assertEquals("movie:42", page.results.single().libraryKey)

        assertEquals(listOf(12, 18), decode<GenreEnvelope>("genres").genres.map(Genre::id))

        val detail = decode<TitleDetail>("title-detail")
        assertEquals("Lead", detail.cast.single().character)
        assertTrue(detail.videos.single().official)

        assertEquals(listOf("w342", "w500", "original"), decode<ImageConfiguration>("configuration").posterSizes)
    }

    @Test
    fun errorAndForwardCompatibilityFixturesDecode() {
        val error = decode<ErrorEnvelope>("error").error
        assertEquals("rate_limited", error.code)
        assertEquals(60L, error.retryAfter)

        val summary = decode<TitleSummary>("title-summary-forward-compatible")
        assertEquals("tv:99", summary.libraryKey)
        assertNull(summary.posterPath)
        assertNull(summary.backdropPath)
        assertNull(summary.releaseDate)
    }

    private inline fun <reified Value> decode(fixture: String): Value {
        val resource = checkNotNull(javaClass.classLoader.getResource("$fixture.json")) { "Missing fixture $fixture" }
        return json.decodeFromString(resource.readText())
    }
}

@Serializable
private data class GenreEnvelope(val genres: List<Genre>)
