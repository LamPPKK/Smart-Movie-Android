package com.lamndt.smartmovie.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import coil3.compose.AsyncImage
import com.lamndt.smartmovie.remote.WatchCommand
import com.lamndt.smartmovie.remote.WatchTitleContext

private val Background = Color(0xFF050508)
private val Elevated = Color(0xFF0E0E12)
private val Accent = Color(0xFFE01C47)
private val Gold = Color(0xFFF5B533)
private val Foreground = Color(0xFFF5F7FC)
private val Muted = Color(0xFF9499A8)

class WearMainActivity : ComponentActivity() {
    private val viewModel: WearRemoteViewModel by viewModels { WearRemoteViewModel.factory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearCinemaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                WearRemoteScreen(state, viewModel::send)
            }
        }
    }
}

@Composable
internal fun WearCinemaTheme(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme.copy(
        primary = Accent,
        onPrimary = Foreground,
        secondary = Gold,
        background = Background,
        onBackground = Foreground,
        surfaceContainerLow = Elevated,
        surfaceContainer = Elevated,
        surfaceContainerHigh = Elevated,
        onSurface = Foreground,
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
internal fun WearRemoteScreen(
    state: WearRemoteUiState,
    onCommand: (WatchCommand) -> Unit,
    timeText: @Composable () -> Unit = { TimeText() },
) {
    val listState = rememberTransformingLazyColumnState()
    AppScaffold(timeText = timeText) {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize().background(Background),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ListHeader {
                        Text(
                            stringResource(R.string.remote).uppercase(),
                            color = Accent,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp,
                        )
                    }
                }
                val title = state.context
                if (title == null) {
                    item { EmptyRemoteState(state.phoneConnected) }
                } else {
                    item { TitleArtwork(title) }
                    item { TitleMetadata(title) }
                    if (!state.phoneConnected || !state.phoneActive) {
                        item {
                            StatusMessage(
                                stringResource(
                                    if (!state.phoneConnected) R.string.phone_disconnected else R.string.phone_inactive,
                                ),
                            )
                        }
                    }
                    if (state.commandFailed) {
                        item { StatusMessage(stringResource(R.string.command_failed), Accent) }
                    }
                    if (state.isSending) {
                        item { StatusMessage(stringResource(R.string.sending), Gold) }
                    }
                    item {
                        RemoteButton(
                            text = stringResource(R.string.open_details),
                            enabled = state.controlsEnabled,
                            icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                            onClick = { onCommand(WatchCommand.OPEN_DETAILS) },
                        )
                    }
                    item {
                        RemoteButton(
                            text = stringResource(R.string.play_trailer),
                            enabled = state.controlsEnabled && title.trailerAvailable,
                            icon = { Icon(Icons.Default.PlayArrow, null) },
                            onClick = { onCommand(WatchCommand.PLAY_TRAILER) },
                        )
                    }
                    item {
                        RemoteButton(
                            text = stringResource(if (title.favorite) R.string.remove_favorite else R.string.favorite),
                            enabled = state.controlsEnabled,
                            icon = {
                                Icon(if (title.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                            },
                            onClick = { onCommand(WatchCommand.TOGGLE_FAVORITE) },
                        )
                    }
                    item {
                        RemoteButton(
                            text = stringResource(if (title.watchlist) R.string.remove_watchlist else R.string.watchlist),
                            enabled = state.controlsEnabled,
                            icon = {
                                Icon(if (title.watchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null)
                            },
                            onClick = { onCommand(WatchCommand.TOGGLE_WATCHLIST) },
                        )
                    }
                    item { Spacer(Modifier.height(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyRemoteState(phoneConnected: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.open_phone_title),
            color = Foreground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(
                if (phoneConnected) R.string.open_phone_description else R.string.phone_disconnected,
            ),
            color = Muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TitleArtwork(title: WatchTitleContext) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Elevated),
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Accent.copy(alpha = 0.42f),
            modifier = Modifier.size(44.dp).align(Alignment.Center),
        )
        AsyncImage(
            model = title.artworkUrl,
            contentDescription = stringResource(R.string.artwork_description, title.title),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
    }
}

@Composable
private fun TitleMetadata(title: WatchTitleContext) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            title.title,
            color = Foreground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("★ %.1f".format(title.rating), color = Gold, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(
                    stringResource(if (title.mediaType == "tv") R.string.tv_series else R.string.movie),
                    title.year,
                ).joinToString(" • "),
                color = Muted,
            )
        }
    }
}

@Composable
private fun RemoteButton(
    text: String,
    enabled: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatusMessage(message: String, color: Color = Muted) {
    Text(
        message,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
    )
}
