package com.lamndt.smartmovie.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object WatchRemotePaths {
    const val PHONE_CAPABILITY = "smartmovie_phone_remote"
    const val STATE = "/smartmovie/remote/state"
    const val COMMAND = "/smartmovie/remote/command"
    const val RESPONSE = "/smartmovie/remote/response"
    const val PAYLOAD = "payload"
}

@Serializable
enum class WatchContextKind {
    @SerialName("title") TITLE,
    @SerialName("episode") EPISODE,
}

@Serializable
data class WatchTitleContext(
    val libraryKey: String,
    val contextKey: String = libraryKey,
    val contextKind: WatchContextKind = WatchContextKind.TITLE,
    val title: String,
    val mediaType: String,
    val seriesTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val year: String? = null,
    val artworkUrl: String? = null,
    val rating: Double = 0.0,
    val trailerAvailable: Boolean = false,
    val libraryActionsAvailable: Boolean = true,
    val favorite: Boolean = false,
    val watchlist: Boolean = false,
)

@Serializable
data class WatchRemoteState(
    val context: WatchTitleContext? = null,
    val phoneActive: Boolean = false,
)

@Serializable
enum class WatchCommand {
    OPEN_DETAILS,
    PLAY_TRAILER,
    TOGGLE_FAVORITE,
    TOGGLE_WATCHLIST,
}

@Serializable
data class WatchCommandRequest(
    val requestId: String,
    val libraryKey: String,
    val command: WatchCommand,
    val contextKey: String? = null,
)

@Serializable
data class WatchCommandResponse(
    val requestId: String,
    val accepted: Boolean,
    val context: WatchTitleContext? = null,
    val message: String? = null,
)

object WatchRemoteCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeState(value: WatchRemoteState): ByteArray =
        json.encodeToString(WatchRemoteState.serializer(), value).encodeToByteArray()

    fun decodeState(value: ByteArray): WatchRemoteState =
        json.decodeFromString(WatchRemoteState.serializer(), value.decodeToString())

    fun encodeRequest(value: WatchCommandRequest): ByteArray =
        json.encodeToString(WatchCommandRequest.serializer(), value).encodeToByteArray()

    fun decodeRequest(value: ByteArray): WatchCommandRequest =
        json.decodeFromString(WatchCommandRequest.serializer(), value.decodeToString())

    fun encodeResponse(value: WatchCommandResponse): ByteArray =
        json.encodeToString(WatchCommandResponse.serializer(), value).encodeToByteArray()

    fun decodeResponse(value: ByteArray): WatchCommandResponse =
        json.decodeFromString(WatchCommandResponse.serializer(), value.decodeToString())
}
