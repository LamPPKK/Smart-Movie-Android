package com.lamndt.smartmovie.data

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.ImageConfiguration
import com.lamndt.smartmovie.model.ImageKind
import org.junit.Test

class ImageUrlFactoryTest {
    private val configuration = ImageConfiguration(
        secureBaseUrl = "https://image.tmdb.org/t/p/",
        posterSizes = listOf("w342", "w500", "original"),
        backdropSizes = listOf("w780", "w1280", "original"),
        profileSizes = listOf("w185", "h632", "original"),
    )
    private val factory = ImageUrlFactory { configuration }

    @Test
    fun urlNormalizesBaseAndImagePath() {
        assertThat(factory.url("/poster.jpg", ImageKind.POSTER))
            .isEqualTo("https://image.tmdb.org/t/p/w500/poster.jpg")
        assertThat(factory.url(" backdrop.jpg ", ImageKind.BACKDROP))
            .isEqualTo("https://image.tmdb.org/t/p/w1280/backdrop.jpg")
    }

    @Test
    fun urlKeepsAbsolutePreviewArtworkAndRejectsBlankPaths() {
        assertThat(factory.url("https://preview.example/artwork/poster.png", ImageKind.POSTER))
            .isEqualTo("https://preview.example/artwork/poster.png")
        assertThat(factory.url("  ", ImageKind.PROFILE)).isNull()
        assertThat(factory.url(null, ImageKind.PROFILE)).isNull()
    }
}
