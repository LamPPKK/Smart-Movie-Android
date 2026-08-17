package com.lamndt.smartmovie.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test

class ContractConformanceTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun canonicalSuccessFixturesDecodeIntoNativeModels() {
        val home = decode<HomeFeed>("home")
        assertThat(home.hero?.libraryKey).isEqualTo("movie:42")

        val page = decode<PagedResult<TitleSummary>>("title-page")
        assertThat(page.results.single().libraryKey).isEqualTo("movie:42")

        val genres = decode<GenreEnvelope>("genres")
        assertThat(genres.genres.map(Genre::id)).containsExactly(12, 18).inOrder()

        val detail = decode<TitleDetail>("title-detail")
        assertThat(detail.cast.single().character).isEqualTo("Lead")
        assertThat(detail.videos.single().official).isTrue()

        val configuration = decode<ImageConfiguration>("configuration")
        assertThat(configuration.posterSizes).containsExactly("w342", "w500", "original").inOrder()
    }

    @Test
    fun additiveFieldsAndMissingNullableFieldsRemainCompatible() {
        val summary = decode<TitleSummary>("title-summary-forward-compatible")

        assertThat(summary.libraryKey).isEqualTo("tv:99")
        assertThat(summary.posterPath).isNull()
        assertThat(summary.backdropPath).isNull()
        assertThat(summary.releaseDate).isNull()
    }

    private inline fun <reified Value> decode(fixture: String): Value {
        val resource = checkNotNull(javaClass.classLoader.getResource("$fixture.json")) { "Missing fixture $fixture" }
        return json.decodeFromString(resource.readText())
    }
}

@Serializable
private data class GenreEnvelope(val genres: List<Genre>)
