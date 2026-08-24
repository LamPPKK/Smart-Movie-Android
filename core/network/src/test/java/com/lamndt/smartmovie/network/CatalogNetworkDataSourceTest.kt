package com.lamndt.smartmovie.network

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.CatalogException
import com.lamndt.smartmovie.model.ExternalIdSource
import com.lamndt.smartmovie.model.SearchScope
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
    fun findExternalId_sendsSourceAndDecodesMixedEntities() = runTest {
        server.enqueue(MockResponse().setBody(FIND).setHeader("Content-Type", "application/json"))

        val result = source().findExternalId("tt0133093", ExternalIdSource.IMDB, "vi-VN")
        val request = server.takeRequest()

        assertThat(result.externalId).isEqualTo("tt0133093")
        assertThat(result.results).hasSize(2)
        assertThat(request.requestUrl?.encodedPath).isEqualTo("/v2/find/tt0133093")
        assertThat(request.requestUrl?.queryParameter("source")).isEqualTo("imdb_id")
        assertThat(request.requestUrl?.queryParameter("language")).isEqualTo("vi-VN")
    }

    @Test
    fun creditDetail_sendsLocaleAndDecodesStableLinks() = runTest {
        server.enqueue(MockResponse().setBody(CREDIT).setHeader("Content-Type", "application/json"))

        val result = source().credit("52fe425bc3a36847f80181c1", "ja-JP")
        val request = server.takeRequest()

        assertThat(result.personSummary?.name).isEqualTo("Keanu Reeves")
        assertThat(result.titleSummary?.libraryKey).isEqualTo("movie:603")
        assertThat(request.requestUrl?.encodedPath).isEqualTo("/v2/credits/52fe425bc3a36847f80181c1")
        assertThat(request.requestUrl?.queryParameter("language")).isEqualTo("ja-JP")
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
    }
}
