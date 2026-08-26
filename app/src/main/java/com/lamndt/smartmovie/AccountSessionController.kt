package com.lamndt.smartmovie

import com.lamndt.smartmovie.model.AccountProfile
import com.lamndt.smartmovie.model.AccountMutationFlushReport
import com.lamndt.smartmovie.model.AccountMutationOutbox
import com.lamndt.smartmovie.model.AccountMutationPayload
import com.lamndt.smartmovie.model.AccountRepository
import com.lamndt.smartmovie.model.AuthAttempt
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibrarySyncRepository
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.PendingAccountMutation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

sealed interface AccountSessionState {
    data object Checking : AccountSessionState
    data object SignedOut : AccountSessionState
    data class Authorizing(val attempt: AuthAttempt) : AccountSessionState
    data class SignedIn(val profile: AccountProfile) : AccountSessionState
    data class Failed(val message: String) : AccountSessionState
}

class AccountSessionController(
    private val account: AccountRepository,
    private val library: LibrarySyncRepository,
    private val accountOutbox: AccountMutationOutbox,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<AccountSessionState>(AccountSessionState.Checking)
    val state: StateFlow<AccountSessionState> = mutableState.asStateFlow()
    private val mutableMutationRevision = MutableStateFlow(0L)
    val mutationRevision: StateFlow<Long> = mutableMutationRevision.asStateFlow()
    private var polling: Job? = null
    @Volatile private var enabled = false
    private val operationGeneration = AtomicLong(0)

    fun enable() {
        enabled = true
        operationGeneration.incrementAndGet()
    }

    fun refresh(language: String) {
        val generation = startOperation() ?: return
        scope.launch {
            val profile = runCatching { account.profile() }.getOrNull()
            if (!isCurrent(generation)) return@launch
            if (profile == null) {
                mutableState.value = AccountSessionState.SignedOut
            } else {
                mutableState.value = AccountSessionState.SignedIn(profile)
                sync(profile, language, generation)
            }
        }
    }

    fun disable() {
        enabled = false
        operationGeneration.incrementAndGet()
        polling?.cancel()
        mutableState.value = AccountSessionState.SignedOut
    }

    suspend fun begin(returnUri: String, mode: String): AuthAttempt? {
        val generation = startOperation() ?: return null
        return runCatching {
            polling?.cancel()
            val attempt = account.createAuthAttempt(returnUri, mode)
            if (!isCurrent(generation)) return null
            attempt.also {
                mutableState.value = AccountSessionState.Authorizing(attempt)
                if (mode == "tv") poll(attempt, generation)
            }
        }.onFailure {
            if (isCurrent(generation)) {
                mutableState.value = AccountSessionState.Failed(it.message ?: "Unable to start TMDb authorization")
            }
        }.getOrNull()
    }

    fun handleCallback(attemptId: String, language: String) {
        if (!enabled) return
        scope.launch { complete(attemptId, null, language) }
    }

    suspend fun complete(attemptId: String, deviceCode: String?, language: String) {
        val generation = startOperation() ?: return
        runCatching { account.completeAuth(attemptId, deviceCode) }
            .onSuccess {
                if (!isCurrent(generation)) return@onSuccess
                mutableState.value = AccountSessionState.SignedIn(it.profile)
                sync(it.profile, language, generation)
            }
            .onFailure {
                if (isCurrent(generation)) {
                    mutableState.value = AccountSessionState.Failed(it.message ?: "Unable to finish TMDb authorization")
                }
            }
    }

    suspend fun logout(removeAccountData: Boolean) {
        polling?.cancel()
        val accountId = (mutableState.value as? AccountSessionState.SignedIn)?.profile?.id
        runCatching { account.logout() }
        library.deactivateAccount(removeAccountData)
        if (removeAccountData && accountId != null) accountOutbox.clear(accountId)
        mutableMutationRevision.value++
        mutableState.value = AccountSessionState.SignedOut
    }

    suspend fun queueAccountMutation(payload: AccountMutationPayload): PendingAccountMutation {
        val profile = (mutableState.value as? AccountSessionState.SignedIn)?.profile
            ?: error("A TMDb account is required for this action.")
        val mutation = accountOutbox.enqueue(profile.id, payload)
        mutableMutationRevision.value++
        scope.launch { flushOutbox() }
        return mutation
    }

    suspend fun pendingAccountMutations(): List<PendingAccountMutation> {
        val profile = (mutableState.value as? AccountSessionState.SignedIn)?.profile ?: return emptyList()
        return accountOutbox.pending(profile.id)
    }

    suspend fun cancelLocalList(localListId: Int): Boolean {
        val mutation = pendingAccountMutations().firstOrNull { it.localListId == localListId } ?: return false
        accountOutbox.cancel(mutation.id)
        mutableMutationRevision.value++
        return true
    }

    suspend fun flushOutbox(): AccountMutationFlushReport? {
        val profile = (mutableState.value as? AccountSessionState.SignedIn)?.profile ?: return null
        if (!enabled) return null
        for (mutation in library.pendingMutations()) {
            if (!enabled || (mutableState.value as? AccountSessionState.SignedIn)?.profile?.id != profile.id) return null
            try {
                account.setLibrary(
                    mutation.collection, mutation.mediaType, mutation.mediaId, mutation.enabled, mutation.id,
                )
                library.confirmMutation(mutation.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                library.failMutation(mutation.id, error.message ?: "Account service unavailable")
                break
            }
        }
        if (!enabled || (mutableState.value as? AccountSessionState.SignedIn)?.profile?.id != profile.id) return null
        return accountOutbox.flush(profile.id).also { report ->
            if (report.delivered.isNotEmpty()) mutableMutationRevision.value++
        }
    }

    private suspend fun sync(profile: AccountProfile, language: String, generation: Long) {
        if (!isCurrent(generation)) return
        library.activateAccount(profile.id)
        for (collection in LibraryCollection.entries) for (mediaType in MediaType.entries) {
            if (!isCurrent(generation)) return
            val values = buildList {
                var page = 1
                do {
                    if (!isCurrent(generation)) return
                    val result = account.library(collection, mediaType, page, language)
                    addAll(result.results)
                    page++
                } while (page <= result.totalPages.coerceAtMost(500))
            }
            if (!isCurrent(generation)) return
            library.mergeRemote(values, collection, mediaType, profile.id)
        }
        flushOutbox()
    }

    private fun poll(attempt: AuthAttempt, generation: Long) {
        polling = scope.launch {
            val interval = (attempt.pollingInterval ?: 5).coerceAtLeast(5) * 1_000L
            val expiry = runCatching { Instant.parse(attempt.expiresAt) }.getOrDefault(Instant.now().plusSeconds(900))
            while (Instant.now().isBefore(expiry) && isCurrent(generation)) {
                delay(interval)
                if (!isCurrent(generation)) return@launch
                when (runCatching { account.authAttempt(attempt.attemptId, attempt.deviceCode) }.getOrNull()) {
                    "approved" -> { complete(attempt.attemptId, attempt.deviceCode, "en-US"); return@launch }
                    "denied", "expired" -> { mutableState.value = AccountSessionState.SignedOut; return@launch }
                }
            }
            if (!isCurrent(generation)) return@launch
            mutableState.value = AccountSessionState.SignedOut
        }
    }

    private fun startOperation(): Long? {
        if (!enabled) {
            mutableState.value = AccountSessionState.SignedOut
            return null
        }
        return operationGeneration.incrementAndGet()
    }

    private fun isCurrent(generation: Long): Boolean = enabled && operationGeneration.get() == generation
}
