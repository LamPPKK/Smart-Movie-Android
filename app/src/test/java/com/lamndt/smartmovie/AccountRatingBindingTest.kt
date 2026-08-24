package com.lamndt.smartmovie

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.AccountMutationPayload
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.PendingAccountMutation
import com.lamndt.smartmovie.model.TitleSummary
import org.junit.Test

class AccountRatingBindingTest {
    @Test
    fun latestPendingTitleRatingOverridesEarlierValueIncludingRemoval() {
        val title = TitleSummary(10, MediaType.MOVIE, "Movie", "Movie", "")
        val pending = listOf(
            mutation("first", AccountMutationPayload.TitleRating(MediaType.MOVIE, 10, 8.5), 1),
            mutation("other", AccountMutationPayload.TitleRating(MediaType.TV, 10, 6.0), 2),
            mutation("remove", AccountMutationPayload.TitleRating(MediaType.MOVIE, 10, null), 3),
        )

        val selected = pending.pendingTitleRating(title)

        assertThat(selected?.id).isEqualTo("remove")
        assertThat((selected?.payload as AccountMutationPayload.TitleRating).value).isNull()
    }

    @Test
    fun episodeRatingUsesSeriesSeasonAndEpisodeIdentity() {
        val pending = listOf(
            mutation("different-season", AccountMutationPayload.EpisodeRating(20, 1, 2, 7.0), 1),
            mutation("match", AccountMutationPayload.EpisodeRating(20, 2, 2, 9.0), 2),
            mutation("different-series", AccountMutationPayload.EpisodeRating(21, 2, 2, 4.0), 3),
        )

        assertThat(pending.pendingEpisodeRating(20, 2, 2)?.id).isEqualTo("match")
    }

    private fun mutation(id: String, payload: AccountMutationPayload, createdAt: Long) = PendingAccountMutation(
        id = id,
        accountId = 7,
        payload = payload,
        createdAt = createdAt,
        attemptCount = 0,
        lastAttemptAt = null,
        lastError = null,
    )
}
