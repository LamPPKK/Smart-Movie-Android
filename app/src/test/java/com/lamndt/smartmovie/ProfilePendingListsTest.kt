package com.lamndt.smartmovie

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.AccountMutationPayload
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.PagedResult
import com.lamndt.smartmovie.model.PendingAccountMutation
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.model.UserList
import com.lamndt.smartmovie.model.UserListItemMutation
import kotlinx.coroutines.runBlocking
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

    @Test
    fun pendingAddAndRemoveSnapshotsWinAfterRemoteReload() {
        val movie = title(1, MediaType.MOVIE)
        val series = title(3, MediaType.TV)
        val remove = pending(
            "01234567-0000-4000-8000-000000000004",
            AccountMutationPayload.MutateListItems(
                10,
                listOf(UserListItemMutation(MediaType.MOVIE, movie.id)),
                titles = listOf(movie),
                remove = true,
            ),
            1,
        )
        val add = pending(
            "01234567-0000-4000-8000-000000000005",
            AccountMutationPayload.MutateListItems(
                10,
                listOf(UserListItemMutation(MediaType.TV, series.id)),
                titles = listOf(series),
                remove = false,
            ),
            2,
        )

        val result = applyPendingListDetail(UserList(10, "Remote", results = listOf(movie)), listOf(add, remove))

        assertThat(result?.results?.map(TitleSummary::libraryKey)).containsExactly("tv:3")
    }

    @Test
    fun allAccountListPagesAreLoadedAndDeduplicated() = runBlocking {
        val requested = mutableListOf<Int>()

        val result = loadAllAccountLists { page ->
            requested += page
            PagedResult(
                page = page,
                totalPages = 3,
                results = listOf(UserList(page.coerceAtMost(2), "List $page")),
            )
        }

        assertThat(requested).containsExactly(1, 2, 3).inOrder()
        assertThat(result.map(UserList::id)).containsExactly(1, 2).inOrder()
    }

    @Test
    fun lockedDetailDoesNotRestorePendingAdultSnapshot() {
        val adult = title(2, MediaType.MOVIE).copy(adult = true)
        val pending = pending(
            "01234567-0000-4000-8000-000000000006",
            AccountMutationPayload.MutateListItems(
                10,
                listOf(UserListItemMutation(MediaType.MOVIE, adult.id)),
                titles = listOf(adult),
                remove = false,
            ),
            1,
        )

        val result = applyPendingListDetail(UserList(10, "Remote"), listOf(pending), includeAdult = false)

        assertThat(result?.results).isEmpty()
    }

    private fun title(id: Int, type: MediaType) = TitleSummary(
        id = id,
        mediaType = type,
        title = "Title $id",
        originalTitle = "Title $id",
        overview = "",
    )

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
