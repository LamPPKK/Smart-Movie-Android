package com.lamndt.smartmovie.network

import android.content.Context
import com.lamndt.smartmovie.model.CatalogException
import com.lamndt.smartmovie.model.CapabilitiesV2
import com.lamndt.smartmovie.model.CatalogEntity
import com.lamndt.smartmovie.model.CollectionDetail
import com.lamndt.smartmovie.model.DiscoverFilter
import com.lamndt.smartmovie.model.Genre
import com.lamndt.smartmovie.model.HomeFeed
import com.lamndt.smartmovie.model.ImageConfiguration
import com.lamndt.smartmovie.model.EntityKind
import com.lamndt.smartmovie.model.EpisodeDetail
import com.lamndt.smartmovie.model.KeywordDetail
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.PagedResult
import com.lamndt.smartmovie.model.OrganizationDetail
import com.lamndt.smartmovie.model.PersonDetail
import com.lamndt.smartmovie.model.SearchScope
import com.lamndt.smartmovie.model.SearchScopeV2
import com.lamndt.smartmovie.model.SeasonDetail
import com.lamndt.smartmovie.model.TitleDetail
import com.lamndt.smartmovie.model.TitleDetailV2
import com.lamndt.smartmovie.model.TitleSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

interface CatalogRemoteDataSource {
    suspend fun home(mediaType: MediaType, language: String): HomeFeed
    suspend fun genres(mediaType: MediaType, language: String): List<Genre>
    suspend fun discover(mediaType: MediaType, filter: DiscoverFilter, page: Int, language: String): PagedResult<TitleSummary>
    suspend fun search(query: String, scope: SearchScope, page: Int, language: String): PagedResult<TitleSummary>
    suspend fun detail(mediaType: MediaType, id: Int, language: String): TitleDetail
    suspend fun imageConfiguration(): ImageConfiguration
}

interface CatalogRemoteDataSourceV2 : CatalogRemoteDataSource {
    suspend fun capabilities(): CapabilitiesV2
    suspend fun trending(kind: String, window: String, page: Int, language: String, includeAdult: Boolean): PagedResult<CatalogEntity>
    suspend fun searchEntities(
        query: String,
        scope: SearchScopeV2,
        page: Int,
        language: String,
        region: String?,
        includeAdult: Boolean,
    ): PagedResult<CatalogEntity>
    suspend fun deepDetail(mediaType: MediaType, id: Int, language: String, region: String?, includeAdult: Boolean): TitleDetailV2
    suspend fun person(id: Int, language: String): PersonDetail
    suspend fun collection(id: Int, language: String): CollectionDetail
    suspend fun organization(kind: EntityKind, id: Int, language: String, page: Int): OrganizationDetail
    suspend fun keyword(id: Int, language: String, page: Int): KeywordDetail
    suspend fun season(seriesId: Int, number: Int, language: String): SeasonDetail
    suspend fun episode(seriesId: Int, season: Int, number: Int, language: String): EpisodeDetail
}

class CatalogNetworkDataSource(
    context: Context?,
    baseUrl: String,
    private val json: Json = defaultJson,
    okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
    private val sleeper: suspend (kotlin.time.Duration) -> Unit = { delay(it) },
    private val clientIdProvider: suspend () -> String = {
        InstallationIdStore(requireNotNull(context) { "Context is required when no client ID provider is supplied." }.applicationContext).get()
    },
) : CatalogRemoteDataSourceV2 {
    private val service = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CatalogService::class.java)

    override suspend fun home(mediaType: MediaType, language: String): HomeFeed = execute {
        service.home(it, mediaType.wireValue, language)
    }

    override suspend fun genres(mediaType: MediaType, language: String): List<Genre> = execute {
        service.genres(it, mediaType.wireValue, language).genres
    }

    override suspend fun discover(
        mediaType: MediaType,
        filter: DiscoverFilter,
        page: Int,
        language: String,
    ): PagedResult<TitleSummary> = execute {
        service.discover(
            clientId = it,
            mediaType = mediaType.wireValue,
            page = page,
            language = language,
            sortBy = filter.sort.wireValue,
            minimumRating = String.format(Locale.US, "%.1f", filter.minimumRating),
            genreIds = filter.genres.sorted().takeIf { it.isNotEmpty() }?.joinToString(","),
            year = filter.year,
        )
    }

    override suspend fun search(query: String, scope: SearchScope, page: Int, language: String): PagedResult<TitleSummary> = execute {
        service.search(it, query, scope.wireValue, page, language)
    }

    override suspend fun detail(mediaType: MediaType, id: Int, language: String): TitleDetail = execute {
        service.detail(it, mediaType.wireValue, id, language)
    }

    override suspend fun imageConfiguration(): ImageConfiguration = execute { service.imageConfiguration(it) }

    override suspend fun capabilities(): CapabilitiesV2 = execute { service.capabilities(it) }

    override suspend fun trending(
        kind: String,
        window: String,
        page: Int,
        language: String,
        includeAdult: Boolean,
    ): PagedResult<CatalogEntity> = execute { service.trending(it, kind, window, page, language, includeAdult) }

    override suspend fun searchEntities(
        query: String,
        scope: SearchScopeV2,
        page: Int,
        language: String,
        region: String?,
        includeAdult: Boolean,
    ): PagedResult<CatalogEntity> = execute {
        service.searchEntities(it, query, scope.wireValue, page, language, region, includeAdult)
    }

    override suspend fun deepDetail(
        mediaType: MediaType,
        id: Int,
        language: String,
        region: String?,
        includeAdult: Boolean,
    ): TitleDetailV2 = execute { service.deepDetail(it, mediaType.wireValue, id, language, region, includeAdult) }

    override suspend fun person(id: Int, language: String): PersonDetail = execute { service.person(it, id, language) }
    override suspend fun collection(id: Int, language: String): CollectionDetail = execute { service.collection(it, id, language) }
    override suspend fun organization(kind: EntityKind, id: Int, language: String, page: Int): OrganizationDetail = execute {
        require(kind == EntityKind.COMPANY || kind == EntityKind.NETWORK)
        service.organization(it, kind.wireValue, id, language, page)
    }
    override suspend fun keyword(id: Int, language: String, page: Int): KeywordDetail = execute { service.keyword(it, id, language, page) }
    override suspend fun season(seriesId: Int, number: Int, language: String): SeasonDetail = execute { service.season(it, seriesId, number, language) }
    override suspend fun episode(seriesId: Int, season: Int, number: Int, language: String): EpisodeDetail = execute {
        service.episode(it, seriesId, season, number, language)
    }

    private suspend fun <T> execute(block: suspend (String) -> T): T {
        val clientId = clientIdProvider()
        var lastFailure: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return block(clientId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (http: HttpException) {
                val body = http.response()?.errorBody()?.string()?.let { runCatching { json.decodeFromString<ErrorEnvelope>(it) }.getOrNull() }
                when (http.code()) {
                    401, 403 -> throw CatalogException(CatalogException.Kind.UNAUTHORIZED, "The service is not configured correctly.", requestId = body?.error?.requestId)
                    404 -> throw CatalogException(CatalogException.Kind.NOT_FOUND, "This title is no longer available.", requestId = body?.error?.requestId)
                    429 -> {
                        val retryAfter = body?.error?.retryAfter
                            ?: parseRetryAfter(http.response()?.headers()?.get("Retry-After"))
                        if (attempt < MAX_ATTEMPTS - 1) sleeper((retryAfter ?: (attempt + 1).toLong()).seconds)
                        else throw CatalogException(CatalogException.Kind.RATE_LIMITED, "Too many requests. Please try again shortly.", retryAfter, body?.error?.requestId)
                    }
                    in 500..599 -> {
                        if (attempt < MAX_ATTEMPTS - 1) sleeper((300L * (attempt + 1)).milliseconds)
                        else throw CatalogException(CatalogException.Kind.SERVER, "The service is temporarily unavailable.", requestId = body?.error?.requestId)
                    }
                    else -> throw CatalogException(CatalogException.Kind.SERVER, body?.error?.message ?: "The server returned an invalid response.", requestId = body?.error?.requestId)
                }
            } catch (serialization: SerializationException) {
                throw CatalogException(CatalogException.Kind.DECODING, "Some movie information could not be read.")
            } catch (io: IOException) {
                lastFailure = io
                if (attempt < MAX_ATTEMPTS - 1) sleeper((300L * (attempt + 1)).milliseconds)
            }
        }
        throw CatalogException(CatalogException.Kind.TRANSPORT, lastFailure?.message ?: "Check your internet connection and try again.")
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        val defaultJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

        private fun parseRetryAfter(value: String?): Long? {
            if (value == null) return null
            value.toLongOrNull()?.let { return it.coerceAtLeast(0) }
            return runCatching {
                val target = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
                (target - System.currentTimeMillis() / 1_000).coerceAtLeast(0)
            }.getOrNull()
        }
    }
}
