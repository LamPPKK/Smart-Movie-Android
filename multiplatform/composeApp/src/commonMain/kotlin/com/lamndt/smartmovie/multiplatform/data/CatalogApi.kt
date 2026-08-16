package com.lamndt.smartmovie.multiplatform.data

import com.lamndt.smartmovie.multiplatform.model.DiscoverFilter
import com.lamndt.smartmovie.multiplatform.model.Genre
import com.lamndt.smartmovie.multiplatform.model.HomeFeed
import com.lamndt.smartmovie.multiplatform.model.ImageConfiguration
import com.lamndt.smartmovie.multiplatform.model.MediaType
import com.lamndt.smartmovie.multiplatform.model.PagedResult
import com.lamndt.smartmovie.multiplatform.model.SearchScope
import com.lamndt.smartmovie.multiplatform.model.TitleDetail
import com.lamndt.smartmovie.multiplatform.model.TitleSummary
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.pow
import kotlin.random.Random

interface CatalogApi {
    suspend fun home(mediaType: MediaType, language: String): HomeFeed
    suspend fun genres(mediaType: MediaType, language: String): List<Genre>
    suspend fun discover(mediaType: MediaType, filter: DiscoverFilter, page: Int, language: String): PagedResult<TitleSummary>
    suspend fun search(query: String, scope: SearchScope, page: Int, language: String): PagedResult<TitleSummary>
    suspend fun detail(mediaType: MediaType, id: Int, language: String): TitleDetail
    suspend fun imageConfiguration(): ImageConfiguration
}

class CatalogFailure(
    val kind: Kind,
    message: String,
    val retryAfterSeconds: Long? = null,
    val requestId: String? = null,
) : Exception(message) {
    enum class Kind { UNAUTHORIZED, NOT_FOUND, RATE_LIMITED, SERVER, DECODING, TRANSPORT }
}

class KtorCatalogApi(
    baseUrl: String = "https://catalog.smartmovie.app/",
    private val clientId: String,
    private val jsonFormat: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) : CatalogApi {
    private val root = baseUrl.trimEnd('/')
    private val client = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) { json(jsonFormat) }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }

    override suspend fun home(mediaType: MediaType, language: String): HomeFeed = request {
        client.get("$root/v1/home") {
            smartMovieHeaders()
            parameter("media_type", mediaType.wireValue)
            parameter("language", language)
        }
    }

    override suspend fun genres(mediaType: MediaType, language: String): List<Genre> = request<GenreEnvelope> {
        client.get("$root/v1/genres/${mediaType.wireValue}") {
            smartMovieHeaders()
            parameter("language", language)
        }
    }.genres

    override suspend fun discover(
        mediaType: MediaType,
        filter: DiscoverFilter,
        page: Int,
        language: String,
    ): PagedResult<TitleSummary> = request {
        client.get("$root/v1/discover/${mediaType.wireValue}") {
            smartMovieHeaders()
            parameter("page", page)
            parameter("language", language)
            parameter("sort_by", filter.sort.wireValue)
            parameter("vote_average_gte", filter.minimumRating)
            filter.genres.takeIf { it.isNotEmpty() }?.let { parameter("genre_ids", it.sorted().joinToString(",")) }
            filter.year?.let { parameter("year", it) }
        }
    }

    override suspend fun search(
        query: String,
        scope: SearchScope,
        page: Int,
        language: String,
    ): PagedResult<TitleSummary> = request {
        client.get("$root/v1/search") {
            smartMovieHeaders()
            parameter("query", query)
            parameter("scope", scope.wireValue)
            parameter("page", page)
            parameter("language", language)
        }
    }

    override suspend fun detail(mediaType: MediaType, id: Int, language: String): TitleDetail = request {
        client.get("$root/v1/titles/${mediaType.wireValue}/$id") {
            smartMovieHeaders()
            parameter("language", language)
        }
    }

    override suspend fun imageConfiguration(): ImageConfiguration = request {
        client.get("$root/v1/configuration") { smartMovieHeaders() }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.smartMovieHeaders() {
        header("X-SmartMovie-Client", clientId)
        header("Accept", "application/json")
    }

    private suspend inline fun <reified T> request(crossinline call: suspend () -> HttpResponse): T {
        var lastFailure: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val response = call()
                val payload = response.bodyAsText()
                if (response.status.value in 200..299) {
                    return try {
                        jsonFormat.decodeFromString(payload)
                    } catch (failure: Throwable) {
                        throw CatalogFailure(CatalogFailure.Kind.DECODING, "The catalog returned unreadable data.")
                    }
                }

                val error = runCatching { jsonFormat.decodeFromString<ErrorEnvelope>(payload).error }.getOrNull()
                when (response.status) {
                    HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> throw CatalogFailure(
                        CatalogFailure.Kind.UNAUTHORIZED,
                        "The catalog service is not configured correctly.",
                        requestId = error?.requestId,
                    )
                    HttpStatusCode.NotFound -> throw CatalogFailure(
                        CatalogFailure.Kind.NOT_FOUND,
                        "This title is no longer available.",
                        requestId = error?.requestId,
                    )
                    HttpStatusCode.TooManyRequests -> {
                        val retryAfter = error?.retryAfter
                            ?: response.headers["Retry-After"]?.toLongOrNull()
                            ?: (attempt + 1).toLong()
                        if (attempt < MAX_ATTEMPTS - 1) sleeper(retryAfter.coerceAtLeast(0) * 1_000)
                        else throw CatalogFailure(
                            CatalogFailure.Kind.RATE_LIMITED,
                            "Too many requests. Please try again shortly.",
                            retryAfter,
                            error?.requestId,
                        )
                    }
                    else -> if (response.status.value >= 500) {
                        if (attempt < MAX_ATTEMPTS - 1) sleeper(backoffMillis(attempt))
                        else throw CatalogFailure(
                            CatalogFailure.Kind.SERVER,
                            "The catalog is temporarily unavailable.",
                            requestId = error?.requestId,
                        )
                    } else {
                        throw CatalogFailure(
                            CatalogFailure.Kind.SERVER,
                            error?.message ?: "The catalog rejected the request.",
                            requestId = error?.requestId,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (known: CatalogFailure) {
                throw known
            } catch (failure: Throwable) {
                lastFailure = failure
                if (attempt < MAX_ATTEMPTS - 1) sleeper(backoffMillis(attempt))
            }
        }
        throw CatalogFailure(
            CatalogFailure.Kind.TRANSPORT,
            lastFailure?.message ?: "Check your connection and try again.",
        )
    }

    private fun backoffMillis(attempt: Int): Long = (300.0 * 2.0.pow(attempt)).toLong()

    companion object { private const val MAX_ATTEMPTS = 3 }
}

@Serializable
private data class GenreEnvelope(val genres: List<Genre>)

@Serializable
private data class ErrorEnvelope(val error: ErrorBody)

@Serializable
private data class ErrorBody(
    val code: String,
    val message: String,
    @kotlinx.serialization.SerialName("request_id") val requestId: String? = null,
    @kotlinx.serialization.SerialName("retry_after") val retryAfter: Long? = null,
)

fun createInstallationId(): String {
    val bytes = Random.nextBytes(16)
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    val hex = bytes.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
    return "${hex.take(8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.drop(20)}"
}
