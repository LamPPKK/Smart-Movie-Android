package com.lamndt.smartmovie.network

import com.lamndt.smartmovie.model.Genre
import com.lamndt.smartmovie.model.HomeFeed
import com.lamndt.smartmovie.model.ImageConfiguration
import com.lamndt.smartmovie.model.PagedResult
import com.lamndt.smartmovie.model.TitleDetail
import com.lamndt.smartmovie.model.TitleSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

internal interface CatalogService {
    @GET("v1/home")
    suspend fun home(
        @Header(CLIENT_HEADER) clientId: String,
        @Query("media_type") mediaType: String,
        @Query("language") language: String,
    ): HomeFeed

    @GET("v1/genres/{mediaType}")
    suspend fun genres(
        @Header(CLIENT_HEADER) clientId: String,
        @Path("mediaType") mediaType: String,
        @Query("language") language: String,
    ): GenreEnvelope

    @GET("v1/discover/{mediaType}")
    suspend fun discover(
        @Header(CLIENT_HEADER) clientId: String,
        @Path("mediaType") mediaType: String,
        @Query("page") page: Int,
        @Query("language") language: String,
        @Query("sort_by") sortBy: String,
        @Query("vote_average_gte") minimumRating: String,
        @Query("genre_ids") genreIds: String?,
        @Query("year") year: Int?,
    ): PagedResult<TitleSummary>

    @GET("v1/search")
    suspend fun search(
        @Header(CLIENT_HEADER) clientId: String,
        @Query("query") query: String,
        @Query("scope") scope: String,
        @Query("page") page: Int,
        @Query("language") language: String,
    ): PagedResult<TitleSummary>

    @GET("v1/titles/{mediaType}/{id}")
    suspend fun detail(
        @Header(CLIENT_HEADER) clientId: String,
        @Path("mediaType") mediaType: String,
        @Path("id") id: Int,
        @Query("language") language: String,
    ): TitleDetail

    @GET("v1/configuration")
    suspend fun imageConfiguration(@Header(CLIENT_HEADER) clientId: String): ImageConfiguration

    companion object { const val CLIENT_HEADER = "X-SmartMovie-Client" }
}

@Serializable
internal data class GenreEnvelope(val genres: List<Genre>)

@Serializable
internal data class ErrorEnvelope(val error: ErrorBody)

@Serializable
internal data class ErrorBody(
    val code: String,
    val message: String,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("retry_after") val retryAfter: Long? = null,
)
