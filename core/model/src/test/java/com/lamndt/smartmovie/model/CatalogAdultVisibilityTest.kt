package com.lamndt.smartmovie.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CatalogAdultVisibilityTest {
    @Test
    fun entityRelatedTitlesAndCreditsFailClosed() {
        val safe = title(1, adult = false)
        val restricted = title(2, adult = true)
        val credits = listOf(
            Credit(creditId = "safe", id = 1, mediaType = MediaType.MOVIE, title = "Safe"),
            Credit(creditId = "restricted", id = 2, mediaType = MediaType.MOVIE, title = "Restricted", adult = true),
        )

        assertThat(visibleCatalogTitles(listOf(safe, restricted), includeAdult = false))
            .containsExactly(safe)
        assertThat(visibleCatalogCredits(credits, includeAdult = false))
            .containsExactly(credits.first())
        assertThat(visibleCatalogTitles(listOf(safe, restricted), includeAdult = true))
            .containsExactly(safe, restricted)
            .inOrder()
        val person = CatalogEntity.Person(PersonSummary(7, "Person", knownFor = listOf(safe, restricted)))
        val visiblePerson = visibleCatalogEntities(listOf(person), includeAdult = false).single() as CatalogEntity.Person
        assertThat(visiblePerson.value.knownFor).containsExactly(safe)
        val season = CatalogEntity.Season(
            SeasonSummary(id = 13, seasonNumber = 1, name = "Season"),
        )
        val episode = CatalogEntity.Episode(
            EpisodeSummary(
                id = 14,
                seriesId = 15,
                seasonNumber = 1,
                episodeNumber = 1,
                name = "Episode",
            ),
        )
        assertThat(visibleCatalogEntities(listOf(season, episode), includeAdult = false)).isEmpty()
        assertThat(visibleCatalogEntities(listOf(season, episode), includeAdult = true))
            .containsExactly(season, episode)
            .inOrder()
    }

    private fun title(id: Int, adult: Boolean) = TitleSummary(
        id = id,
        mediaType = MediaType.MOVIE,
        title = if (adult) "Restricted" else "Safe",
        originalTitle = if (adult) "Restricted" else "Safe",
        overview = "",
        adult = adult,
    )
}
