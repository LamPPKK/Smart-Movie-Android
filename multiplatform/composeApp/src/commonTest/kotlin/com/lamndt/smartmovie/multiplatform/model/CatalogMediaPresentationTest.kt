package com.lamndt.smartmovie.multiplatform.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogMediaPresentationTest {
    @Test
    fun imagesRemoveBlankAndDuplicatePathsWithoutReordering() {
        val values = listOf(
            image("/first.jpg"),
            image(" "),
            image("/first.jpg", "poster"),
            image("/second.jpg", "poster"),
        )

        assertEquals(listOf("/first.jpg", "/second.jpg"), presentedImages(values).map(ImageAsset::filePath))
    }

    @Test
    fun videosOnlyExposeUniquePlayableYouTubeKeys() {
        val values = listOf(
            video("1", "trailer", "YouTube"),
            video("2", "trailer", "youtube"),
            video("3", "clip", "Vimeo"),
            video("4", " ", "YouTube"),
        )

        assertEquals(listOf("1"), playableVideos(values).map(Video::id))
    }

    @Test
    fun externalIdentifiersRemoveBlankValuesAndUseStableOrder() {
        assertEquals(
            listOf("imdb_id" to "tt123", "tvdb_id" to "42"),
            presentedExternalIds(mapOf("tvdb_id" to "42", "blank" to " ", "imdb_id" to "tt123")),
        )
    }

    private fun image(path: String, kind: String = "backdrop") = ImageAsset(
        kind = kind,
        filePath = path,
        aspectRatio = 1.78,
        height = 720,
        width = 1280,
    )

    private fun video(id: String, key: String, site: String) = Video(
        id = id,
        key = key,
        name = "Sample",
        site = site,
        type = "Trailer",
        official = true,
    )
}
