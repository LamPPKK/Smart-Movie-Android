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
}
