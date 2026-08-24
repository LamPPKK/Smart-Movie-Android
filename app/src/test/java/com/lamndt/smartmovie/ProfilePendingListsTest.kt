package com.lamndt.smartmovie

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.AccountMutationPayload
import com.lamndt.smartmovie.model.PendingAccountMutation
import com.lamndt.smartmovie.model.UserList
import org.junit.Test

class ProfilePendingListsTest {
    @Test
    fun createUpdateAndDeleteAreAppliedOptimisticallyInFifoOrder() {
        val create = pending(
            "01234567-0000-4000-8000-000000000001",
            AccountMutationPayload.CreateList("Offline", "First", false, "VN", "vi"),
            1,
        )
        val localId = requireNotNull(create.localListId)
        val update = pending(
            "01234567-0000-4000-8000-000000000002",
            AccountMutationPayload.UpdateList(10, "Updated", "New description", true),
            2,
        )
        val delete = pending(
            "01234567-0000-4000-8000-000000000003",
            AccountMutationPayload.DeleteList(11),
            3,
        )

        val result = applyPendingLists(
            listOf(UserList(10, "Original"), UserList(11, "Remove me")),
            listOf(delete, create, update),
        )

        assertThat(result.map { it.id }).containsExactly(10, localId).inOrder()
        assertThat(result.first().name).isEqualTo("Updated")
        assertThat(result.first().public).isTrue()
        assertThat(result.last().name).isEqualTo("Offline")
    }

    private fun pending(id: String, payload: AccountMutationPayload, createdAt: Long) = PendingAccountMutation(
        id = id,
        accountId = 7,
        payload = payload,
        createdAt = createdAt,
        attemptCount = 0,
        lastAttemptAt = null,
        lastError = null,
    )
}
