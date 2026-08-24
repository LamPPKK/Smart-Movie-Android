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

    fun refresh(language: String) = scope.launch {
        val profile = runCatching { account.profile() }.getOrNull()
        if (profile == null) mutableState.value = AccountSessionState.SignedOut
        else {
            mutableState.value = AccountSessionState.SignedIn(profile)
            sync(profile, language)
        }
    }

    suspend fun begin(returnUri: String, mode: String): AuthAttempt? = runCatching {
        polling?.cancel()
        account.createAuthAttempt(returnUri, mode).also { attempt ->
            mutableState.value = AccountSessionState.Authorizing(attempt)
            if (mode == "tv") poll(attempt)
        }
    }.onFailure { mutableState.value = AccountSessionState.Failed(it.message ?: "Unable to start TMDb authorization") }.getOrNull()

    fun handleCallback(attemptId: String, language: String) = scope.launch { complete(attemptId, null, language) }

    suspend fun complete(attemptId: String, deviceCode: String?, language: String) {
        runCatching { account.completeAuth(attemptId, deviceCode) }
            .onSuccess {
                mutableState.value = AccountSessionState.SignedIn(it.profile)
                sync(it.profile, language)
            }
            .onFailure { mutableState.value = AccountSessionState.Failed(it.message ?: "Unable to finish TMDb authorization") }
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
        for (mutation in library.pendingMutations()) {
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
        val profile = (mutableState.value as? AccountSessionState.SignedIn)?.profile ?: return null
        return accountOutbox.flush(profile.id).also { report ->
            if (report.delivered.isNotEmpty()) mutableMutationRevision.value++
        }
    }

    private suspend fun sync(profile: AccountProfile, language: String) {
        library.activateAccount(profile.id)
        for (collection in LibraryCollection.entries) for (mediaType in MediaType.entries) {
            val values = buildList {
                var page = 1
                do {
                    val result = account.library(collection, mediaType, page, language)
                    addAll(result.results)
                    page++
                } while (page <= result.totalPages.coerceAtMost(500))
            }
            library.mergeRemote(values, collection, mediaType, profile.id)
        }
        flushOutbox()
    }

    private fun poll(attempt: AuthAttempt) {
        polling = scope.launch {
            val interval = (attempt.pollingInterval ?: 5).coerceAtLeast(5) * 1_000L
            val expiry = runCatching { Instant.parse(attempt.expiresAt) }.getOrDefault(Instant.now().plusSeconds(900))
            while (Instant.now().isBefore(expiry)) {
                delay(interval)
                when (runCatching { account.authAttempt(attempt.attemptId, attempt.deviceCode) }.getOrNull()) {
                    "approved" -> { complete(attempt.attemptId, attempt.deviceCode, "en-US"); return@launch }
                    "denied", "expired" -> { mutableState.value = AccountSessionState.SignedOut; return@launch }
                }
            }
            mutableState.value = AccountSessionState.SignedOut
        }
    }
}
