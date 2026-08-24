package com.lamndt.smartmovie.multiplatform.data

import com.lamndt.smartmovie.multiplatform.model.MediaType
import com.lamndt.smartmovie.multiplatform.model.MutationResult
import com.lamndt.smartmovie.multiplatform.model.TitleSummary
import com.lamndt.smartmovie.multiplatform.model.UserList
import com.lamndt.smartmovie.multiplatform.platform.KeyValueStore
import com.lamndt.smartmovie.multiplatform.platform.systemTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed interface AccountMutationPayload {
    @Serializable
    @SerialName("title_rating")
    data class TitleRating(val mediaType: MediaType, val mediaId: Int, val value: Double?) : AccountMutationPayload

    @Serializable
    @SerialName("episode_rating")
    data class EpisodeRating(
        val seriesId: Int,
        val seasonNumber: Int,
        val episodeNumber: Int,
        val value: Double?,
    ) : AccountMutationPayload

    @Serializable
    @SerialName("create_list")
    data class CreateList(
        val name: String,
        val description: String,
        val public: Boolean,
        val region: String,
        val language: String,
    ) : AccountMutationPayload

    @Serializable
    @SerialName("update_list")
    data class UpdateList(
        val listId: Int,
        val name: String,
        val description: String,
        val public: Boolean,
    ) : AccountMutationPayload

    @Serializable
    @SerialName("delete_list")
    data class DeleteList(val listId: Int) : AccountMutationPayload

    @Serializable
    @SerialName("mutate_list_items")
    data class MutateListItems(
        val listId: Int,
        val items: List<ListItemMutation>,
        val titles: List<TitleSummary> = emptyList(),
        val remove: Boolean,
    ) : AccountMutationPayload
}

@Serializable
data class PendingAccountMutation(
    val id: String,
    val accountId: Int,
    val payload: AccountMutationPayload,
    val createdAt: Long,
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
) {
    val localListId: Int?
        get() = if (payload is AccountMutationPayload.CreateList) {
            val seed = id.replace("-", "").take(8).toLongOrNull(16) ?: 1L
            -((seed and 0x3fff_ffffL).coerceAtLeast(1L).toInt())
        } else null
}

data class AccountMutationFlushReport(
    val delivered: Map<String, MutationResult> = emptyMap(),
    val failure: String? = null,
)

class PersistentAccountMutationOutbox(
    private val store: KeyValueStore,
    private val clock: () -> Long = ::systemTimeMillis,
    private val idFactory: () -> String = ::createInstallationId,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        classDiscriminator = "type"
    },
) {
    private val mutableMutations = MutableStateFlow(read())
    private val flushMutex = Mutex()
    val mutations: StateFlow<List<PendingAccountMutation>> = mutableMutations.asStateFlow()

    fun enqueue(
        accountId: Int,
        payload: AccountMutationPayload,
        id: String = idFactory(),
    ): PendingAccountMutation = PendingAccountMutation(
        id = id,
        accountId = accountId,
        payload = payload,
        createdAt = clock(),
    ).also { mutation ->
        mutableMutations.value = mutableMutations.value + mutation
        persist()
    }

    fun pending(accountId: Int, limit: Int = 500): List<PendingAccountMutation> = mutableMutations.value
        .asSequence()
        .filter { it.accountId == accountId }
        .sortedWith(compareBy(PendingAccountMutation::createdAt, PendingAccountMutation::id))
        .take(limit.coerceAtLeast(0))
        .toList()

    fun cancel(id: String) {
        mutableMutations.value = mutableMutations.value.filterNot { it.id == id }
        persist()
    }

    fun clear(accountId: Int) {
        mutableMutations.value = mutableMutations.value.filterNot { it.accountId == accountId }
        persist()
    }

    suspend fun flush(
        accountId: Int,
        limit: Int = 100,
        dispatch: suspend (PendingAccountMutation) -> MutationResult,
    ): AccountMutationFlushReport = flushMutex.withLock {
        val delivered = linkedMapOf<String, MutationResult>()
        for (mutation in pending(accountId, limit)) {
            try {
                val result = dispatch(mutation)
                check(result.mutationId == mutation.id) {
                    "The mutation acknowledgement did not match its idempotency key."
                }
                cancel(mutation.id)
                delivered[mutation.id] = result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                fail(mutation.id, error.message ?: "Account service unavailable")
                return@withLock AccountMutationFlushReport(delivered, error.message)
            }
        }
        AccountMutationFlushReport(delivered)
    }

    private fun fail(id: String, message: String) {
        mutableMutations.value = mutableMutations.value.map { mutation ->
            if (mutation.id == id) mutation.copy(
                attemptCount = mutation.attemptCount + 1,
                lastAttemptAt = clock(),
                lastError = message.take(500),
            ) else mutation
        }
        persist()
    }

    private fun read(): List<PendingAccountMutation> = store.getString(STORE_KEY)
        ?.let { runCatching { json.decodeFromString<List<PendingAccountMutation>>(it) }.getOrNull() }
        .orEmpty()

    private fun persist() {
        store.putString(STORE_KEY, json.encodeToString(mutableMutations.value))
    }

    private companion object {
        const val STORE_KEY = "smartmovie_account_mutation_outbox_v1"
    }
}

fun applyPendingLists(remote: List<UserList>, pending: List<PendingAccountMutation>): List<UserList> {
    var result = remote
    pending.sortedBy(PendingAccountMutation::createdAt).forEach { mutation ->
        when (val payload = mutation.payload) {
            is AccountMutationPayload.CreateList -> {
                val localId = mutation.localListId ?: return@forEach
                result = result.filterNot { it.id == localId } + UserList(
                    id = localId,
                    name = payload.name,
                    description = payload.description,
                    public = payload.public,
                )
            }
            is AccountMutationPayload.UpdateList -> result = result.map { list ->
                if (list.id == payload.listId) list.copy(
                    name = payload.name,
                    description = payload.description,
                    public = payload.public,
                ) else list
            }
            is AccountMutationPayload.DeleteList -> result = result.filterNot { it.id == payload.listId }
            is AccountMutationPayload.MutateListItems -> result = result.map { list ->
                if (list.id != payload.listId) return@map list
                val keys = payload.items.mapTo(hashSetOf()) { "${it.mediaType}:${it.mediaId}" }
                val results = if (payload.remove) {
                    list.results.filterNot { it.libraryKey in keys }
                } else {
                    (list.results + payload.titles.filter { it.libraryKey in keys })
                        .distinctBy(TitleSummary::libraryKey)
                }
                list.copy(results = results)
            }
            is AccountMutationPayload.EpisodeRating,
            is AccountMutationPayload.TitleRating,
            -> Unit
        }
    }
    return result
}

fun applyPendingListDetail(
    remote: UserList,
    pending: List<PendingAccountMutation>,
    includeAdult: Boolean = true,
): UserList? = applyPendingLists(listOf(remote), pending)
    .firstOrNull { it.id == remote.id }
    ?.let { list -> list.copy(results = list.results.filter { includeAdult || !it.adult }) }
