package com.lamndt.smartmovie.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class CatalogModelsTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun detailFixture_decodesSameStableContractAsIos() {
        val detail = json.decodeFromString<TitleDetail>(DETAIL_FIXTURE)

        assertThat(detail.id).isEqualTo(42)
        assertThat(detail.mediaType).isEqualTo(MediaType.MOVIE)
        assertThat(detail.cast.first().character).isEqualTo("Lead")
        assertThat(detail.videos.first().official).isTrue()
        assertThat(detail.summary.libraryKey).isEqualTo("movie:42")
    }

    @Test
    fun localeResolver_supportsAllShippedLanguages() {
        assertThat(CatalogLocale.from("vi", "VN")).isEqualTo("vi-VN")
        assertThat(CatalogLocale.from("ja", "JP")).isEqualTo("ja-JP")
        assertThat(CatalogLocale.from("ko", "KR")).isEqualTo("ko-KR")
        assertThat(CatalogLocale.from("zh", "CN")).isEqualTo("zh-CN")
        assertThat(CatalogLocale.from("zh", "TW")).isEqualTo("zh-TW")
        assertThat(CatalogLocale.from("fr", "FR")).isEqualTo("en-US")
    }

    @Test
    fun tvSummary_usesOriginalTitleAndStableLibraryKey() {
        val title = json.decodeFromString<TitleSummary>(
            """{"id":77,"media_type":"tv","title":"","original_title":"原題","overview":"","release_date":"2025-04-03","vote_average":8.2,"genre_ids":[18]}""",
        )

        assertThat(title.displayTitle).isEqualTo("原題")
        assertThat(title.releaseYear).isEqualTo("2025")
        assertThat(title.libraryKey).isEqualTo("tv:77")
    }

    @Test
    fun preferredTrailer_prioritizesOfficialLocalizedYoutubeTrailer() {
        val selected = preferredTrailer(
            listOf(
                Video("1", "fallback", "Fallback", "YouTube", "Trailer", false, "en"),
                Video("2", "local", "Local", "YouTube", "Trailer", true, "vi"),
            ),
            "vi",
        )
        assertThat(selected?.key).isEqualTo("local")
    }

    private companion object {
        const val DETAIL_FIXTURE = """
            {
              "id": 42, "media_type": "movie", "title": "A Test Story", "original_title": "A Test Story",
              "overview": "A fixture used to prove the stable contract.", "poster_path": "/poster.jpg",
              "backdrop_path": "/backdrop.jpg", "release_date": "2026-08-14", "vote_average": 8.4,
              "genres": [{"id": 18, "name": "Drama"}], "runtime_minutes": 123, "number_of_seasons": null,
              "status": "Released", "cast": [{"id": 7, "name": "Actor", "character": "Lead", "profile_path": "/actor.jpg"}],
              "videos": [{"id": "video", "key": "abc", "name": "Trailer", "site": "YouTube", "type": "Trailer", "official": true, "language": "en-US"}],
              "similar": []
            }
        """
    }
}
