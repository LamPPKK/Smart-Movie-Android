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

interface CatalogV2Repository : CatalogRepository {
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

interface AccountRepository {
    suspend fun createAuthAttempt(returnUri: String, mode: String): AuthAttempt
    suspend fun authAttempt(id: String, deviceCode: String?): String
    suspend fun completeAuth(id: String, deviceCode: String?): AuthSession
    suspend fun profile(): AccountProfile
    suspend fun logout()
    suspend fun library(
        collection: LibraryCollection,
        mediaType: MediaType,
        page: Int,
        language: String,
    ): PagedResult<TitleSummary>
    suspend fun setLibrary(
        collection: LibraryCollection,
        mediaType: MediaType,
        mediaId: Int,
        enabled: Boolean,
        mutationId: String,
    ): MutationResult
    suspend fun setRating(mediaType: MediaType, mediaId: Int, value: Double?, mutationId: String): MutationResult
    suspend fun setEpisodeRating(seriesId: Int, season: Int, episode: Int, value: Double?, mutationId: String): MutationResult
    suspend fun recommendations(mediaType: MediaType, page: Int, language: String): PagedResult<TitleSummary>
    suspend fun lists(page: Int): PagedResult<UserList>
    suspend fun list(id: Int, page: Int, language: String): UserList
    suspend fun createList(name: String, description: String, isPublic: Boolean, region: String, language: String, mutationId: String): MutationResult
    suspend fun updateList(id: Int, name: String, description: String, isPublic: Boolean, mutationId: String): MutationResult
    suspend fun deleteList(id: Int, mutationId: String): MutationResult
    suspend fun mutateListItems(id: Int, items: List<UserListItemMutation>, remove: Boolean, mutationId: String): MutationResult
}

data class UserListItemMutation(val mediaType: MediaType, val mediaId: Int, val comment: String? = null)

interface LibraryRepository {
    fun observeItems(collection: LibraryCollection, mediaType: MediaType?, sort: LibrarySort): Flow<List<LibrarySnapshot>>
    fun observeMembership(libraryKey: String): Flow<LibraryMembership>
    suspend fun toggle(title: TitleSummary, collection: LibraryCollection)
}

interface LibrarySyncRepository : LibraryRepository {
    suspend fun activateAccount(accountId: Int)
    suspend fun deactivateAccount(removeAccountData: Boolean)
    suspend fun mergeRemote(
        items: List<TitleSummary>,
        collection: LibraryCollection,
        mediaType: MediaType,
        accountId: Int,
    )
    suspend fun pendingMutations(limit: Int = 100): List<PendingLibraryMutation>
    suspend fun confirmMutation(id: String)
    suspend fun failMutation(id: String, message: String)
}

data class PendingLibraryMutation(
    val id: String,
    val libraryKey: String,
    val mediaType: MediaType,
    val mediaId: Int,
    val collection: LibraryCollection,
    val enabled: Boolean,
    val accountId: Int,
    val attemptCount: Int,
)

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
