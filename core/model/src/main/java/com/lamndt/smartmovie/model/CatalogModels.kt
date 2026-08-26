package com.lamndt.smartmovie.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MediaType { @SerialName("movie") MOVIE, @SerialName("tv") TV;
    val wireValue: String get() = if (this == MOVIE) "movie" else "tv"
}

@Serializable
data class Genre(val id: Int, val name: String)

@Serializable
data class CastMember(
    val id: Int,
    val name: String,
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
)

@Serializable
data class Video(
    val id: String,
    val key: String,
    val name: String,
    val site: String,
    val type: String,
    val official: Boolean = false,
    val language: String? = null,
)

@Serializable
data class TitleSummary(
    val id: Int,
    @SerialName("media_type") val mediaType: MediaType,
    val title: String,
    @SerialName("original_title") val originalTitle: String,
    val overview: String,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    val adult: Boolean = false,
) {
    val libraryKey: String get() = "${mediaType.wireValue}:$id"
    val displayTitle: String get() = title.ifBlank { originalTitle }
    val releaseYear: String? get() = releaseDate?.take(4)?.takeIf { it.length == 4 }
}

@Serializable
data class TitleDetail(
    val id: Int,
    @SerialName("media_type") val mediaType: MediaType,
    val title: String,
    @SerialName("original_title") val originalTitle: String,
    val overview: String,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val genres: List<Genre> = emptyList(),
    @SerialName("runtime_minutes") val runtimeMinutes: Int? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    val status: String? = null,
    val cast: List<CastMember> = emptyList(),
    val videos: List<Video> = emptyList(),
    val similar: List<TitleSummary> = emptyList(),
) {
    val summary: TitleSummary get() = TitleSummary(
        id = id,
        mediaType = mediaType,
        title = title,
        originalTitle = originalTitle,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate,
        voteAverage = voteAverage,
        genreIds = genres.map(Genre::id),
        adult = false,
    )
}

@Serializable
data class PagedResult<T>(
    val page: Int,
    @SerialName("total_pages") val totalPages: Int,
    val results: List<T>,
)

@Serializable
data class HomeSection(val id: String, val title: String, val items: List<TitleSummary>)

@Serializable
data class HomeFeed(
    @SerialName("media_type") val mediaType: MediaType,
    val hero: TitleSummary? = null,
    val sections: List<HomeSection> = emptyList(),
)

enum class SearchScope(val wireValue: String) { ALL("all"), MOVIE("movie"), TV("tv") }
enum class DiscoverSort(val wireValue: String) {
    POPULARITY("popularity.desc"), RATING("vote_average.desc"), RELEASE_DATE("primary_release_date.desc")
}

enum class WatchMonetizationType(val wireValue: String) {
    SUBSCRIPTION("flatrate"), FREE("free"), ADS("ads"), RENT("rent"), BUY("buy")
}

data class DiscoverFilter(
    val genres: Set<Int> = emptySet(),
    val year: Int? = null,
    val minimumRating: Double = 0.0,
    val sort: DiscoverSort = DiscoverSort.POPULARITY,
    val releaseDateFrom: String? = null,
    val releaseDateThrough: String? = null,
    val originalLanguage: String? = null,
    val originCountry: String? = null,
    val certificationCountry: String? = null,
    val certificationMinimum: String? = null,
    val certificationMaximum: String? = null,
    val minimumRuntime: Int? = null,
    val maximumRuntime: Int? = null,
    val minimumVoteCount: Int = 0,
    val region: String? = null,
    val watchProviderIds: Set<Int> = emptySet(),
    val monetizationTypes: Set<WatchMonetizationType> = emptySet(),
    val includeAdult: Boolean = false,
)

@Serializable
data class ImageConfiguration(
    @SerialName("secure_base_url") val secureBaseUrl: String,
    @SerialName("poster_sizes") val posterSizes: List<String>,
    @SerialName("backdrop_sizes") val backdropSizes: List<String>,
    @SerialName("profile_sizes") val profileSizes: List<String>,
) {
    companion object {
        val Fallback = ImageConfiguration(
            secureBaseUrl = "https://image.tmdb.org/t/p/",
            posterSizes = listOf("w342", "w500", "original"),
            backdropSizes = listOf("w780", "w1280", "original"),
            profileSizes = listOf("w185", "h632", "original"),
        )
    }
}

enum class ImageKind { POSTER, BACKDROP, PROFILE }
enum class LibraryCollection { FAVORITES, WATCHLIST }
enum class LibrarySort { RECENTLY_ADDED, TITLE, RELEASE_DATE }

data class LibrarySnapshot(
    val id: String,
    val title: TitleSummary,
    val isFavorite: Boolean,
    val isWatchlisted: Boolean,
    val favoritedAt: Long?,
    val watchlistedAt: Long?,
    val updatedAt: Long,
)

sealed interface Loadable<out T> {
    data object Idle : Loadable<Nothing>
    data object Loading : Loadable<Nothing>
    data class Loaded<T>(val value: T) : Loadable<T>
    data class Failed(val message: String) : Loadable<Nothing>
}
