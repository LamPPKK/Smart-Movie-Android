package com.lamndt.smartmovie.wear

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lamndt.smartmovie.remote.WatchCommand
import com.lamndt.smartmovie.remote.WatchCommandRequest
import com.lamndt.smartmovie.remote.WatchContextKind
import com.lamndt.smartmovie.remote.WatchTitleContext
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class WearRemoteUiState(
    val context: WatchTitleContext? = null,
    val phoneConnected: Boolean = false,
    val phoneActive: Boolean = false,
    val isSending: Boolean = false,
    val commandFailed: Boolean = false,
) {
    val controlsEnabled: Boolean get() = context != null && phoneConnected && phoneActive && !isSending
}

internal class WearRemoteViewModel(
    private val gateway: WearRemoteGateway,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WearRemoteUiState())
    val state: StateFlow<WearRemoteUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(gateway.remoteState, gateway.phoneConnected) { remote, connected -> remote to connected }
                .collect { (remote, connected) ->
                    mutableState.update {
                        it.copy(
                            context = remote.context,
                            phoneConnected = connected,
                            phoneActive = remote.phoneActive,
                        )
                    }
                }
        }
    }

    fun send(command: WatchCommand) {
        val title = mutableState.value.context ?: return
        if (!mutableState.value.controlsEnabled) return
        if (
            command == WatchCommand.PLAY_TRAILER &&
            title.contextKind != WatchContextKind.TITLE
        ) return
        if (
            command in setOf(WatchCommand.TOGGLE_FAVORITE, WatchCommand.TOGGLE_WATCHLIST) &&
            !title.libraryActionsAvailable
        ) return
        viewModelScope.launch {
            mutableState.update { it.copy(isSending = true, commandFailed = false) }
            try {
                val response = gateway.send(
                    WatchCommandRequest(
                        requestId = UUID.randomUUID().toString(),
                        libraryKey = title.libraryKey,
                        command = command,
                        contextKey = title.contextKey,
                    ),
                )
                mutableState.update {
                    it.copy(
                        context = response.context ?: it.context,
                        isSending = false,
                        commandFailed = !response.accepted,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                mutableState.update { it.copy(isSending = false, commandFailed = true) }
            }
        }
    }

    override fun onCleared() {
        gateway.close()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WearRemoteViewModel(PlayServicesWearRemoteGateway(context.applicationContext)) as T
        }
    }
}
