package com.lamndt.smartmovie.data

import androidx.room.withTransaction
import com.lamndt.smartmovie.database.AccountMutationOutboxEntity
import com.lamndt.smartmovie.database.SmartMovieDatabase
import com.lamndt.smartmovie.model.AccountMutationFlushReport
import com.lamndt.smartmovie.model.AccountMutationOutbox
import com.lamndt.smartmovie.model.AccountMutationPayload
import com.lamndt.smartmovie.model.AccountRepository
import com.lamndt.smartmovie.model.CatalogException
import com.lamndt.smartmovie.model.MutationResult
import com.lamndt.smartmovie.model.PendingAccountMutation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class DurableAccountMutationOutbox(
    private val database: SmartMovieDatabase,
    private val account: AccountRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString().lowercase() },
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        classDiscriminator = "type"
    },
) : AccountMutationOutbox {
    private val dao = database.libraryDao()
    private val flushMutex = Mutex()

    override suspend fun enqueue(
        accountId: Int,
        payload: AccountMutationPayload,
        id: String?,
    ): PendingAccountMutation {
        val mutation = PendingAccountMutation(
            id = id ?: idFactory(),
            accountId = accountId,
            payload = payload,
            createdAt = clock(),
            attemptCount = 0,
            lastAttemptAt = null,
            lastError = null,
        )
        dao.upsertAccountMutation(mutation.toEntity())
        return mutation
    }

    override suspend fun flush(accountId: Int, limit: Int): AccountMutationFlushReport = flushMutex.withLock {
        val delivered = linkedMapOf<String, MutationResult>()
        for (mutation in pending(accountId, limit)) {
            try {
                val result = dispatch(mutation)
                if (result.mutationId != mutation.id) {
                    throw CatalogException(
                        CatalogException.Kind.DECODING,
                        "The mutation acknowledgement did not match its idempotency key.",
                    )
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

    override suspend fun pending(accountId: Int, limit: Int): List<PendingAccountMutation> =
        dao.pendingAccountMutations(accountId, limit.coerceAtLeast(0)).map { entity -> entity.toModel() }

    override suspend fun cancel(id: String) {
        val mutation = dao.accountMutation(id) ?: return
        dao.deleteAccountMutation(mutation)
    }

    override suspend fun clear(accountId: Int) {
        dao.clearAccountMutations(accountId)
    }

    suspend fun pendingTitleRating(accountId: Int, mediaType: String, mediaId: Int): PendingAccountMutation? =
        pending(accountId).lastOrNull { mutation ->
            val payload = mutation.payload as? AccountMutationPayload.TitleRating
            payload?.mediaType?.wireValue == mediaType && payload.mediaId == mediaId
        }

    suspend fun pendingEpisodeRating(
        accountId: Int,
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
    ): PendingAccountMutation? = pending(accountId).lastOrNull { mutation ->
        val payload = mutation.payload as? AccountMutationPayload.EpisodeRating
        payload?.seriesId == seriesId && payload.seasonNumber == seasonNumber && payload.episodeNumber == episodeNumber
    }

    private suspend fun fail(id: String, message: String) {
        database.withTransaction {
            val current = dao.accountMutation(id) ?: return@withTransaction
            dao.upsertAccountMutation(
                current.copy(
                    attemptCount = current.attemptCount + 1,
                    lastAttemptAt = clock(),
                    lastError = message.take(500),
                ),
            )
        }
    }

    private suspend fun dispatch(mutation: PendingAccountMutation): MutationResult = when (val payload = mutation.payload) {
        is AccountMutationPayload.TitleRating -> account.setRating(
            payload.mediaType,
            payload.mediaId,
            payload.value,
            mutation.id,
        )
        is AccountMutationPayload.EpisodeRating -> account.setEpisodeRating(
            payload.seriesId,
            payload.seasonNumber,
            payload.episodeNumber,
            payload.value,
            mutation.id,
        )
        is AccountMutationPayload.CreateList -> account.createList(
            payload.name,
            payload.description,
            payload.isPublic,
            payload.region,
            payload.language,
            mutation.id,
        )
        is AccountMutationPayload.UpdateList -> account.updateList(
            payload.listId,
            payload.name,
            payload.description,
            payload.isPublic,
            mutation.id,
        )
        is AccountMutationPayload.DeleteList -> account.deleteList(payload.listId, mutation.id)
        is AccountMutationPayload.MutateListItems -> account.mutateListItems(
            payload.listId,
            payload.items,
            payload.remove,
            mutation.id,
        )
    }

    private fun PendingAccountMutation.toEntity() = AccountMutationOutboxEntity(
        mutationId = id,
        accountId = accountId,
        payloadJson = json.encodeToString(payload),
        createdAt = createdAt,
        attemptCount = attemptCount,
        lastAttemptAt = lastAttemptAt,
        lastError = lastError,
    )

    private fun AccountMutationOutboxEntity.toModel() = PendingAccountMutation(
        id = mutationId,
        accountId = accountId,
        payload = json.decodeFromString(payloadJson),
        createdAt = createdAt,
        attemptCount = attemptCount,
        lastAttemptAt = lastAttemptAt,
        lastError = lastError,
    )
}
