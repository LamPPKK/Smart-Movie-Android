package com.lamndt.smartmovie.wear

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.lamndt.smartmovie.remote.WatchCommandRequest
import com.lamndt.smartmovie.remote.WatchCommandResponse
import com.lamndt.smartmovie.remote.WatchRemoteCodec
import com.lamndt.smartmovie.remote.WatchRemotePaths
import com.lamndt.smartmovie.remote.WatchRemoteState
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

internal interface WearRemoteGateway {
    val remoteState: StateFlow<WatchRemoteState>
    val phoneConnected: StateFlow<Boolean>
    suspend fun send(request: WatchCommandRequest): WatchCommandResponse
    fun close()
}

internal class PlayServicesWearRemoteGateway(context: Context) :
    WearRemoteGateway,
    CapabilityClient.OnCapabilityChangedListener,
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataClient = Wearable.getDataClient(context.applicationContext)
    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val capabilityClient = Wearable.getCapabilityClient(context.applicationContext)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<WatchCommandResponse>>()
    private val mutableRemoteState = MutableStateFlow(WatchRemoteState())
    private val mutablePhoneConnected = MutableStateFlow(false)

    override val remoteState: StateFlow<WatchRemoteState> = mutableRemoteState.asStateFlow()
    override val phoneConnected: StateFlow<Boolean> = mutablePhoneConnected.asStateFlow()

    init {
        dataClient.addListener(this)
        messageClient.addListener(this)
        capabilityClient.addListener(this, WatchRemotePaths.PHONE_CAPABILITY)
        scope.launch { refresh() }
    }

    override suspend fun send(request: WatchCommandRequest): WatchCommandResponse {
        val nodes = reachablePhoneNodes()
        mutablePhoneConnected.value = nodes.isNotEmpty()
        val node = nodes.preferred() ?: return WatchCommandResponse(
            requestId = request.requestId,
            accepted = false,
            message = "Phone unavailable",
        )
        val response = CompletableDeferred<WatchCommandResponse>()
        pending[request.requestId] = response
        return try {
            messageClient.sendMessage(
                node.id,
                WatchRemotePaths.COMMAND,
                WatchRemoteCodec.encodeRequest(request),
            ).await()
            withTimeout(5_000) { response.await() }
        } finally {
            pending.remove(request.requestId)
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events
            .asSequence()
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WatchRemotePaths.STATE }
            .mapNotNull { event ->
                DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray(WatchRemotePaths.PAYLOAD)
            }
            .mapNotNull { runCatching { WatchRemoteCodec.decodeState(it) }.getOrNull() }
            .lastOrNull()
            ?.let { mutableRemoteState.value = it }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchRemotePaths.RESPONSE) return
        val response = runCatching { WatchRemoteCodec.decodeResponse(event.data) }.getOrNull() ?: return
        response.context?.let { context ->
            mutableRemoteState.value = mutableRemoteState.value.copy(context = context)
        }
        pending[response.requestId]?.complete(response)
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name == WatchRemotePaths.PHONE_CAPABILITY) {
            mutablePhoneConnected.value = info.nodes.isNotEmpty()
        }
    }

    override fun close() {
        dataClient.removeListener(this)
        messageClient.removeListener(this)
        capabilityClient.removeListener(this, WatchRemotePaths.PHONE_CAPABILITY)
        pending.values.forEach { it.cancel() }
        scope.cancel()
    }

    private suspend fun refresh() {
        mutablePhoneConnected.value = reachablePhoneNodes().isNotEmpty()
        val items = runCatching { dataClient.dataItems.await() }.getOrNull() ?: return
        try {
            items
                .asSequence()
                .filter { it.uri.path == WatchRemotePaths.STATE }
                .mapNotNull { DataMapItem.fromDataItem(it).dataMap.getByteArray(WatchRemotePaths.PAYLOAD) }
                .mapNotNull { runCatching { WatchRemoteCodec.decodeState(it) }.getOrNull() }
                .lastOrNull()
                ?.let { mutableRemoteState.value = it }
        } finally {
            items.release()
        }
    }

    private suspend fun reachablePhoneNodes(): Set<Node> = runCatching {
        capabilityClient.getCapability(
            WatchRemotePaths.PHONE_CAPABILITY,
            CapabilityClient.FILTER_REACHABLE,
        ).await().nodes
    }.getOrDefault(emptySet())
}

private fun Collection<Node>.preferred(): Node? = firstOrNull(Node::isNearby) ?: firstOrNull()
