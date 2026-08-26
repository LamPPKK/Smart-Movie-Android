package com.lamndt.smartmovie.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

class ContractV2ConformanceTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun everyCanonicalDiscriminatorAndDeepEntityDecodes() {
        val capabilities = decode<CapabilitiesV2>("capabilities")
        assertThat(capabilities.apiVersion).isEqualTo("v2")
        assertThat(capabilities.supportsCatalog("advanced_discover")).isTrue()
        assertThat(capabilities.supportsAccount("browser_auth")).isTrue()
        assertThat(capabilities.supportsAccount("tv_qr_auth")).isTrue()
        assertThat(capabilities.supportsAccount("ratings")).isTrue()
        assertThat(capabilities.supportedEntityKinds).containsExactlyElementsIn(EntityKind.entries)
        val configuration = decode<DiscoverConfiguration>("configuration")
        assertThat(configuration.region).isEqualTo("US")
        val providers = requireNotNull(configuration.watchProviders)
        assertThat(providers.movie.map(WatchProviderOption::id))
            .containsExactly(8)
            .inOrder()
        assertThat(providers.tv.single().id).isEqualTo(337)

        val page = decode<PagedResult<CatalogEntity>>("entities")
        assertThat(page.results.map(CatalogEntity::entityKind)).containsExactlyElementsIn(EntityKind.entries)
        val stableKeys = page.results.map(CatalogEntity::stableKey)
        assertThat(stableKeys.toSet()).hasSize(stableKeys.size)

        val find = decode<ExternalIdFindResult>("find")
        assertThat(find.source).isEqualTo(ExternalIdSource.IMDB)
        assertThat(find.externalId).isEqualTo("tt0000010")
        assertThat(find.results.map(CatalogEntity::entityKind))
            .containsExactly(EntityKind.MOVIE, EntityKind.PERSON)
            .inOrder()

        val detail = decode<TitleDetailV2>("title-detail")
        assertThat(detail.summary.libraryKey).isEqualTo("movie:10")
        assertThat(detail.watchProviders.single().attribution).isEqualTo("JustWatch")
        assertThat(detail.externalIds["imdb_id"]).isEqualTo("tt0000010")
        assertThat(detail.alternativeTitles.map(AlternativeTitle::title))
            .containsExactly("Phim Mẫu", "Catalog Sample")
            .inOrder()
        assertThat(detail.alternativeTitles.last().countryCode).isNull()
        assertThat(detail.releaseInformation.single().certification).isEqualTo("PG-13")
        assertThat(detail.releaseInformation.single().firstReleaseDate).isEqualTo("2026-08-25T00:00:00.000Z")
        assertThat(detail.translations.single().localizedTitle).isEqualTo("Phim Mẫu")
        assertThat(detail.releaseInformationFor("vn")).isNull()
        assertThat(detail.releaseInformationFor("us")?.countryCode).isEqualTo("US")
        assertThat(detail.displayAlternativeTitles("VN").first().title).isEqualTo("Phim Mẫu")
        assertThat(detail.displayTranslations("vi-VN").mapNotNull(TitleTranslation::localizedTitle)).containsExactly("Phim Mẫu")
        assertThat(detail.images.backdrops.single().filePath).isEqualTo("/catalog-backdrop.jpg")
        assertThat(detail.images.posters.single().filePath).isEqualTo("/catalog-poster.jpg")
        assertThat(detail.videos.map(Video::key)).containsExactly("catalogYT123", "catalogYT456").inOrder()
        assertThat(detail.reviews.results.map(Review::id))
            .containsExactly("review-catalog-1", "review-catalog-2")
            .inOrder()
        assertThat(detail.reviews.results.first().rating).isEqualTo(8.5)
        assertThat(detail.recommendations.results.map(TitleSummary::libraryKey))
            .containsExactly("movie:20", "movie:21")
            .inOrder()
        assertThat(detail.similar.map(TitleSummary::libraryKey)).containsExactly("movie:30")

        assertThat(decode<PersonDetail>("person").id).isEqualTo(12)
        assertThat(decode<CollectionDetail>("collection").id).isEqualTo(13)
        val season = decode<SeasonDetail>("season")
        assertThat(season.episodes.single().episodeKey).isEqualTo("11:1:1")
        assertThat(season.images.single().filePath).isEqualTo("/season-gallery.jpg")
        assertThat(season.videos.single().key).isEqualTo("seasonYT123")
        assertThat(season.externalIds["tvdb_id"]).isEqualTo("season-12345")
        val episode = decode<EpisodeDetail>("episode")
        assertThat(episode.episodeNumber).isEqualTo(1)
        assertThat(episode.runtimeMinutes).isEqualTo(52)
        assertThat(episode.productionCode).isEqualTo("SM-101")
        assertThat(episode.voteAverage).isEqualTo(8.3)
        assertThat(episode.voteCount).isEqualTo(240)
        assertThat(episode.images.single().filePath).isEqualTo("/pilot-gallery.jpg")
        assertThat(episode.videos.single().key).isEqualTo("episodeYT123")
        assertThat(episode.externalIds["imdb_id"]).isEqualTo("ttepisode101")
        val credit = decode<CreditDetail>("credit-detail")
        assertThat(credit.personSummary?.id).isEqualTo(6384)
        assertThat(credit.titleSummary?.libraryKey).isEqualTo("movie:603")

        val recommendations = decode<PagedResult<TitleSummary>>("account-recommendations")
        assertThat(recommendations.totalPages).isEqualTo(2)
        assertThat(recommendations.results.map(TitleSummary::libraryKey))
            .containsExactly("movie:438631", "movie:999001")
            .inOrder()
    }

    @Test
    fun accountAuthMutationAndUnknownFieldsRemainCompatible() {
        val account = decode<AccountFixture>("account")
        assertThat(account.state.favorite).isTrue()
        assertThat(account.list.results.map(TitleSummary::libraryKey)).containsExactly("movie:10", "tv:11").inOrder()
        assertThat(decode<AuthAttempt>("auth-attempt").authorizationUrl).contains("themoviedb.org")
        assertThat(decode<MutationResult>("mutation").success).isTrue()

        val future = json.decodeFromString<CatalogEntity>(
            """{"entity_kind":"person","id":99,"name":"Future Person","profile_path":null,"known_for_department":null,"popularity":0,"known_for":[],"future":{"safe":true}}""",
        )
        assertThat(future).isInstanceOf(CatalogEntity.Person::class.java)

        val tvRating = json.decodeFromString<ReleaseInformation>(
            """{"iso_3166_1":"US","rating":"TV-14","future":true}""",
        )
        assertThat(tvRating.certification).isEqualTo("TV-14")
        assertThat(tvRating.releaseDates).isEmpty()
    }

    private inline fun <reified Value> decode(fixture: String): Value {
        val file = File("../../catalog-contract/v2/fixtures/$fixture.json")
        check(file.isFile) { "Missing v2 fixture ${file.absolutePath}" }
        return json.decodeFromString(file.readText())
    }
}

@Serializable
private data class AccountFixture(val profile: AccountProfile, val state: AccountStateFixture, val list: UserList)

@Serializable
private data class AccountStateFixture(val favorite: Boolean, val watchlist: Boolean)
