package com.lamndt.smartmovie.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.database.SmartMovieDatabase
import com.lamndt.smartmovie.model.AccountMutationPayload
import com.lamndt.smartmovie.model.AccountProfile
import com.lamndt.smartmovie.model.AccountRepository
import com.lamndt.smartmovie.model.AuthAttempt
import com.lamndt.smartmovie.model.AuthSession
import com.lamndt.smartmovie.model.EpisodeAccountState
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.MutationResult
import com.lamndt.smartmovie.model.PagedResult
import com.lamndt.smartmovie.model.TitleAccountState
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.model.UserList
import com.lamndt.smartmovie.model.UserListItemMutation
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DurableAccountMutationOutboxTest {
    private lateinit var database: SmartMovieDatabase
    private lateinit var account: RecordingAccountRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SmartMovieDatabase::class.java,
        ).allowMainThreadQueries().build()
        account = RecordingAccountRepository()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun mutationsPersistAcrossOutboxInstancesAndRemainAccountScoped() = runTest {
        val first = outbox()
        first.enqueue(7, AccountMutationPayload.CreateList("Offline", "Saved locally", false, "VN", "vi"), CREATE_ID)
        first.enqueue(8, AccountMutationPayload.DeleteList(42), DELETE_ID)

        val restored = outbox()

        assertThat(restored.pending(7).map { it.id }).containsExactly(CREATE_ID)
        assertThat(restored.pending(7).single().localListId).isLessThan(0)
        assertThat(restored.pending(8).map { it.id }).containsExactly(DELETE_ID)
    }

    @Test
    fun retryKeepsStableIdempotencyKeyAndRecordsFailure() = runTest {
        account.failuresRemaining = 1
        val outbox = outbox()
        outbox.enqueue(7, AccountMutationPayload.TitleRating(MediaType.MOVIE, 11, 8.5), RATING_ID)

        val failed = outbox.flush(7)
        val pending = outbox.pending(7).single()
        assertThat(failed.failure).contains("offline")
        assertThat(pending.attemptCount).isEqualTo(1)
        assertThat(pending.lastAttemptAt).isEqualTo(1_001L)

        val delivered = outbox.flush(7)

        assertThat(delivered.delivered.keys).containsExactly(RATING_ID)
        assertThat(outbox.pending(7)).isEmpty()
        assertThat(account.receivedMutationIds).containsExactly(RATING_ID, RATING_ID).inOrder()
    }

    @Test
    fun mismatchedAcknowledgementNeverDeletesMutation() = runTest {
        account.acknowledgement = { "different-$it" }
        val outbox = outbox()
        outbox.enqueue(7, AccountMutationPayload.TitleRating(MediaType.TV, 22, null), RATING_ID)

        val report = outbox.flush(7)

        assertThat(report.delivered).isEmpty()
        assertThat(report.failure).contains("acknowledgement")
        assertThat(outbox.pending(7).single().attemptCount).isEqualTo(1)
    }

    private fun outbox() = DurableAccountMutationOutbox(
        database = database,
        account = account,
        clock = { now++ },
        idFactory = { error("Tests provide deterministic mutation IDs") },
    )

    private companion object {
        const val CREATE_ID = "01234567-0000-4000-8000-000000000001"
        const val DELETE_ID = "01234567-0000-4000-8000-000000000002"
        const val RATING_ID = "01234567-0000-4000-8000-000000000003"
    }
}

private class RecordingAccountRepository : AccountRepository {
    var failuresRemaining = 0
    var acknowledgement: (String) -> String = { it }
    val receivedMutationIds = mutableListOf<String>()

    override suspend fun setRating(
        mediaType: MediaType,
        mediaId: Int,
        value: Double?,
        mutationId: String,
    ): MutationResult {
        receivedMutationIds += mutationId
        if (failuresRemaining-- > 0) error("offline")
        return MutationResult(acknowledgement(mutationId), success = true)
    }

    override suspend fun logout() = Unit
    override suspend fun createAuthAttempt(returnUri: String, mode: String): AuthAttempt = unsupported()
    override suspend fun authAttempt(id: String, deviceCode: String?): String = unsupported()
    override suspend fun completeAuth(id: String, deviceCode: String?): AuthSession = unsupported()
    override suspend fun profile(): AccountProfile = unsupported()
    override suspend fun accountState(mediaType: MediaType, mediaId: Int): TitleAccountState = unsupported()
    override suspend fun episodeAccountState(seriesId: Int, season: Int, episode: Int): EpisodeAccountState = unsupported()
    override suspend fun library(
        collection: LibraryCollection,
        mediaType: MediaType,
        page: Int,
        language: String,
    ): PagedResult<TitleSummary> = unsupported()

    override suspend fun setLibrary(
        collection: LibraryCollection,
        mediaType: MediaType,
        mediaId: Int,
        enabled: Boolean,
        mutationId: String,
    ): MutationResult = unsupported()

    override suspend fun setEpisodeRating(
        seriesId: Int,
        season: Int,
        episode: Int,
        value: Double?,
        mutationId: String,
    ): MutationResult = unsupported()

    override suspend fun recommendations(mediaType: MediaType, page: Int, language: String): PagedResult<TitleSummary> =
        unsupported()

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

    private fun <T> unsupported(): T = error("Unexpected account API call")
}
