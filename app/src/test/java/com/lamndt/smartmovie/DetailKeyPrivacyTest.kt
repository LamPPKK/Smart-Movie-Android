package com.lamndt.smartmovie

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class DetailKeyPrivacyTest {
    @Test
    fun legacyNavigationStateDefaultsToAdultUntilFreshMetadataLoads() {
        val key = Json.decodeFromString<DetailKey>(
            """{"id":550,"type":"movie","title":"Fight Club","originalTitle":"Fight Club","overview":"","posterPath":null,"backdropPath":null,"releaseDate":null,"rating":8.4}""",
        )

        assertThat(key.summary().adult).isTrue()
    }

    @Test
    fun currentNavigationStatePreservesKnownAdultFlag() {
        val key = DetailKey(
            id = 550,
            type = "movie",
            title = "Fight Club",
            originalTitle = "Fight Club",
            overview = "",
            posterPath = null,
            backdropPath = null,
            releaseDate = null,
            rating = 8.4,
            adult = false,
        )

        assertThat(key.summary().adult).isFalse()
    }
}
