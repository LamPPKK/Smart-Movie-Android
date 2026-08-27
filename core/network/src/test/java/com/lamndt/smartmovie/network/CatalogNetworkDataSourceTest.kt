package com.lamndt.smartmovie.network

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.CatalogException
import com.lamndt.smartmovie.model.DiscoverFilter
import com.lamndt.smartmovie.model.ExternalIdSource
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.SearchScope
import com.lamndt.smartmovie.model.WatchMonetizationType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogNetworkDataSourceTest {
    private lateinit var server: MockWebServer
    private val sleeps = mutableListOf<Duration>()

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun source() = CatalogNetworkDataSource(
        context = null,
        baseUrl = server.url("/").toString(),
        okHttpClient = OkHttpClient(),
        sleeper = { sleeps += it },
        clientIdProvider = { "123e4567-e89b-12d3-a456-426614174000" },
    )

    @Test
    fun search_sendsWorkerPathQueryLocaleAndInstallationHeader() = runTest {
        server.enqueue(MockResponse().setBody(PAGE).setHeader("Content-Type", "application/json"))

        val result = source().search("Dune Part Two", SearchScope.ALL, 2, "vi-VN")
        val request = server.takeRequest()

        assertThat(result.page).isEqualTo(2)
        assertThat(request.requestUrl?.encodedPath).isEqualTo("/v1/search")
        assertThat(request.requestUrl?.queryParameter("query")).isEqualTo("Dune Part Two")
        assertThat(request.requestUrl?.queryParameter("scope")).isEqualTo("all")
        assertThat(request.requestUrl?.queryParameter("language")).isEqualTo("vi-VN")
        assertThat(request.getHeader("X-SmartMovie-Client")).isEqualTo("123e4567-e89b-12d3-a456-426614174000")
    }

    @Test
    fun discover_sendsCompleteDeterministicV2Filter() = runTest {
        server.enqueue(MockResponse().setBody(PAGE).setHeader("Content-Type", "application/json"))

        source().discover(
            mediaType = MediaType.MOVIE,
            filter = DiscoverFilter(
                genres = setOf(878, 18),
                year = 2026,
                minimumRating = 7.5,
                sort = com.lamndt.smartmovie.model.DiscoverSort.RATING,
                releaseDateFrom = " 2026-01-01 ",
                releaseDateThrough = "2026-12-31",
                originalLanguage = "VI",
                originCountry = "vn",
                certificationCountry = "us",
                certificationMinimum = "PG",
                certificationMaximum = "R",
                minimumRuntime = 80,
                maximumRuntime = 180,
                minimumVoteCount = 250,
                region = "vn",
                watchProviderIds = setOf(337, 8),
                monetizationTypes = setOf(WatchMonetizationType.RENT, WatchMonetizationType.SUBSCRIPTION),
                includeAdult = true,
            ),
            page = 3,
            language = "vi-VN",
        )
        val request = server.takeRequest().requestUrl!!

        assertThat(request.encodedPath).isEqualTo("/v2/discover/movie")
        assertThat(request.queryParameter("genres")).isEqualTo("18,878")
        assertThat(request.queryParameter("release_date_gte")).isEqualTo("2026-01-01")
        assertThat(request.queryParameter("original_language")).isEqualTo("vi")
        assertThat(request.queryParameter("origin_country")).isEqualTo("VN")
        assertThat(request.queryParameter("certification_country")).isEqualTo("US")
        assertThat(request.queryParameter("runtime_gte")).isEqualTo("80")
        assertThat(request.queryParameter("vote_count_gte")).isEqualTo("250")
        assertThat(request.queryParameter("region")).isEqualTo("VN")
        assertThat(request.queryParameter("watch_region")).isEqualTo("VN")
        assertThat(request.queryParameter("watch_providers")).isEqualTo("8|337")
        assertThat(request.queryParameter("watch_monetization_types")).isEqualTo("flatrate|rent")
        assertThat(request.queryParameter("include_adult")).isEqualTo("true")
    }

    @Test
    fun discoverBasic_usesV1AndOmitsAdvancedFields() = runTest {
        server.enqueue(MockResponse().setBody(PAGE).setHeader("Content-Type", "application/json"))

        source().discoverBasic(
            mediaType = MediaType.MOVIE,
            filter = DiscoverFilter(
                genres = setOf(878, 18),
                year = 1999,
                minimumRating = 7.0,
                releaseDateFrom = "2026-01-01",
                watchProviderIds = setOf(8),
                includeAdult = true,
            ),
            page = 2,
            language = "en-US",
        )
        val request = server.takeRequest().requestUrl!!

        assertThat(request.encodedPath).isEqualTo("/v1/discover/movie")
        assertThat(request.queryParameter("genre_ids")).isEqualTo("18,878")
        assertThat(request.queryParameter("year")).isEqualTo("1999")
        assertThat(request.queryParameter("vote_average_gte")).isEqualTo("7.0")
        assertThat(request.queryParameter("genres")).isNull()
        assertThat(request.queryParameter("include_adult")).isNull()
        assertThat(request.queryParameter("release_date_gte")).isNull()
        assertThat(request.queryParameter("watch_providers")).isNull()
    }

    @Test
    fun discoverConfiguration_sendsRegionAndDecodesProviderOptions() = runTest {
        server.enqueue(MockResponse().setBody(CONFIGURATION).setHeader("Content-Type", "application/json"))

        val configuration = source().discoverConfiguration("vi-VN", "VN")
        val request = server.takeRequest()

        assertThat(request.requestUrl?.encodedPath).isEqualTo("/v2/configuration")
        assertThat(request.requestUrl?.queryParameter("language")).isEqualTo("vi-VN")
        assertThat(request.requestUrl?.queryParameter("region")).isEqualTo("VN")
        assertThat(configuration.region).isEqualTo("VN")
        assertThat(configuration.watchProviders?.movie?.single()?.name).isEqualTo("Netflix")
        assertThat(configuration.countries.single().displayName).isEqualTo("Việt Nam")
    }

    @Test
    fun findExternalId_sendsSourceAndDecodesMixedEntities() = runTest {
        server.enqueue(MockResponse().setBody(FIND).setHeader("Content-Type", "application/json"))

        val result = source().findExternalId("tt0133093", ExternalIdSource.IMDB, "vi-VN", includeAdult = true)
        val request = server.takeRequest()

        assertThat(result.externalId).isEqualTo("tt0133093")
        assertThat(result.results).hasSize(2)
        assertThat(request.requestUrl?.encodedPath).isEqualTo("/v2/find/tt0133093")
        assertThat(request.requestUrl?.queryParameter("source")).isEqualTo("imdb_id")
        assertThat(request.requestUrl?.queryParameter("language")).isEqualTo("vi-VN")
        assertThat(request.requestUrl?.queryParameter("include_adult")).isEqualTo("true")
    }

    @Test
    fun creditDetail_sendsLocaleAndDecodesStableLinks() = runTest {
        server.enqueue(MockResponse().setBody(CREDIT).setHeader("Content-Type", "application/json"))

        val result = source().credit("52fe425bc3a36847f80181c1", "ja-JP", includeAdult = false)
        val request = server.takeRequest()

        assertThat(result.personSummary?.name).isEqualTo("Keanu Reeves")
        assertThat(result.titleSummary?.libraryKey).isEqualTo("movie:603")
        assertThat(request.requestUrl?.encodedPath).isEqualTo("/v2/credits/52fe425bc3a36847f80181c1")
        assertThat(request.requestUrl?.queryParameter("language")).isEqualTo("ja-JP")
        assertThat(request.requestUrl?.queryParameter("include_adult")).isEqualTo("false")
    }

    @Test
    fun rateLimit_retriesTwiceAndHonorsRetryAfter() = runTest {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "2").setBody(ERROR)) }
        server.enqueue(MockResponse().setBody(PAGE).setHeader("Content-Type", "application/json"))

        source().search("Dune", SearchScope.MOVIE, 1, "en-US")

        assertThat(server.requestCount).isEqualTo(3)
        assertThat(sleeps).containsExactly(Duration.parse("2s"), Duration.parse("2s"))
    }

    @Test
    fun fixedNotFound_doesNotRetry() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody(ERROR))

        val failure = runCatching { source().search("missing", SearchScope.ALL, 1, "en-US") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(CatalogException::class.java)
        assertThat((failure as CatalogException).kind).isEqualTo(CatalogException.Kind.NOT_FOUND)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun unauthorized_doesNotRetry() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody(ERROR))

        val failure = runCatching { source().search("Dune", SearchScope.ALL, 1, "en-US") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(CatalogException::class.java)
        assertThat((failure as CatalogException).kind).isEqualTo(CatalogException.Kind.UNAUTHORIZED)
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(sleeps).isEmpty()
    }

    @Test
    fun serverFailure_retriesAtMostTwice() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503).setBody(ERROR)) }

        val failure = runCatching { source().search("Dune", SearchScope.ALL, 1, "en-US") }.exceptionOrNull()

        assertThat((failure as CatalogException).kind).isEqualTo(CatalogException.Kind.SERVER)
        assertThat(server.requestCount).isEqualTo(3)
        assertThat(sleeps).containsExactly(Duration.parse("300ms"), Duration.parse("600ms")).inOrder()
    }

    @Test
    fun malformedSuccessBody_isReportedWithoutRetry() = runTest {
        server.enqueue(MockResponse().setBody("{not-json").setHeader("Content-Type", "application/json"))

        val failure = runCatching { source().search("Dune", SearchScope.ALL, 1, "en-US") }.exceptionOrNull()

        assertThat((failure as CatalogException).kind).isEqualTo(CatalogException.Kind.DECODING)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun cancellation_propagatesWithoutRetry() = runTest {
        server.enqueue(MockResponse().setBodyDelay(30, java.util.concurrent.TimeUnit.SECONDS).setBody(PAGE))
        val request = async { source().search("slow", SearchScope.ALL, 1, "en-US") }
        request.cancelAndJoin()
        assertThat(request.isCancelled).isTrue()
        assertThat(sleeps).isEmpty()
    }

    private companion object {
        const val PAGE = """{"page":2,"total_pages":4,"results":[{"id":42,"media_type":"movie","title":"Dune","original_title":"Dune","overview":"","vote_average":8.4,"genre_ids":[]}]}"""
        const val FIND = """{"source":"imdb_id","external_id":"tt0133093","results":[{"entity_kind":"movie","id":603,"media_type":"movie","title":"The Matrix","original_title":"The Matrix","overview":"","vote_average":8.2,"genre_ids":[]},{"entity_kind":"person","id":6384,"name":"Keanu Reeves","known_for":[]}]}"""
        const val CREDIT = """{"credit_id":"52fe425bc3a36847f80181c1","credit_type":"cast","department":"Acting","job":"Actor","character":"Neo","person_summary":{"entity_kind":"person","id":6384,"name":"Keanu Reeves","known_for":[]},"title_summary":{"entity_kind":"movie","id":603,"media_type":"movie","title":"The Matrix","original_title":"The Matrix","overview":"","vote_average":8.2,"genre_ids":[]}}"""
        const val ERROR = """{"error":{"code":"not_found","message":"Missing","request_id":"request-1"}}"""
        const val CONFIGURATION = """{"countries":[{"iso_3166_1":"VN","english_name":"Vietnam","native_name":"Việt Nam"}],"languages":[{"iso_639_1":"vi","english_name":"Vietnamese","name":"Tiếng Việt"}],"watch_provider_regions":[{"iso_3166_1":"VN","english_name":"Vietnam","native_name":"Việt Nam"}],"region":"VN","watch_providers":{"movie":[{"id":8,"name":"Netflix","display_priority":0}],"tv":[]}}"""
    }
}
