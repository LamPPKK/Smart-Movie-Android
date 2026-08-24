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
        assertThat(capabilities.supportedEntityKinds).containsExactlyElementsIn(EntityKind.entries)

        val page = decode<PagedResult<CatalogEntity>>("entities")
        assertThat(page.results.map(CatalogEntity::entityKind)).containsExactlyElementsIn(EntityKind.entries)
        val stableKeys = page.results.map(CatalogEntity::stableKey)
        assertThat(stableKeys.toSet()).hasSize(stableKeys.size)

        val detail = decode<TitleDetailV2>("title-detail")
        assertThat(detail.summary.libraryKey).isEqualTo("movie:10")
        assertThat(detail.watchProviders.single().attribution).isEqualTo("JustWatch")
        assertThat(detail.externalIds["imdb_id"]).isEqualTo("tt0000010")

        assertThat(decode<PersonDetail>("person").id).isEqualTo(12)
        assertThat(decode<CollectionDetail>("collection").id).isEqualTo(13)
        assertThat(decode<SeasonDetail>("season").episodes.single().episodeKey).isEqualTo("11:1:1")
        assertThat(decode<EpisodeDetail>("episode").episodeNumber).isEqualTo(1)
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
