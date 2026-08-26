package com.lamndt.smartmovie.multiplatform.model

import com.lamndt.smartmovie.multiplatform.data.ErrorEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContractConformanceTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun canonicalSuccessFixturesDecodeIntoMultiplatformModels() {
        val home = decode<HomeFeed>("home")
        assertEquals("movie:42", home.hero?.libraryKey)

        val page = decode<PagedResult<TitleSummary>>("title-page")
        assertEquals("movie:42", page.results.single().libraryKey)

        assertEquals(listOf(12, 18), decode<GenreEnvelope>("genres").genres.map(Genre::id))

        val detail = decode<TitleDetail>("title-detail")
        assertEquals("Lead", detail.cast.single().character)
        assertTrue(detail.videos.single().official)

        assertEquals(listOf("w342", "w500", "original"), decode<ImageConfiguration>("configuration").posterSizes)
    }

    @Test
    fun errorAndForwardCompatibilityFixturesDecode() {
        val error = decode<ErrorEnvelope>("error").error
        assertEquals("rate_limited", error.code)
        assertEquals(60L, error.retryAfter)

        val summary = decode<TitleSummary>("title-summary-forward-compatible")
        assertEquals("tv:99", summary.libraryKey)
        assertNull(summary.posterPath)
        assertNull(summary.backdropPath)
        assertNull(summary.releaseDate)
    }

    @Test
    fun canonicalV2DiscriminatorsAndDeepEntitiesDecode() {
        val entities = decodeV2<PagedResult<CatalogEntity>>("entities")
        assertEquals(EntityKind.entries.toSet(), entities.results.map(CatalogEntity::entityKind).toSet())
        assertEquals(entities.results.size, entities.results.map(CatalogEntity::stableKey).toSet().size)

        val find = decodeV2<ExternalIdFindResult>("find")
        assertEquals(ExternalIdSource.IMDB, find.source)
        assertEquals("tt0000010", find.externalId)
        assertEquals(listOf(EntityKind.MOVIE, EntityKind.PERSON), find.results.map(CatalogEntity::entityKind))

        val title = decodeV2<TitleDetailV2>("title-detail")
        assertEquals("Catalog everything.", title.tagline)
        assertEquals("JustWatch", title.watchProviders.single().attribution)
        assertTrue(title.watchProviders.single().stream.isNotEmpty())
        assertEquals(listOf("Phim Mẫu", "Catalog Sample"), title.alternativeTitles.map(AlternativeTitle::title))
        assertNull(title.alternativeTitles.last().countryCode)
        assertEquals("PG-13", title.releaseInformation.single().certification)
        assertEquals("2026-08-25T00:00:00.000Z", title.releaseInformation.single().firstReleaseDate)
        assertEquals("Phim Mẫu", title.translations.single().localizedTitle)
        assertEquals(null, title.releaseInformationFor("vn"))
        assertEquals("US", title.releaseInformationFor("us")?.countryCode)
        assertEquals("Phim Mẫu", title.displayAlternativeTitles("VN").first().title)
        assertEquals(listOf("Phim Mẫu"), title.displayTranslations("vi-VN").mapNotNull(TitleTranslation::localizedTitle))

        assertEquals(12, decodeV2<PersonDetail>("person").id)
        assertEquals(13, decodeV2<CollectionDetail>("collection").id)
        assertEquals(11, decodeV2<SeasonDetail>("season").seriesId)
        val episode = decodeV2<EpisodeDetail>("episode")
        assertEquals("11:1:1", "${episode.seriesId}:${episode.seasonNumber}:${episode.episodeNumber}")
        assertEquals(0.0, episode.voteAverage)
        assertEquals(0, episode.voteCount)
        val credit = decodeV2<CreditDetail>("credit-detail")
        assertEquals(6384, credit.personSummary?.id)
        assertEquals("movie:603", credit.titleSummary?.libraryKey)
        val recommendations = decodeV2<PagedResult<TitleSummary>>("account-recommendations")
        assertEquals(listOf("movie:438631", "movie:999001"), recommendations.results.map(TitleSummary::libraryKey))
        val configuration = decodeV2<DiscoverConfiguration>("configuration")
        assertEquals("US", configuration.region)
        assertEquals("Netflix", configuration.watchProviders?.movie?.first()?.name)
        val capabilities = decodeV2<CapabilitiesV2>("capabilities")
        assertTrue(capabilities.supportsCatalog("advanced_discover"))
        assertTrue(capabilities.supportsAccount("browser_auth"))
        assertTrue(capabilities.supportsAccount("tv_qr_auth"))
        assertTrue(capabilities.supportsAccount("ratings"))

        val tvRating = json.decodeFromString<ReleaseInformation>(
            """{"iso_3166_1":"US","rating":"TV-14","future":true}""",
        )
        assertEquals("TV-14", tvRating.certification)
        assertTrue(tvRating.releaseDates.isEmpty())
    }

    @Test
    fun canonicalV2AccountAndErrorsDecodeWithoutSecrets() {
        val account = decodeV2<AccountFixture>("account")
        assertEquals("fixture_user", account.profile.username)
        assertEquals(7, account.list.id)
        assertEquals("pending", decodeV2<AuthAttempt>("auth-attempt").status)
        assertTrue(decodeV2<MutationResult>("mutation").success == true)
        val raw = v2Fixture("account").readText()
        assertTrue("session_token" !in raw && "access_token" !in raw && "password" !in raw)
        assertEquals("upstream_rate_limited", json.decodeFromString<ErrorEnvelope>(v2Fixture("error").readText()).error.code)
    }

    private inline fun <reified Value> decode(fixture: String): Value {
        val resource = checkNotNull(javaClass.classLoader.getResource("$fixture.json")) { "Missing fixture $fixture" }
        return json.decodeFromString(resource.readText())
    }

    private inline fun <reified Value> decodeV2(fixture: String): Value =
        json.decodeFromString(v2Fixture(fixture).readText())

    private fun v2Fixture(name: String): File = sequenceOf(
        File("../../catalog-contract/v2/fixtures/$name.json"),
        File("../catalog-contract/v2/fixtures/$name.json"),
        File("catalog-contract/v2/fixtures/$name.json"),
    ).firstOrNull(File::isFile) ?: error("Missing v2 fixture $name")
}

@Serializable
private data class GenreEnvelope(val genres: List<Genre>)

@Serializable
private data class AccountFixture(val profile: AccountProfile, val list: UserList)
