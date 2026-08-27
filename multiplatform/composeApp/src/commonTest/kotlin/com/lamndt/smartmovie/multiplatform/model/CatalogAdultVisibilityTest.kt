package com.lamndt.smartmovie.multiplatform.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogAdultVisibilityTest {
    @Test
    fun entityRelatedTitlesAndCreditsFailClosed() {
        val safe = title(1, adult = false)
        val restricted = title(2, adult = true)
        val credits = listOf(
            Credit(creditId = "safe", id = 1, mediaType = MediaType.MOVIE, title = "Safe"),
            Credit(
                creditId = "restricted",
                id = 2,
                mediaType = MediaType.MOVIE,
                title = "Restricted",
                adult = true,
            ),
        )

        assertEquals(listOf(safe), visibleCatalogTitles(listOf(safe, restricted), includeAdult = false))
        assertEquals(listOf(credits.first()), visibleCatalogCredits(credits, includeAdult = false))
        assertEquals(listOf(safe, restricted), visibleCatalogTitles(listOf(safe, restricted), includeAdult = true))
        val person = CatalogEntity.Person(PersonSummary(7, "Person", knownFor = listOf(safe, restricted)))
        val visiblePerson = visibleCatalogEntities(listOf(person), includeAdult = false).single() as CatalogEntity.Person
        assertEquals(listOf(safe), visiblePerson.value.knownFor)
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
        assertEquals(emptyList(), visibleCatalogEntities(listOf(season, episode), includeAdult = false))
        assertEquals(listOf(season, episode), visibleCatalogEntities(listOf(season, episode), includeAdult = true))
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
