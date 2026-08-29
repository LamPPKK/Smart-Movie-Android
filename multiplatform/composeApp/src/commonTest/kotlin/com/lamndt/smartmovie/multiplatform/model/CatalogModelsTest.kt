package com.lamndt.smartmovie.multiplatform.model

import com.lamndt.smartmovie.multiplatform.data.createInstallationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogModelsTest {
    @Test
    fun trailerSelectionPrefersOfficialLocalizedYoutubeTrailer() {
        val videos = listOf(
            Video("one", "generic", "Generic", "YouTube", "Trailer"),
            Video("two", "localized", "Vietnamese", "YouTube", "Trailer", official = true, language = "vi"),
        )
        assertEquals("localized", preferredTrailer(videos, "vi-VN")?.key)
        assertNull(preferredTrailer(videos.filter { it.site == "Vimeo" }, "vi-VN"))
    }

    @Test
    fun localeMappingCoversAllSixCatalogLocales() {
        assertEquals("en-US", AppLocale.fromTag("en").backendTag)
        assertEquals("vi-VN", AppLocale.fromTag("vi-VN").backendTag)
        assertEquals("ja-JP", AppLocale.fromTag("ja").backendTag)
        assertEquals("ko-KR", AppLocale.fromTag("ko").backendTag)
        assertEquals("zh-CN", AppLocale.fromTag("zh-CN").backendTag)
        assertEquals("zh-TW", AppLocale.fromTag("zh-TW").backendTag)
    }

    @Test
    fun installationIdHasUuidShape() {
        val id = createInstallationId()
        assertEquals(36, id.length)
        assertEquals(listOf(8, 13, 18, 23), id.indices.filter { id[it] == '-' })
        assertTrue(id.all { it == '-' || it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun imageUrlsNormalizeConfigurationAndPaths() {
        val images = ImageUrlFactory(
            ImageConfiguration(
                secureBaseUrl = "https://image.tmdb.org/t/p",
                posterSizes = listOf("w342", "w500", "original"),
                backdropSizes = listOf("w780", "w1280", "original"),
                profileSizes = listOf("w185", "h632", "original"),
            ),
        )

        assertEquals("https://image.tmdb.org/t/p/w342/poster.jpg", images.poster("/poster.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", images.poster("poster.jpg", expanded = true))
        assertEquals("https://image.tmdb.org/t/p/w1280/backdrop.jpg", images.backdrop(" backdrop.jpg "))
        assertEquals("https://image.tmdb.org/t/p/w185/profile.jpg", images.profile("/profile.jpg"))
    }

    @Test
    fun imageUrlsKeepAbsolutePreviewArtworkAndRejectBlankPaths() {
        val images = ImageUrlFactory(ImageConfiguration.Fallback)

        assertEquals("https://preview.example/poster.png", images.poster("https://preview.example/poster.png"))
        assertNull(images.poster("  "))
        assertNull(images.backdrop(null))
    }
}
