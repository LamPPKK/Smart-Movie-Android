package com.lamndt.smartmovie

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.lamndt.smartmovie.designsystem.CinemaColors
import com.lamndt.smartmovie.designsystem.PosterCard
import com.lamndt.smartmovie.designsystem.R
import com.lamndt.smartmovie.model.AccountMutationPayload
import com.lamndt.smartmovie.model.CapabilitiesV2
import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.CatalogV2Repository
import com.lamndt.smartmovie.model.ConfigurationCountry
import com.lamndt.smartmovie.model.PendingAccountMutation
import com.lamndt.smartmovie.model.ImageKind
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.PagedResult
import com.lamndt.smartmovie.model.SearchScope
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.model.UserList
import com.lamndt.smartmovie.model.UserListItemMutation
import com.lamndt.smartmovie.model.supportsAccountAuthentication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ProfileScreen(
    container: AppContainer,
    language: String,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onTitleClick: (TitleSummary) -> Unit = {},
) {
    val state by container.accountSession.state.collectAsState()
    val mutationRevision by container.accountSession.mutationRevision.collectAsState()
    val capabilities by container.capabilities.collectAsState()
    val region by container.preferences.region.collectAsState()
    val unlocked by container.preferences.adultUnlocked.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pinInvalidMessage = stringResource(R.string.pin_invalid)
    val adultAgeRequiredMessage = stringResource(R.string.adult_age_required)
    val pinLockedMessage = stringResource(R.string.pin_locked)
    val pinIncorrectMessage = stringResource(R.string.pin_incorrect)
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var adultAgeConfirmed by remember { mutableStateOf(false) }
    var pinMessage by remember { mutableStateOf<String?>(null) }
    var showLogout by remember { mutableStateOf(false) }
    var lists by remember { mutableStateOf<List<UserList>>(emptyList()) }
    var listName by remember { mutableStateOf("") }
    var listDescription by remember { mutableStateOf("") }
    var listError by remember { mutableStateOf<String?>(null) }
    var selectedListId by remember { mutableStateOf<Int?>(null) }
    var listDetail by remember { mutableStateOf(AccountListDetailUiState()) }
    var editListName by remember { mutableStateOf("") }
    var editListDescription by remember { mutableStateOf("") }
    var editListPublic by remember { mutableStateOf(false) }
    var listSearchQuery by remember { mutableStateOf("") }
    var listRequestRevision by remember { mutableStateOf(0) }
    var listsRequestRevision by remember { mutableStateOf(0) }
    var recommendationType by remember { mutableStateOf(MediaType.MOVIE) }
    var recommendations by remember { mutableStateOf(AccountRecommendationsUiState()) }
    var recommendationReload by remember { mutableStateOf(0) }
    var recommendationRequestRevision by remember { mutableStateOf(0) }
    var accountUiOwnerId by remember { mutableStateOf<Int?>(null) }
    var providerRegions by remember { mutableStateOf<List<ConfigurationCountry>>(emptyList()) }
    val signedInProfile = (state as? AccountSessionState.SignedIn)?.profile
    val includeAdult = container.preferences.adultConfigured && unlocked
    fun currentAccountId(): Int? =
        (container.accountSession.state.value as? AccountSessionState.SignedIn)?.profile?.id

    val accountAuthenticationAvailable = capabilities.supportsAccountAuthentication(isTv)

    LaunchedEffect(language, region, capabilities?.supportsCatalog("advanced_discover")) {
        providerRegions = runCatching {
            loadProfileProviderRegions(container.catalog, capabilities, language, region)
        }
            .also { result -> if (result.exceptionOrNull() is CancellationException) throw result.exceptionOrNull()!! }
            .getOrDefault(emptyList())
    }

    LaunchedEffect(signedInProfile?.id) {
        accountUiOwnerId = null
        listRequestRevision += 1
        listsRequestRevision += 1
        lists = emptyList()
        listName = ""
        listDescription = ""
        listError = null
        selectedListId = null
        listDetail = AccountListDetailUiState()
        editListName = ""
        editListDescription = ""
        editListPublic = false
        listSearchQuery = ""
        recommendations = AccountRecommendationsUiState()
        accountUiOwnerId = signedInProfile?.id
    }

    LaunchedEffect(signedInProfile?.id, recommendationType, language, includeAdult, recommendationReload) {
        recommendationRequestRevision += 1
        val requestedRevision = recommendationRequestRevision
        if (signedInProfile == null) {
            recommendations = AccountRecommendationsUiState()
        } else {
            val requestedAccountId = signedInProfile.id
            recommendations = recommendations.copy(items = emptyList(), page = 0, loading = true, error = null)
            val result = runCatching {
                container.account.recommendations(recommendationType, 1, language)
            }.also { result ->
                if (result.exceptionOrNull() is CancellationException) throw result.exceptionOrNull()!!
            }
            if (currentAccountId() == requestedAccountId && recommendationRequestRevision == requestedRevision) {
                val currentIncludeAdult = container.preferences.includeAdult
                recommendations = result.fold(
                    onSuccess = { page -> recommendationsFromPage(emptyList(), page, currentIncludeAdult) },
                    onFailure = { error -> recommendations.copy(loading = false, error = error.message.orEmpty()) },
                )
            }
        }
    }

    LaunchedEffect(signedInProfile?.id, selectedListId, language, includeAdult) {
        listRequestRevision += 1
        val requestedRevision = listRequestRevision
        val requestedAccountId = signedInProfile?.id ?: return@LaunchedEffect
        val listId = selectedListId ?: return@LaunchedEffect
        val summary = lists.firstOrNull { it.id == listId } ?: listDetail.list
        if (summary != null) {
            val visibleSummary = summary.copy(results = summary.results.filter { includeAdult || !it.adult })
            listDetail = AccountListDetailUiState(list = visibleSummary, loading = listId > 0)
            editListName = summary.name
            editListDescription = summary.description
            editListPublic = summary.public
        }
        if (listId > 0) {
            listDetail = runCatching { container.account.list(listId, 1, language) }
                .also { result -> if (result.exceptionOrNull() is CancellationException) throw result.exceptionOrNull()!! }
                .fold(
                    onSuccess = { response ->
                        if (currentAccountId() != requestedAccountId ||
                            selectedListId != listId ||
                            listRequestRevision != requestedRevision
                        ) return@LaunchedEffect
                        val pending = container.accountSession.pendingAccountMutations()
                        if (currentAccountId() != requestedAccountId ||
                            selectedListId != listId ||
                            listRequestRevision != requestedRevision
                        ) return@LaunchedEffect
                        val currentIncludeAdult = container.preferences.includeAdult
                        val merged = applyPendingListDetail(
                            mergeAccountListPage(null, response, currentIncludeAdult),
                            pending,
                            currentIncludeAdult,
                        ) ?: return@LaunchedEffect
                        editListName = merged.name
                        editListDescription = merged.description
                        editListPublic = merged.public
                        AccountListDetailUiState(list = merged)
                    },
                    onFailure = { error -> listDetail.copy(loading = false, error = error.message.orEmpty()) },
                )
        }
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.profile), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.tmdb_account), style = MaterialTheme.typography.titleLarge)
                when (val value = state) {
                    AccountSessionState.Checking -> CircularProgressIndicator()
                    AccountSessionState.SignedOut -> {
                        Text(
                            stringResource(
                                if (accountAuthenticationAvailable) {
                                    R.string.account_sign_in_description
                                } else {
                                    R.string.account_unavailable
                                },
                            ),
                            color = CinemaColors.Muted,
                        )
                        Button(onClick = {
                            if (!accountAuthenticationAvailable) return@Button
                            scope.launch {
                                val attempt = container.accountSession.begin("smartmovie://auth/callback", if (isTv) "tv" else "browser")
                                if (!isTv && attempt != null) context.startActivity(Intent(Intent.ACTION_VIEW, attempt.authorizationUrl.toUri()))
                            }
                        }, enabled = accountAuthenticationAvailable) {
                            Icon(Icons.Default.OpenInBrowser, null)
                            Text(stringResource(R.string.continue_tmdb), Modifier.padding(start = 8.dp))
                        }
                    }
                    is AccountSessionState.Authorizing -> {
                        if (isTv) {
                            QrCode(value.attempt.authorizationUrl)
                            value.attempt.deviceCode?.let { Text(it, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black) }
                            Text(stringResource(R.string.scan_tmdb_qr), color = CinemaColors.Muted)
                        } else {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.complete_browser), color = CinemaColors.Muted)
                            OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, value.attempt.authorizationUrl.toUri())) }) {
                                Text(stringResource(R.string.open_browser_again))
                            }
                        }
                    }
                    is AccountSessionState.SignedIn -> {
                        LaunchedEffect(value.profile.id, mutationRevision) {
                            val requestedAccountId = value.profile.id
                            listsRequestRevision += 1
                            val requestedRevision = listsRequestRevision
                            val remote = runCatching { loadAllAccountLists(loadPage = container.account::lists) }
                                .also { result ->
                                    if (result.exceptionOrNull() is CancellationException) {
                                        throw result.exceptionOrNull()!!
                                    }
                                }
                            if (currentAccountId() != requestedAccountId ||
                                listsRequestRevision != requestedRevision
                            ) return@LaunchedEffect
                            val pending = container.accountSession.pendingAccountMutations()
                            if (currentAccountId() != requestedAccountId ||
                                listsRequestRevision != requestedRevision
                            ) return@LaunchedEffect
                            remote.onSuccess {
                                lists = applyPendingLists(it, pending)
                                listError = pending.lastOrNull { mutation -> mutation.lastError != null }?.lastError
                            }.onFailure {
                                lists = applyPendingLists(lists.filter { list -> list.id > 0 }, pending)
                                listError = it.message
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.AccountCircle, null, Modifier.size(56.dp), tint = CinemaColors.Accent)
                            Column {
                                Text(value.profile.name.ifBlank { value.profile.username }, style = MaterialTheme.typography.titleLarge)
                                Text("@${value.profile.username}", color = CinemaColors.Muted)
                            }
                        }
                        OutlinedButton(onClick = { showLogout = true }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, null); Text(stringResource(R.string.sign_out), Modifier.padding(start = 8.dp))
                        }
                    }
                    is AccountSessionState.Failed -> {
                        Text(value.message, color = MaterialTheme.colorScheme.error)
                        TextButton(
                            onClick = { container.accountSession.refresh(language) },
                            enabled = accountAuthenticationAvailable,
                        ) { Text(stringResource(R.string.try_again)) }
                    }
                }
            }
        }

        if (state is AccountSessionState.SignedIn && accountUiOwnerId == signedInProfile?.id) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.account_recommendations), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediaType.entries.forEach { type ->
                        FilterChip(
                            selected = recommendationType == type,
                            onClick = { recommendationType = type },
                            label = { Text(stringResource(if (type == MediaType.MOVIE) R.string.movies else R.string.tv_series)) },
                        )
                    }
                }
                when {
                    recommendations.loading && recommendations.items.isEmpty() -> CircularProgressIndicator()
                    recommendations.error != null && recommendations.items.isEmpty() -> {
                        Text(recommendations.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { recommendationReload += 1 }) { Text(stringResource(R.string.try_again)) }
                    }
                    recommendations.items.isEmpty() -> Text(stringResource(R.string.no_account_recommendations), color = CinemaColors.Muted)
                    else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(recommendations.items, key = TitleSummary::libraryKey) { title ->
                            PosterCard(
                                title = title,
                                imageUrl = container.images.url(title.posterPath, ImageKind.POSTER),
                                onClick = { onTitleClick(title) },
                            )
                        }
                    }
                }
                if (recommendations.page in 1 until recommendations.totalPages) {
                    Button(
                        enabled = !recommendations.loading,
                        onClick = {
                            scope.launch {
                                val requestedPage = recommendations.page + 1
                                val requestedType = recommendationType
                                val requestedAccountId = signedInProfile?.id ?: return@launch
                                val requestedRevision = recommendationRequestRevision
                                recommendations = recommendations.copy(loading = true, error = null)
                                val result = runCatching {
                                    container.account.recommendations(requestedType, requestedPage, language)
                                }.also { value ->
                                    if (value.exceptionOrNull() is CancellationException) throw value.exceptionOrNull()!!
                                }
                                if (currentAccountId() == requestedAccountId &&
                                    recommendationType == requestedType &&
                                    recommendationRequestRevision == requestedRevision
                                ) {
                                    val currentIncludeAdult = container.preferences.includeAdult
                                    recommendations = result.fold(
                                        onSuccess = { page ->
                                            recommendationsFromPage(recommendations.items, page, currentIncludeAdult)
                                        },
                                        onFailure = { error -> recommendations.copy(loading = false, error = error.message.orEmpty()) },
                                    )
                                }
                            }
                        },
                    ) {
                        if (recommendations.loading) CircularProgressIndicator(Modifier.size(18.dp))
                        else Text(stringResource(R.string.load_more))
                    }
                }
                if (recommendations.items.isNotEmpty()) recommendations.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (state is AccountSessionState.SignedIn && accountUiOwnerId == signedInProfile?.id) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.custom_lists), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = listName,
                    onValueChange = { listName = it.take(100) },
                    label = { Text(stringResource(R.string.list_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = listDescription,
                    onValueChange = { listDescription = it.take(1000) },
                    label = { Text(stringResource(R.string.list_description)) },
                )
                Button(enabled = listName.isNotBlank(), onClick = {
                    val name = listName.trim()
                    val description = listDescription.trim()
                    val requestedAccountId = signedInProfile?.id ?: return@Button
                    scope.launch {
                        if (currentAccountId() != requestedAccountId) return@launch
                        runCatching {
                            container.accountSession.queueAccountMutation(
                                AccountMutationPayload.CreateList(
                                    name = name,
                                    description = description,
                                    isPublic = false,
                                    region = region ?: "US",
                                    language = language.substringBefore('-'),
                                ),
                            )
                        }.onSuccess { mutation ->
                            if (currentAccountId() != requestedAccountId) return@onSuccess
                            lists = applyPendingLists(lists, listOf(mutation))
                            listName = ""
                            listDescription = ""
                            listError = null
                        }.onFailure {
                            if (currentAccountId() == requestedAccountId) listError = it.message
                        }
                    }
                }) { Text(stringResource(R.string.create_list)) }
                listError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                lists.forEach { list ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(list.name, style = MaterialTheme.typography.titleMedium)
                            Text(list.description, color = CinemaColors.Muted)
                        }
                        Row {
                            TextButton(onClick = {
                                selectedListId = list.id
                                listDetail = AccountListDetailUiState(list = list, loading = list.id > 0)
                                editListName = list.name
                                editListDescription = list.description
                                editListPublic = list.public
                            }) { Text(stringResource(R.string.open_list)) }
                            TextButton(onClick = {
                                val requestedAccountId = signedInProfile?.id ?: return@TextButton
                                scope.launch {
                                    if (currentAccountId() != requestedAccountId) return@launch
                                    runCatching {
                                        if (list.id < 0) container.accountSession.cancelLocalList(list.id)
                                        else container.accountSession.queueAccountMutation(AccountMutationPayload.DeleteList(list.id))
                                    }.onSuccess {
                                        if (currentAccountId() != requestedAccountId) return@onSuccess
                                        lists = lists.filterNot { it.id == list.id }
                                        if (selectedListId == list.id) {
                                            selectedListId = null
                                            listDetail = AccountListDetailUiState()
                                        }
                                    }.onFailure {
                                        if (currentAccountId() == requestedAccountId) listError = it.message
                                    }
                                }
                            }) { Text(stringResource(R.string.delete_list)) }
                        }
                    }
                }
                selectedListId?.let { listId ->
                    Text(stringResource(R.string.list_details), style = MaterialTheme.typography.titleLarge)
                    if (listId < 0) Text(stringResource(R.string.waiting_list_sync), color = CinemaColors.Muted)
                    OutlinedTextField(
                        value = editListName,
                        onValueChange = { editListName = it.take(100) },
                        label = { Text(stringResource(R.string.list_name)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = editListDescription,
                        onValueChange = { editListDescription = it.take(1000) },
                        label = { Text(stringResource(R.string.list_description)) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(checked = editListPublic, onCheckedChange = { editListPublic = it }, enabled = listId > 0)
                        Text(stringResource(R.string.public_list), modifier = Modifier.padding(top = 12.dp))
                    }
                    Button(
                        enabled = listId > 0 && editListName.isNotBlank(),
                        onClick = {
                            val normalizedName = editListName.trim()
                            val normalizedDescription = editListDescription.trim()
                            val normalizedPublic = editListPublic
                            val requestedAccountId = signedInProfile?.id ?: return@Button
                            scope.launch {
                                if (currentAccountId() != requestedAccountId) return@launch
                                runCatching {
                                    container.accountSession.queueAccountMutation(
                                        AccountMutationPayload.UpdateList(
                                            listId,
                                            normalizedName,
                                            normalizedDescription,
                                            normalizedPublic,
                                        ),
                                    )
                                }.onSuccess {
                                    if (currentAccountId() != requestedAccountId) return@onSuccess
                                    if (selectedListId == listId) {
                                        listDetail = listDetail.copy(
                                            list = listDetail.list?.copy(
                                                name = normalizedName,
                                                description = normalizedDescription,
                                                public = normalizedPublic,
                                            ),
                                            error = null,
                                        )
                                    }
                                    lists = lists.map { current ->
                                        if (current.id == listId) current.copy(
                                            name = normalizedName,
                                            description = normalizedDescription,
                                            public = normalizedPublic,
                                        ) else current
                                    }
                                }.onFailure {
                                    if (currentAccountId() == requestedAccountId && selectedListId == listId) {
                                        listDetail = listDetail.copy(error = it.message)
                                    }
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.save_changes)) }

                    Text(stringResource(R.string.titles_in_list), style = MaterialTheme.typography.titleMedium)
                    when {
                        listDetail.loading && listDetail.list?.results.isNullOrEmpty() -> CircularProgressIndicator()
                        listDetail.list?.results.isNullOrEmpty() -> Text(stringResource(R.string.empty_list_titles), color = CinemaColors.Muted)
                        else -> listDetail.list?.results.orEmpty().forEach { title ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { onTitleClick(title) }, modifier = Modifier.weight(1f)) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(title.title)
                                        Text(
                                            stringResource(if (title.mediaType == MediaType.MOVIE) R.string.movies else R.string.tv_series),
                                            color = CinemaColors.Muted,
                                        )
                                    }
                                }
                                TextButton(enabled = listId > 0, onClick = {
                                    val requestedAccountId = signedInProfile?.id ?: return@TextButton
                                    scope.launch {
                                        if (currentAccountId() != requestedAccountId) return@launch
                                        runCatching {
                                            container.accountSession.queueAccountMutation(
                                                AccountMutationPayload.MutateListItems(
                                                    listId,
                                                    listOf(UserListItemMutation(title.mediaType, title.id)),
                                                    titles = listOf(title),
                                                    remove = true,
                                                ),
                                            )
                                        }.onSuccess {
                                            if (currentAccountId() != requestedAccountId) return@onSuccess
                                            if (selectedListId == listId) {
                                                listDetail = listDetail.copy(
                                                    list = listDetail.list?.copy(
                                                        results = listDetail.list?.results.orEmpty()
                                                            .filterNot { it.libraryKey == title.libraryKey },
                                                    ),
                                                    error = null,
                                                )
                                            }
                                        }.onFailure {
                                            if (currentAccountId() == requestedAccountId && selectedListId == listId) {
                                                listDetail = listDetail.copy(error = it.message)
                                            }
                                        }
                                    }
                                }) { Text(stringResource(R.string.remove_title)) }
                            }
                        }
                    }
                    val currentList = listDetail.list
                    if (currentList != null && (currentList.page ?: 0) in 1 until (currentList.totalPages ?: 1)) {
                        Button(enabled = !listDetail.loadingMore, onClick = {
                            val requestedListId = listId
                            val requestedPage = (listDetail.list?.page ?: 1) + 1
                            val requestedRevision = listRequestRevision
                            scope.launch {
                                listDetail = listDetail.copy(loadingMore = true, error = null)
                                runCatching { container.account.list(requestedListId, requestedPage, language) }
                                    .also { result -> if (result.exceptionOrNull() is CancellationException) throw result.exceptionOrNull()!! }
                                    .onSuccess { response ->
                                        if (selectedListId == requestedListId && listRequestRevision == requestedRevision) {
                                            val pending = container.accountSession.pendingAccountMutations()
                                            val currentIncludeAdult = container.preferences.includeAdult
                                            val merged = mergeAccountListPage(
                                                listDetail.list,
                                                response,
                                                currentIncludeAdult,
                                            )
                                            listDetail = listDetail.copy(
                                                list = applyPendingListDetail(merged, pending, currentIncludeAdult),
                                                loadingMore = false,
                                            )
                                        }
                                    }
                                    .onFailure {
                                        if (selectedListId == requestedListId && listRequestRevision == requestedRevision) {
                                            listDetail = listDetail.copy(loadingMore = false, error = it.message)
                                        }
                                    }
                            }
                        }) {
                            if (listDetail.loadingMore) CircularProgressIndicator(Modifier.size(18.dp))
                            else Text(stringResource(R.string.load_more))
                        }
                    }

                    Text(stringResource(R.string.add_titles), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = listSearchQuery,
                        onValueChange = {
                            listSearchQuery = it
                            listDetail = listDetail.copy(searching = false, searchResults = emptyList())
                        },
                        label = { Text(stringResource(R.string.search_titles)) },
                        singleLine = true,
                    )
                    Button(
                        enabled = listId > 0 && listSearchQuery.isNotBlank() && !listDetail.searching,
                        onClick = {
                            val query = listSearchQuery.trim()
                            val requestedListId = listId
                            val requestedRevision = listRequestRevision
                            scope.launch {
                                listDetail = listDetail.copy(searching = true, error = null)
                                runCatching { container.catalog.search(query, SearchScope.ALL, 1, language) }
                                    .also { result -> if (result.exceptionOrNull() is CancellationException) throw result.exceptionOrNull()!! }
                                    .onSuccess { page ->
                                        if (selectedListId == requestedListId &&
                                            listRequestRevision == requestedRevision &&
                                            listSearchQuery.trim() == query
                                        ) {
                                            val currentIncludeAdult = container.preferences.includeAdult
                                            listDetail = listDetail.copy(
                                                searchResults = filterAccountListSearchResults(
                                                    page.results,
                                                    listDetail.list?.results.orEmpty(),
                                                    currentIncludeAdult,
                                                ),
                                                searching = false,
                                            )
                                        }
                                    }
                                    .onFailure {
                                        if (selectedListId == requestedListId &&
                                            listRequestRevision == requestedRevision &&
                                            listSearchQuery.trim() == query
                                        ) {
                                            listDetail = listDetail.copy(searching = false, error = it.message)
                                        }
                                    }
                            }
                        },
                    ) {
                        if (listDetail.searching) CircularProgressIndicator(Modifier.size(18.dp))
                        else Text(stringResource(R.string.search))
                    }
                    listDetail.searchResults.forEach { title ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(title.title)
                                Text(
                                    stringResource(if (title.mediaType == MediaType.MOVIE) R.string.movies else R.string.tv_series),
                                    color = CinemaColors.Muted,
                                )
                            }
                            TextButton(onClick = {
                                val requestedAccountId = signedInProfile?.id ?: return@TextButton
                                scope.launch {
                                    if (currentAccountId() != requestedAccountId) return@launch
                                    runCatching {
                                        container.accountSession.queueAccountMutation(
                                            AccountMutationPayload.MutateListItems(
                                                listId,
                                                listOf(UserListItemMutation(title.mediaType, title.id)),
                                                titles = listOf(title),
                                                remove = false,
                                            ),
                                        )
                                    }.onSuccess {
                                        if (currentAccountId() != requestedAccountId) return@onSuccess
                                        if (selectedListId == listId) {
                                            if (container.preferences.includeAdult || !title.adult) {
                                                listDetail = listDetail.copy(
                                                    list = listDetail.list?.copy(
                                                        results = (listDetail.list?.results.orEmpty() + title)
                                                            .distinctBy(TitleSummary::libraryKey),
                                                    ),
                                                    searchResults = listDetail.searchResults
                                                        .filterNot { it.libraryKey == title.libraryKey },
                                                    error = null,
                                                )
                                            } else {
                                                listDetail = listDetail.copy(
                                                    searchResults = listDetail.searchResults
                                                        .filterNot { it.libraryKey == title.libraryKey },
                                                    error = null,
                                                )
                                            }
                                        }
                                    }.onFailure {
                                        if (currentAccountId() == requestedAccountId && selectedListId == listId) {
                                            listDetail = listDetail.copy(error = it.message)
                                        }
                                    }
                                }
                            }) { Text(stringResource(R.string.add_title)) }
                        }
                    }
                    listDetail.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = {
                        selectedListId = null
                        listDetail = AccountListDetailUiState()
                    }) { Text(stringResource(R.string.close_list)) }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.provider_region), style = MaterialTheme.typography.titleLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = region == null,
                            onClick = { container.preferences.setRegion(null) },
                            label = { Text(stringResource(R.string.device_region)) },
                        )
                    }
                    val options = providerRegions.ifEmpty {
                        fallbackProviderRegionCodes.map { ConfigurationCountry(it, it) }
                    }
                    items(options, key = ConfigurationCountry::code) { option ->
                        FilterChip(
                            selected = region == option.code,
                            onClick = { container.preferences.setRegion(option.code) },
                            label = { Text(option.displayName) },
                        )
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, null)
                    Text(stringResource(R.string.adult_content), style = MaterialTheme.typography.titleLarge)
                }
                Text(stringResource(R.string.adult_content_description), color = CinemaColors.Muted)
                when {
                    !container.preferences.adultConfigured -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Switch(
                                checked = adultAgeConfirmed,
                                onCheckedChange = { adultAgeConfirmed = it; pinMessage = null },
                            )
                            Text(stringResource(R.string.adult_age_confirmation), modifier = Modifier.weight(1f))
                        }
                        PinField(pin, { pin = it }, stringResource(R.string.six_digit_pin))
                        PinField(confirmation, { confirmation = it }, stringResource(R.string.confirm_pin))
                        Button(onClick = {
                            val ok = container.preferences.configureAdult(pin, confirmation, adultAgeConfirmed)
                            pinMessage = if (ok) null else if (!adultAgeConfirmed) adultAgeRequiredMessage else pinInvalidMessage
                            if (ok) {
                                pin = ""
                                confirmation = ""
                                adultAgeConfirmed = false
                            }
                        }) { Text(stringResource(R.string.enable_adult)) }
                    }
                    unlocked -> {
                        Text(stringResource(R.string.adult_unlocked), color = CinemaColors.Success)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = container.preferences::lockAdult) { Text(stringResource(R.string.lock)) }
                            TextButton(onClick = {
                                container.preferences.disableAdult()
                                adultAgeConfirmed = false
                            }) { Text(stringResource(R.string.disable)) }
                        }
                    }
                    else -> {
                        PinField(pin, { pin = it }, stringResource(R.string.enter_pin))
                        Button(onClick = {
                            val ok = container.preferences.unlockAdult(pin)
                            pinMessage = if (ok) null else if (container.preferences.isLocked) pinLockedMessage else pinIncorrectMessage
                            if (ok) pin = ""
                        }) { Text(stringResource(R.string.unlock)) }
                    }
                }
                pinMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        Spacer(Modifier.size(32.dp))
    }

    if (showLogout) AlertDialog(
        onDismissRequest = { showLogout = false },
        title = { Text(stringResource(R.string.keep_local_library_question)) },
        confirmButton = {
            TextButton(onClick = { showLogout = false; scope.launch { container.accountSession.logout(false) } }) {
                Text(stringResource(R.string.keep_local_library))
            }
        },
        dismissButton = {
            TextButton(onClick = { showLogout = false; scope.launch { container.accountSession.logout(true) } }) {
                Text(stringResource(R.string.remove_account_data))
            }
        },
    )
}

private val fallbackProviderRegionCodes = listOf(
    "US", "GB", "CA", "AU", "FR", "DE", "JP", "KR", "VN", "TW", "HK", "SG", "IN", "BR", "MX",
)

internal suspend fun loadProfileProviderRegions(
    catalog: CatalogRepository,
    capabilities: CapabilitiesV2?,
    language: String,
    region: String?,
): List<ConfigurationCountry> {
    val v2 = catalog as? CatalogV2Repository ?: return emptyList()
    if (capabilities?.supportsCatalog("advanced_discover") != true) return emptyList()
    return v2.discoverConfiguration(language, region).watchProviderRegions
}

internal data class AccountRecommendationsUiState(
    val items: List<TitleSummary> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = false,
    val error: String? = null,
)

internal fun recommendationsFromPage(
    existing: List<TitleSummary>,
    page: PagedResult<TitleSummary>,
    includeAdult: Boolean,
): AccountRecommendationsUiState = AccountRecommendationsUiState(
    items = (existing + page.results)
        .filter { includeAdult || !it.adult }
        .distinctBy(TitleSummary::libraryKey),
    page = page.page,
    totalPages = page.totalPages,
)

internal fun applyPendingLists(remote: List<UserList>, pending: List<PendingAccountMutation>): List<UserList> {
    var result = remote
    pending.sortedBy(PendingAccountMutation::createdAt).forEach { mutation ->
        when (val payload = mutation.payload) {
            is AccountMutationPayload.CreateList -> {
                val localId = mutation.localListId ?: return@forEach
                result = result.filterNot { it.id == localId } + UserList(
                    id = localId,
                    name = payload.name,
                    description = payload.description,
                    public = payload.isPublic,
                )
            }
            is AccountMutationPayload.UpdateList -> result = result.map { list ->
                if (list.id == payload.listId) list.copy(
                    name = payload.name,
                    description = payload.description,
                    public = payload.isPublic,
                ) else list
            }
            is AccountMutationPayload.DeleteList -> result = result.filterNot { it.id == payload.listId }
            is AccountMutationPayload.MutateListItems -> result = result.map { list ->
                if (list.id != payload.listId) return@map list
                val keys = payload.items.mapTo(hashSetOf()) { "${it.mediaType.wireValue}:${it.mediaId}" }
                val results = if (payload.remove) {
                    list.results.filterNot { it.libraryKey in keys }
                } else {
                    (list.results + payload.titles.filter { it.libraryKey in keys })
                        .distinctBy(TitleSummary::libraryKey)
                }
                list.copy(results = results)
            }
            is AccountMutationPayload.TitleRating,
            is AccountMutationPayload.EpisodeRating,
            -> Unit
        }
    }
    return result
}

internal fun applyPendingListDetail(
    remote: UserList,
    pending: List<PendingAccountMutation>,
    includeAdult: Boolean = true,
): UserList? = applyPendingLists(listOf(remote), pending)
    .firstOrNull { it.id == remote.id }
    ?.let { list -> list.copy(results = list.results.filter { includeAdult || !it.adult }) }

internal suspend fun loadAllAccountLists(
    maxPages: Int = 500,
    loadPage: suspend (Int) -> PagedResult<UserList>,
): List<UserList> {
    var page = 1
    var totalPages = 1
    val lists = mutableListOf<UserList>()
    do {
        val response = loadPage(page)
        lists += response.results
        totalPages = response.totalPages.coerceIn(1, maxPages)
        page += 1
    } while (page <= totalPages)
    return lists.distinctBy(UserList::id)
}

internal data class AccountListDetailUiState(
    val list: UserList? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val searching: Boolean = false,
    val searchResults: List<TitleSummary> = emptyList(),
    val error: String? = null,
)

internal fun mergeAccountListPage(existing: UserList?, page: UserList, includeAdult: Boolean): UserList {
    val pageNumber = page.page ?: 1
    val results = ((if (pageNumber > 1) existing?.results.orEmpty() else emptyList()) + page.results)
        .filter { includeAdult || !it.adult }
        .distinctBy(TitleSummary::libraryKey)
    return page.copy(
        page = pageNumber,
        totalPages = page.totalPages ?: pageNumber,
        results = results,
    )
}

internal fun filterAccountListSearchResults(
    candidates: List<TitleSummary>,
    existing: List<TitleSummary>,
    includeAdult: Boolean,
): List<TitleSummary> {
    val existingKeys = existing.mapTo(hashSetOf(), TitleSummary::libraryKey)
    return candidates.filter { (includeAdult || !it.adult) && it.libraryKey !in existingKeys }
        .distinctBy(TitleSummary::libraryKey)
}

@Composable
private fun PinField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(6)) },
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
    )
}

@Composable
private fun QrCode(value: String) {
    val bitmap = remember(value) {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
        Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until 512) for (x in 0 until 512) {
                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }.asImageBitmap()
    }
    Image(bitmap, contentDescription = stringResource(R.string.tmdb_qr_code), modifier = Modifier.size(280.dp))
}
