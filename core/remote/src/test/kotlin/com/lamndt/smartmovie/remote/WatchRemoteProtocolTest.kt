package com.lamndt.smartmovie.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WatchRemoteProtocolTest {
    private val title = WatchTitleContext(
        libraryKey = "movie:550",
        title = "Fight Club",
        mediaType = "movie",
        year = "1999",
        artworkUrl = "https://image.test/fight-club.jpg",
        rating = 8.4,
        trailerAvailable = true,
        favorite = true,
    )

    @Test
    fun stateRoundTripPreservesDetailContext() {
        val decoded = WatchRemoteCodec.decodeState(
            WatchRemoteCodec.encodeState(WatchRemoteState(title)),
        )

        assertThat(decoded.context).isEqualTo(title)
    }

    @Test
    fun requestRoundTripPreservesCommandIdentity() {
        val request = WatchCommandRequest("request-1", title.libraryKey, WatchCommand.PLAY_TRAILER)

        assertThat(WatchRemoteCodec.decodeRequest(WatchRemoteCodec.encodeRequest(request))).isEqualTo(request)
    }

    @Test
    fun responseRoundTripPreservesUpdatedMembership() {
        val response = WatchCommandResponse(
            requestId = "request-2",
            accepted = true,
            context = title.copy(watchlist = true),
        )

        assertThat(WatchRemoteCodec.decodeResponse(WatchRemoteCodec.encodeResponse(response))).isEqualTo(response)
    }

    @Test
    fun decoderIgnoresFieldsFromNewerPeers() {
        val payload = """{"context":null,"future_field":"safe"}""".encodeToByteArray()

        assertThat(WatchRemoteCodec.decodeState(payload)).isEqualTo(WatchRemoteState())
    }
}
