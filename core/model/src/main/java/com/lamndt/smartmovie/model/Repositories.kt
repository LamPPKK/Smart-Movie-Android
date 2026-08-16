package com.lamndt.smartmovie.model

import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    suspend fun home(mediaType: MediaType, language: String): HomeFeed
    suspend fun genres(mediaType: MediaType, language: String): List<Genre>
    suspend fun discover(mediaType: MediaType, filter: DiscoverFilter, page: Int, language: String): PagedResult<TitleSummary>
    suspend fun search(query: String, scope: SearchScope, page: Int, language: String): PagedResult<TitleSummary>
    suspend fun detail(mediaType: MediaType, id: Int, language: String): TitleDetail
    suspend fun imageConfiguration(): ImageConfiguration
}

interface LibraryRepository {
    fun observeItems(collection: LibraryCollection, mediaType: MediaType?, sort: LibrarySort): Flow<List<LibrarySnapshot>>
    fun observeMembership(libraryKey: String): Flow<LibraryMembership>
    suspend fun toggle(title: TitleSummary, collection: LibraryCollection)
}

data class LibraryMembership(val isFavorite: Boolean = false, val isWatchlisted: Boolean = false)

class CatalogException(
    val kind: Kind,
    message: String,
    val retryAfterSeconds: Long? = null,
    val requestId: String? = null,
) : Exception(message) {
    enum class Kind { INVALID_URL, INVALID_RESPONSE, UNAUTHORIZED, NOT_FOUND, RATE_LIMITED, SERVER, DECODING, TRANSPORT }
}

fun preferredTrailer(videos: List<Video>, language: String): Video? {
    val youtube = videos.filter { it.site.equals("YouTube", ignoreCase = true) }
    val trailers = youtube.filter { it.type.equals("Trailer", ignoreCase = true) }
    return trailers.firstOrNull {
        it.official && (it.language == language || it.language?.let(language::startsWith) == true)
    } ?: trailers.firstOrNull()
        ?: youtube.firstOrNull { it.type.equals("Teaser", ignoreCase = true) }
}

object CatalogLocale {
    private val supported = setOf("en", "vi", "ja", "ko", "zh")

    fun from(language: String, country: String = ""): String = when {
        language !in supported -> "en-US"
        language == "zh" && country.equals("TW", true) -> "zh-TW"
        language == "zh" -> "zh-CN"
        language == "vi" -> "vi-VN"
        language == "ja" -> "ja-JP"
        language == "ko" -> "ko-KR"
        else -> "en-US"
    }
}
