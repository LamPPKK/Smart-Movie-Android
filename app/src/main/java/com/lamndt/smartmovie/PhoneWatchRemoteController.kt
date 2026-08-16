package com.lamndt.smartmovie

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibraryMembership
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.remote.WatchCommand
import com.lamndt.smartmovie.remote.WatchCommandRequest
import com.lamndt.smartmovie.remote.WatchCommandResponse
import com.lamndt.smartmovie.remote.WatchRemoteCodec
import com.lamndt.smartmovie.remote.WatchRemotePaths
import com.lamndt.smartmovie.remote.WatchRemoteState
import com.lamndt.smartmovie.remote.WatchTitleContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

sealed interface PhoneRemoteAction {
    data class OpenDetails(val title: TitleSummary) : PhoneRemoteAction
    data class PlayTrailer(val youtubeKey: String) : PhoneRemoteAction
}

class PhoneWatchRemoteController(
    context: Context,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
) : MessageClient.OnMessageReceivedListener {
    private val dataClient = Wearable.getDataClient(context.applicationContext)
    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val commandMutex = Mutex()
    private val mutableActions = MutableSharedFlow<PhoneRemoteAction>(extraBufferCapacity = 1)
    val actions: SharedFlow<PhoneRemoteAction> = mutableActions.asSharedFlow()

    @Volatile private var phoneActive = false
    @Volatile private var activeDetail: ActiveDetail? = null

    init {
        messageClient.addListener(this)
    }

    fun setPhoneActive(active: Boolean) {
        phoneActive = active
        publishState()
    }

    fun publish(
        title: TitleSummary,
        membership: LibraryMembership,
        trailerKey: String?,
        artworkUrl: String?,
    ) {
        val context = WatchTitleContext(
            libraryKey = title.libraryKey,
            title = title.displayTitle,
            mediaType = title.mediaType.wireValue,
            year = title.releaseYear,
            artworkUrl = artworkUrl,
            rating = title.voteAverage,
            trailerAvailable = trailerKey != null,
            favorite = membership.isFavorite,
            watchlist = membership.isWatchlisted,
        )
        activeDetail = ActiveDetail(title, trailerKey, context)
        publishState()
    }

    fun clear(libraryKey: String) {
        if (activeDetail?.title?.libraryKey == libraryKey) {
            activeDetail = null
            publishState()
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchRemotePaths.COMMAND) return
        val request = runCatching { WatchRemoteCodec.decodeRequest(event.data) }.getOrNull() ?: return
        scope.launch {
            val response = commandMutex.withLock { handle(request) }
            runCatching {
                messageClient.sendMessage(
                    event.sourceNodeId,
                    WatchRemotePaths.RESPONSE,
                    WatchRemoteCodec.encodeResponse(response),
                ).await()
            }
        }
    }

    private suspend fun handle(request: WatchCommandRequest): WatchCommandResponse {
        val current = activeDetail
        if (!phoneActive) return request.rejected("Open SmartMovie on your phone")
        if (current == null || current.title.libraryKey != request.libraryKey) {
            return request.rejected("Open this title on your phone")
        }

        return when (request.command) {
            WatchCommand.OPEN_DETAILS -> {
                mutableActions.emit(PhoneRemoteAction.OpenDetails(current.title))
                request.accepted(current.context)
            }
            WatchCommand.PLAY_TRAILER -> {
                val key = current.trailerKey ?: return request.rejected("Trailer unavailable")
                mutableActions.emit(PhoneRemoteAction.PlayTrailer(key))
                request.accepted(current.context)
            }
            WatchCommand.TOGGLE_FAVORITE -> toggle(current, request, LibraryCollection.FAVORITES)
            WatchCommand.TOGGLE_WATCHLIST -> toggle(current, request, LibraryCollection.WATCHLIST)
        }
    }

    private suspend fun toggle(
        current: ActiveDetail,
        request: WatchCommandRequest,
        collection: LibraryCollection,
    ): WatchCommandResponse {
        library.toggle(current.title, collection)
        val updatedContext = when (collection) {
            LibraryCollection.FAVORITES -> current.context.copy(favorite = !current.context.favorite)
            LibraryCollection.WATCHLIST -> current.context.copy(watchlist = !current.context.watchlist)
        }
        activeDetail = current.copy(context = updatedContext)
        uploadState()
        return request.accepted(updatedContext)
    }

    private fun publishState() {
        scope.launch { uploadState() }
    }

    private suspend fun uploadState() {
        val request = PutDataMapRequest.create(WatchRemotePaths.STATE).apply {
            dataMap.putByteArray(
                WatchRemotePaths.PAYLOAD,
                WatchRemoteCodec.encodeState(WatchRemoteState(activeDetail?.context, phoneActive)),
            )
            dataMap.putLong("updated_at", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        runCatching { dataClient.putDataItem(request).await() }
    }

    private data class ActiveDetail(
        val title: TitleSummary,
        val trailerKey: String?,
        val context: WatchTitleContext,
    )
}

private fun WatchCommandRequest.accepted(context: WatchTitleContext) = WatchCommandResponse(
    requestId = requestId,
    accepted = true,
    context = context,
)

private fun WatchCommandRequest.rejected(message: String) = WatchCommandResponse(
    requestId = requestId,
    accepted = false,
    message = message,
)
