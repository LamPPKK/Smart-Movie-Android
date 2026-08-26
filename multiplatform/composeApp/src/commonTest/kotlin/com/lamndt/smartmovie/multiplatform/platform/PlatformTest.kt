package com.lamndt.smartmovie.multiplatform.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlatformTest {
    @Test
    fun regionParserHandlesBcp47ScriptSubtags() {
        assertEquals("TW", regionFromLanguageTag("zh-Hant-TW"))
        assertEquals("CN", regionFromLanguageTag("zh-Hans-CN"))
        assertEquals("VN", regionFromLanguageTag("vi-VN"))
        assertNull(regionFromLanguageTag("en"))
    }
}
