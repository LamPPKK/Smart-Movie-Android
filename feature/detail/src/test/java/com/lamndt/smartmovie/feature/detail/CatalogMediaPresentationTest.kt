package com.lamndt.smartmovie.feature.detail

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.ImageAsset
import com.lamndt.smartmovie.model.Video
import org.junit.Test

class CatalogMediaPresentationTest {
    @Test
    fun `images remove blank and duplicate paths without reordering`() {
        val values = listOf(
            image("/first.jpg"),
            image(" "),
            image("/first.jpg", "poster"),
            image("/second.jpg", "poster"),
        )

        assertThat(presentedImages(values).map(ImageAsset::filePath))
            .containsExactly("/first.jpg", "/second.jpg")
            .inOrder()
    }

    @Test
    fun `videos only expose unique playable YouTube keys`() {
        val values = listOf(
            video("1", "trailer", "YouTube"),
            video("2", "trailer", "youtube"),
            video("3", "clip", "Vimeo"),
            video("4", " ", "YouTube"),
        )

        assertThat(playableVideos(values).map(Video::id)).containsExactly("1")
    }

    @Test
    fun `external identifiers remove blank values and use stable order`() {
        val values = presentedExternalIds(
            mapOf("tvdb_id" to "42", "blank" to " ", "imdb_id" to "tt123"),
        )

        assertThat(values).containsExactly("imdb_id" to "tt123", "tvdb_id" to "42").inOrder()
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
