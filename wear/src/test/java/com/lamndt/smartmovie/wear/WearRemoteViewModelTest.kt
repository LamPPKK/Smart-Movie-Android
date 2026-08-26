package com.lamndt.smartmovie.wear

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.remote.WatchCommand
import com.lamndt.smartmovie.remote.WatchCommandRequest
import com.lamndt.smartmovie.remote.WatchCommandResponse
import com.lamndt.smartmovie.remote.WatchContextKind
import com.lamndt.smartmovie.remote.WatchRemoteState
import com.lamndt.smartmovie.remote.WatchTitleContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WearRemoteViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun pairedActivePhoneEnablesControls() = runTest(dispatcher) {
        val gateway = FakeGateway().apply {
            phoneConnected.value = true
            remoteState.value = WatchRemoteState(title, phoneActive = true)
        }
        val viewModel = WearRemoteViewModel(gateway)

        advanceUntilIdle()

        assertThat(viewModel.state.value.controlsEnabled).isTrue()
        assertThat(viewModel.state.value.context).isEqualTo(title)
    }

    @Test
    fun favoriteCommandAppliesPhoneResponse() = runTest(dispatcher) {
        val gateway = FakeGateway().apply {
            phoneConnected.value = true
            remoteState.value = WatchRemoteState(title, phoneActive = true)
            responseContext = title.copy(favorite = true)
        }
        val viewModel = WearRemoteViewModel(gateway)
        advanceUntilIdle()

        viewModel.send(WatchCommand.TOGGLE_FAVORITE)
        advanceUntilIdle()

        assertThat(gateway.lastRequest?.command).isEqualTo(WatchCommand.TOGGLE_FAVORITE)
        assertThat(gateway.lastRequest?.contextKey).isEqualTo(title.contextKey)
        assertThat(viewModel.state.value.context?.favorite).isTrue()
        assertThat(viewModel.state.value.commandFailed).isFalse()
    }

    @Test
    fun disconnectedPhoneDoesNotSendCommand() = runTest(dispatcher) {
        val gateway = FakeGateway().apply { remoteState.value = WatchRemoteState(title, phoneActive = true) }
        val viewModel = WearRemoteViewModel(gateway)
        advanceUntilIdle()

        viewModel.send(WatchCommand.PLAY_TRAILER)
        advanceUntilIdle()

        assertThat(gateway.lastRequest).isNull()
    }

    @Test
    fun episodeContextOnlySendsExactDetailHandoff() = runTest(dispatcher) {
        val episode = title.copy(
            contextKey = "episode:1399:1:1",
            contextKind = WatchContextKind.EPISODE,
            title = "Winter Is Coming",
            mediaType = "episode",
            seriesTitle = "Game of Thrones",
            seasonNumber = 1,
            episodeNumber = 1,
            trailerAvailable = false,
            libraryActionsAvailable = false,
        )
        val gateway = FakeGateway().apply {
            phoneConnected.value = true
            remoteState.value = WatchRemoteState(episode, phoneActive = true)
        }
        val viewModel = WearRemoteViewModel(gateway)
        advanceUntilIdle()

        viewModel.send(WatchCommand.PLAY_TRAILER)
        viewModel.send(WatchCommand.TOGGLE_FAVORITE)
        advanceUntilIdle()
        assertThat(gateway.lastRequest).isNull()

        viewModel.send(WatchCommand.OPEN_DETAILS)
        advanceUntilIdle()
        assertThat(gateway.lastRequest?.contextKey).isEqualTo("episode:1399:1:1")
        assertThat(gateway.lastRequest?.command).isEqualTo(WatchCommand.OPEN_DETAILS)
    }

    private class FakeGateway : WearRemoteGateway {
        override val remoteState = MutableStateFlow(WatchRemoteState())
        override val phoneConnected = MutableStateFlow(false)
        var lastRequest: WatchCommandRequest? = null
        var responseContext: WatchTitleContext? = null

        override suspend fun send(request: WatchCommandRequest): WatchCommandResponse {
            lastRequest = request
            return WatchCommandResponse(request.requestId, accepted = true, context = responseContext)
        }

        override fun close() = Unit
    }

    companion object {
        private val title = WatchTitleContext(
            libraryKey = "movie:550",
            title = "Fight Club",
            mediaType = "movie",
            year = "1999",
            rating = 8.4,
            trailerAvailable = true,
        )
    }
}
