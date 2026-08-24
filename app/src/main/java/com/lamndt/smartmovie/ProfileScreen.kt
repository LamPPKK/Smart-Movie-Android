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
import com.lamndt.smartmovie.designsystem.R
import com.lamndt.smartmovie.model.AccountMutationPayload
import com.lamndt.smartmovie.model.PendingAccountMutation
import com.lamndt.smartmovie.model.UserList
import kotlinx.coroutines.launch

@Composable
internal fun ProfileScreen(container: AppContainer, language: String, isTv: Boolean, modifier: Modifier = Modifier) {
    val state by container.accountSession.state.collectAsState()
    val mutationRevision by container.accountSession.mutationRevision.collectAsState()
    val region by container.preferences.region.collectAsState()
    val unlocked by container.preferences.adultUnlocked.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pinInvalidMessage = stringResource(R.string.pin_invalid)
    val pinLockedMessage = stringResource(R.string.pin_locked)
    val pinIncorrectMessage = stringResource(R.string.pin_incorrect)
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf<String?>(null) }
    var showLogout by remember { mutableStateOf(false) }
    var lists by remember { mutableStateOf<List<UserList>>(emptyList()) }
    var listName by remember { mutableStateOf("") }
    var listDescription by remember { mutableStateOf("") }
    var listError by remember { mutableStateOf<String?>(null) }

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
                        Text(stringResource(R.string.account_sign_in_description), color = CinemaColors.Muted)
                        Button(onClick = {
                            scope.launch {
                                val attempt = container.accountSession.begin("smartmovie://auth/callback", if (isTv) "tv" else "browser")
                                if (!isTv && attempt != null) context.startActivity(Intent(Intent.ACTION_VIEW, attempt.authorizationUrl.toUri()))
                            }
                        }) { Icon(Icons.Default.OpenInBrowser, null); Text(stringResource(R.string.continue_tmdb), Modifier.padding(start = 8.dp)) }
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
                            val remote = runCatching { container.account.lists(1).results }
                            val pending = container.accountSession.pendingAccountMutations()
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
                        TextButton(onClick = { container.accountSession.refresh(language) }) { Text(stringResource(R.string.try_again)) }
                    }
                }
            }
        }

        if (state is AccountSessionState.SignedIn) Card(Modifier.fillMaxWidth()) {
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
                    scope.launch {
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
                            lists = applyPendingLists(lists, listOf(mutation))
                            listName = ""
                            listDescription = ""
                            listError = null
                        }.onFailure { listError = it.message }
                    }
                }) { Text(stringResource(R.string.create_list)) }
                listError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                lists.forEach { list ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(list.name, style = MaterialTheme.typography.titleMedium)
                            Text(list.description, color = CinemaColors.Muted)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                runCatching {
                                    if (list.id < 0) container.accountSession.cancelLocalList(list.id)
                                    else container.accountSession.queueAccountMutation(AccountMutationPayload.DeleteList(list.id))
                                }.onSuccess { lists = lists.filterNot { it.id == list.id } }
                                    .onFailure { listError = it.message }
                            }
                        }) { Text(stringResource(R.string.delete_list)) }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.provider_region), style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("", "US", "GB", "CA", "AU", "FR", "DE", "JP", "KR", "VN", "TW", "SG", "IN").forEach { code ->
                        FilterChip(
                            selected = region.orEmpty() == code,
                            onClick = { container.preferences.setRegion(code.ifEmpty { null }) },
                            label = { Text(code.ifEmpty { stringResource(R.string.device_region) }) },
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
                        PinField(pin, { pin = it }, stringResource(R.string.six_digit_pin))
                        PinField(confirmation, { confirmation = it }, stringResource(R.string.confirm_pin))
                        Button(onClick = {
                            val ok = container.preferences.configureAdult(pin, confirmation)
                            pinMessage = if (ok) null else pinInvalidMessage
                            if (ok) { pin = ""; confirmation = "" }
                        }) { Text(stringResource(R.string.enable_adult)) }
                    }
                    unlocked -> {
                        Text(stringResource(R.string.adult_unlocked), color = CinemaColors.Success)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = container.preferences::lockAdult) { Text(stringResource(R.string.lock)) }
                            TextButton(onClick = container.preferences::disableAdult) { Text(stringResource(R.string.disable)) }
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
            is AccountMutationPayload.MutateListItems,
            is AccountMutationPayload.TitleRating,
            is AccountMutationPayload.EpisodeRating,
            -> Unit
        }
    }
    return result
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
