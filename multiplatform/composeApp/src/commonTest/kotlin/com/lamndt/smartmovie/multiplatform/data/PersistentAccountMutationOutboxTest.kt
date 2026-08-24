package com.lamndt.smartmovie.multiplatform.data

import com.lamndt.smartmovie.multiplatform.model.MediaType
import com.lamndt.smartmovie.multiplatform.model.MutationResult
import com.lamndt.smartmovie.multiplatform.model.UserList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersistentAccountMutationOutboxTest {
    @Test
    fun pendingMutationsPersistAndRemainAccountScoped() {
        val store = MemoryStore()
        val outbox = PersistentAccountMutationOutbox(store, clock = { 100L }, idFactory = { error("explicit id") })
        outbox.enqueue(7, AccountMutationPayload.CreateList("Offline", "Local", false, "VN", "vi"), CREATE_ID)
        outbox.enqueue(8, AccountMutationPayload.DeleteList(42), DELETE_ID)

        val restored = PersistentAccountMutationOutbox(store)

        assertEquals(listOf(CREATE_ID), restored.pending(7).map(PendingAccountMutation::id))
        assertTrue(requireNotNull(restored.pending(7).single().localListId) < 0)
        assertEquals(listOf(DELETE_ID), restored.pending(8).map(PendingAccountMutation::id))
    }

    @Test
    fun failedRetryKeepsStableIdAndAcknowledgementMustMatch() = runTest {
        val store = MemoryStore()
        var now = 100L
        val outbox = PersistentAccountMutationOutbox(store, clock = { now++ }, idFactory = { error("explicit id") })
        outbox.enqueue(7, AccountMutationPayload.TitleRating(MediaType.MOVIE, 10, 8.5), RATING_ID)
        val received = mutableListOf<String>()
        var fail = true

        val first = outbox.flush(7) { mutation ->
            received += mutation.id
            if (fail) error("offline")
            MutationResult(mutation.id, success = true)
        }
        assertEquals("offline", first.failure)
        assertEquals(1, outbox.pending(7).single().attemptCount)
        assertEquals(101L, outbox.pending(7).single().lastAttemptAt)

        fail = false
        val second = outbox.flush(7) { mutation ->
            received += mutation.id
            MutationResult(mutation.id, success = true)
        }
        assertEquals(listOf(RATING_ID, RATING_ID), received)
        assertEquals(setOf(RATING_ID), second.delivered.keys)
        assertTrue(outbox.pending(7).isEmpty())

        outbox.enqueue(7, AccountMutationPayload.DeleteList(12), DELETE_ID)
        val mismatch = outbox.flush(7) { MutationResult("wrong-${it.id}", success = true) }
        assertTrue(mismatch.delivered.isEmpty())
        assertEquals(1, outbox.pending(7).single().attemptCount)
    }

    @Test
    fun pendingListMutationsOverlayRemoteListsInFifoOrder() {
        val create = PendingAccountMutation(
            CREATE_ID,
            7,
            AccountMutationPayload.CreateList("Offline", "Local", false, "VN", "vi"),
            1,
        )
        val update = PendingAccountMutation(
            "01234567-0000-4000-8000-000000000004",
            7,
            AccountMutationPayload.UpdateList(10, "Updated", "Changed", true),
            2,
        )
        val delete = PendingAccountMutation(DELETE_ID, 7, AccountMutationPayload.DeleteList(11), 3)

        val result = applyPendingLists(
            listOf(UserList(10, "Original"), UserList(11, "Delete")),
            listOf(delete, update, create),
        )

        assertEquals(listOf(10, create.localListId), result.map(UserList::id))
        assertEquals("Updated", result.first().name)
        assertTrue(result.first().public)
    }

    private companion object {
        const val CREATE_ID = "01234567-0000-4000-8000-000000000001"
        const val DELETE_ID = "01234567-0000-4000-8000-000000000002"
        const val RATING_ID = "01234567-0000-4000-8000-000000000003"
    }
}
