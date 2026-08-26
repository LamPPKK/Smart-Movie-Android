package com.lamndt.smartmovie

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.AccountMutationFlushReport
import com.lamndt.smartmovie.model.AccountMutationOutbox
import com.lamndt.smartmovie.model.AccountMutationPayload
import com.lamndt.smartmovie.model.AccountProfile
import com.lamndt.smartmovie.model.AccountRepository
import com.lamndt.smartmovie.model.AdultContentCapability
import com.lamndt.smartmovie.model.AuthAttempt
import com.lamndt.smartmovie.model.AuthSession
import com.lamndt.smartmovie.model.CapabilitiesV2
import com.lamndt.smartmovie.model.EpisodeAccountState
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibraryMembership
import com.lamndt.smartmovie.model.LibrarySnapshot
import com.lamndt.smartmovie.model.LibrarySort
import com.lamndt.smartmovie.model.LibrarySyncRepository
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.MutationResult
import com.lamndt.smartmovie.model.PagedResult
import com.lamndt.smartmovie.model.PendingAccountMutation
import com.lamndt.smartmovie.model.PendingLibraryMutation
import com.lamndt.smartmovie.model.TitleAccountState
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.model.UserList
import com.lamndt.smartmovie.model.UserListItemMutation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountCapabilityGateTest {
    @Test
    fun callbackBeforeResolutionFailsClosedForNilAndFalseAndCompletesForTrue() {
        val unavailable = mutableListOf<String>()
        val unavailableGate = AccountCapabilityGate(isTelevision = false)
        unavailableGate.submit("nil-attempt", "en-US") { attempt, _ -> unavailable += attempt }
        unavailableGate.resolve(null, {}, {}, {}, { attempt, _ -> unavailable += attempt })
        assertThat(unavailable).isEmpty()

        val disabled = mutableListOf<String>()
        val disabledGate = AccountCapabilityGate(isTelevision = false)
        disabledGate.submit("false-attempt", "en-US") { attempt, _ -> disabled += attempt }
        disabledGate.resolve(capabilities(browser = false), {}, {}, {}, { attempt, _ -> disabled += attempt })
        assertThat(disabled).isEmpty()

        val enabled = mutableListOf<String>()
        val enabledGate = AccountCapabilityGate(isTelevision = false)
        enabledGate.submit("true-attempt", "vi-VN") { attempt, _ -> enabled += attempt }
        enabledGate.resolve(capabilities(browser = true), {}, {}, {}, { attempt, _ -> enabled += attempt })
        assertThat(enabled).containsExactly("true-attempt")
    }

    @Test
    fun phoneAndTelevisionRequireTheirOwnCapability() {
        var phoneEnabled = 0
        AccountCapabilityGate(isTelevision = false).resolve(
            capabilities(browser = true, television = false),
            onEnabled = { phoneEnabled++ },
            onDisabled = {},
            onRefresh = {},
            onCallback = { _, _ -> },
        )
        var televisionDisabled = 0
        AccountCapabilityGate(isTelevision = true).resolve(
            capabilities(browser = true, television = false),
            onEnabled = {},
            onDisabled = { televisionDisabled++ },
            onRefresh = {},
            onCallback = { _, _ -> },
        )

        assertThat(phoneEnabled).isEqualTo(1)
        assertThat(televisionDisabled).isEqualTo(1)
    }

    @Test
    fun disabledControllerDoesNotCompleteCallbackOrFlushDurableLibraryMutation() = runTest {
        val account = RecordingAccountRepository()
        val library = RecordingLibrarySyncRepository()
        val controller = AccountSessionController(account, library, EmptyAccountOutbox(), backgroundScope)

        controller.complete("attempt", null, "en-US")
        controller.flushOutbox()
        advanceUntilIdle()

        assertThat(account.completeCalls).isEqualTo(0)
        assertThat(account.libraryMutationCalls).isEqualTo(0)
        assertThat(library.pendingReads).isEqualTo(0)
        assertThat(controller.state.value).isEqualTo(AccountSessionState.SignedOut)
    }

    @Test
    fun disableInvalidatesCompletionAlreadyInFlight() = runTest {
        val account = RecordingAccountRepository()
        val controller = AccountSessionController(
            account,
            RecordingLibrarySyncRepository(),
            EmptyAccountOutbox(),
            backgroundScope,
        )
        controller.enable()
        val completion = CompletableDeferred<Unit>()
        account.completionRelease = completion

        controller.handleCallback("attempt", "en-US")
        account.completionStarted.await()
        controller.disable()
        completion.complete(Unit)
        advanceUntilIdle()

        assertThat(account.completeCalls).isEqualTo(1)
        assertThat(controller.state.value).isEqualTo(AccountSessionState.SignedOut)
    }
}

private fun capabilities(browser: Boolean, television: Boolean = browser) = CapabilitiesV2(
    apiVersion = "v2",
    releaseTrain = "3.0.0",
    catalog = emptyMap(),
    account = mapOf("browser_auth" to browser, "tv_qr_auth" to television),
    supportedLanguages = emptyList(),
    supportedEntityKinds = emptyList(),
    adultContent = AdultContentCapability(false, false, true),
)

private class RecordingAccountRepository : AccountRepository {
    var completeCalls = 0
    var libraryMutationCalls = 0
    val completionStarted = CompletableDeferred<Unit>()
    var completionRelease: CompletableDeferred<Unit>? = null

    override suspend fun completeAuth(id: String, deviceCode: String?): AuthSession {
        completeCalls++
        completionStarted.complete(Unit)
        completionRelease?.await()
        return AuthSession(
            csrfToken = "csrf",
            expiresAt = "2099-01-01T00:00:00Z",
            profile = AccountProfile(7, "fixture", "Fixture"),
        )
    }

    override suspend fun setLibrary(
        collection: LibraryCollection,
        mediaType: MediaType,
        mediaId: Int,
        enabled: Boolean,
        mutationId: String,
    ): MutationResult {
        libraryMutationCalls++
        return MutationResult(mutationId, success = true)
    }

    override suspend fun createAuthAttempt(returnUri: String, mode: String): AuthAttempt = unsupported()
    override suspend fun authAttempt(id: String, deviceCode: String?): String = unsupported()
    override suspend fun profile(): AccountProfile = unsupported()
    override suspend fun accountState(mediaType: MediaType, mediaId: Int): TitleAccountState = unsupported()
    override suspend fun episodeAccountState(seriesId: Int, season: Int, episode: Int): EpisodeAccountState = unsupported()
    override suspend fun logout() = Unit
    override suspend fun library(
        collection: LibraryCollection,
        mediaType: MediaType,
        page: Int,
        language: String,
    ): PagedResult<TitleSummary> = unsupported()
    override suspend fun setRating(mediaType: MediaType, mediaId: Int, value: Double?, mutationId: String): MutationResult = unsupported()
    override suspend fun setEpisodeRating(
        seriesId: Int,
        season: Int,
        episode: Int,
        value: Double?,
        mutationId: String,
    ): MutationResult = unsupported()
    override suspend fun recommendations(mediaType: MediaType, page: Int, language: String): PagedResult<TitleSummary> = unsupported()
    override suspend fun lists(page: Int): PagedResult<UserList> = unsupported()
    override suspend fun list(id: Int, page: Int, language: String): UserList = unsupported()
    override suspend fun createList(
        name: String,
        description: String,
        isPublic: Boolean,
        region: String,
        language: String,
        mutationId: String,
    ): MutationResult = unsupported()
    override suspend fun updateList(
        id: Int,
        name: String,
        description: String,
        isPublic: Boolean,
        mutationId: String,
    ): MutationResult = unsupported()
    override suspend fun deleteList(id: Int, mutationId: String): MutationResult = unsupported()
    override suspend fun mutateListItems(
        id: Int,
        items: List<UserListItemMutation>,
        remove: Boolean,
        mutationId: String,
    ): MutationResult = unsupported()

    private fun <T> unsupported(): T = error("Unexpected account request")
}

private class RecordingLibrarySyncRepository : LibrarySyncRepository {
    var pendingReads = 0
    override fun observeItems(collection: LibraryCollection, mediaType: MediaType?, sort: LibrarySort): Flow<List<LibrarySnapshot>> =
        flowOf(emptyList())
    override fun observeMembership(libraryKey: String): Flow<LibraryMembership> = flowOf(LibraryMembership())
    override suspend fun toggle(title: TitleSummary, collection: LibraryCollection) = Unit
    override suspend fun activateAccount(accountId: Int) = Unit
    override suspend fun deactivateAccount(removeAccountData: Boolean) = Unit
    override suspend fun mergeRemote(
        items: List<TitleSummary>,
        collection: LibraryCollection,
        mediaType: MediaType,
        accountId: Int,
    ) = Unit
    override suspend fun pendingMutations(limit: Int): List<PendingLibraryMutation> {
        pendingReads++
        return emptyList()
    }
    override suspend fun confirmMutation(id: String) = Unit
    override suspend fun failMutation(id: String, message: String) = Unit
}

private class EmptyAccountOutbox : AccountMutationOutbox {
    override suspend fun enqueue(accountId: Int, payload: AccountMutationPayload, id: String?): PendingAccountMutation =
        unsupported()
    override suspend fun flush(accountId: Int, limit: Int): AccountMutationFlushReport = AccountMutationFlushReport()
    override suspend fun pending(accountId: Int, limit: Int): List<PendingAccountMutation> = emptyList()
    override suspend fun cancel(id: String) = Unit
    override suspend fun clear(accountId: Int) = Unit
    private fun <T> unsupported(): T = error("Unexpected outbox request")
}
