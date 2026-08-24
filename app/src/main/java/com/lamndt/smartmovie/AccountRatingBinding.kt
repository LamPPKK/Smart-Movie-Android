package com.lamndt.smartmovie

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.lamndt.smartmovie.model.AccountMutationPayload
import com.lamndt.smartmovie.model.PendingAccountMutation
import com.lamndt.smartmovie.model.TitleSummary
import kotlinx.coroutines.launch

internal data class AccountRatingBinding(
    val signedIn: Boolean = false,
    val value: Double? = null,
    val pending: Boolean = false,
    val error: String? = null,
    val onChange: (Double?) -> Unit = {},
)

@Composable
internal fun rememberTitleAccountRating(
    container: AppContainer?,
    title: TitleSummary,
): AccountRatingBinding {
    if (container == null) return AccountRatingBinding()
    val session by container.accountSession.state.collectAsState()
    val revision by container.accountSession.mutationRevision.collectAsState()
    val scope = rememberCoroutineScope()
    var value by remember(title.libraryKey) { mutableStateOf<Double?>(null) }
    var pending by remember(title.libraryKey) { mutableStateOf(false) }
    var error by remember(title.libraryKey) { mutableStateOf<String?>(null) }
    val profileId = (session as? AccountSessionState.SignedIn)?.profile?.id

    LaunchedEffect(title.libraryKey, profileId, revision) {
        if (profileId == null) {
            value = null
            pending = false
            error = null
            return@LaunchedEffect
        }
        val local = container.accountSession.pendingAccountMutations().pendingTitleRating(title)
        if (local != null) {
            value = (local.payload as AccountMutationPayload.TitleRating).value
            pending = true
            error = local.lastError
        } else {
            runCatching { container.account.accountState(title.mediaType, title.id).ratingValue }
                .onSuccess { remote -> value = remote; pending = false; error = null }
                .onFailure { failure -> pending = false; error = failure.message }
        }
    }

    return AccountRatingBinding(
        signedIn = profileId != null,
        value = value,
        pending = pending,
        error = error,
        onChange = { rating ->
            value = rating
            pending = true
            error = null
            scope.launch {
                runCatching {
                    container.accountSession.queueAccountMutation(
                        AccountMutationPayload.TitleRating(title.mediaType, title.id, rating),
                    )
                }.onFailure { failure ->
                    pending = false
                    error = failure.message
                }
            }
        },
    )
}

@Composable
internal fun rememberEpisodeAccountRating(
    container: AppContainer?,
    seriesId: Int,
    seasonNumber: Int,
    episodeNumber: Int,
): AccountRatingBinding {
    if (container == null) return AccountRatingBinding()
    val session by container.accountSession.state.collectAsState()
    val revision by container.accountSession.mutationRevision.collectAsState()
    val scope = rememberCoroutineScope()
    val key = "$seriesId:$seasonNumber:$episodeNumber"
    var value by remember(key) { mutableStateOf<Double?>(null) }
    var pending by remember(key) { mutableStateOf(false) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    val profileId = (session as? AccountSessionState.SignedIn)?.profile?.id

    LaunchedEffect(key, profileId, revision) {
        if (profileId == null) {
            value = null
            pending = false
            error = null
            return@LaunchedEffect
        }
        val local = container.accountSession.pendingAccountMutations()
            .pendingEpisodeRating(seriesId, seasonNumber, episodeNumber)
        if (local != null) {
            value = (local.payload as AccountMutationPayload.EpisodeRating).value
            pending = true
            error = local.lastError
        } else {
            runCatching {
                container.account.episodeAccountState(seriesId, seasonNumber, episodeNumber).ratingValue
            }.onSuccess { remote ->
                value = remote
                pending = false
                error = null
            }.onFailure { failure ->
                pending = false
                error = failure.message
            }
        }
    }

    return AccountRatingBinding(
        signedIn = profileId != null,
        value = value,
        pending = pending,
        error = error,
        onChange = { rating ->
            value = rating
            pending = true
            error = null
            scope.launch {
                runCatching {
                    container.accountSession.queueAccountMutation(
                        AccountMutationPayload.EpisodeRating(
                            seriesId = seriesId,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            value = rating,
                        ),
                    )
                }.onFailure { failure ->
                    pending = false
                    error = failure.message
                }
            }
        },
    )
}

internal fun List<PendingAccountMutation>.pendingTitleRating(title: TitleSummary): PendingAccountMutation? =
    lastOrNull { mutation ->
        val payload = mutation.payload as? AccountMutationPayload.TitleRating
        payload?.mediaType == title.mediaType && payload.mediaId == title.id
    }

internal fun List<PendingAccountMutation>.pendingEpisodeRating(
    seriesId: Int,
    seasonNumber: Int,
    episodeNumber: Int,
): PendingAccountMutation? = lastOrNull { mutation ->
    val payload = mutation.payload as? AccountMutationPayload.EpisodeRating
    payload?.seriesId == seriesId &&
        payload.seasonNumber == seasonNumber &&
        payload.episodeNumber == episodeNumber
}
