package com.lamndt.smartmovie.feature.detail

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lamndt.smartmovie.data.ImageUrlFactory
import com.lamndt.smartmovie.designsystem.CinemaColors
import com.lamndt.smartmovie.designsystem.R
import com.lamndt.smartmovie.designsystem.RemoteArtwork
import com.lamndt.smartmovie.designsystem.SectionTitle
import com.lamndt.smartmovie.model.ImageAsset
import com.lamndt.smartmovie.model.ImageKind
import com.lamndt.smartmovie.model.Video

internal fun presentedImages(values: List<ImageAsset>, limit: Int = 20): List<ImageAsset> =
    values.filter { it.filePath.isNotBlank() }.distinctBy(ImageAsset::filePath).take(limit)

internal fun playableVideos(values: List<Video>, limit: Int = 12): List<Video> =
    values.filter { it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank() }
        .distinctBy(Video::key)
        .take(limit)

internal fun presentedExternalIds(values: Map<String, String>, limit: Int = 8): List<Pair<String, String>> =
    values.filterValues(String::isNotBlank).toSortedMap().entries.take(limit).map { it.key to it.value }

@Composable
fun CatalogMediaSection(
    imageAssets: List<ImageAsset>,
    videos: List<Video>,
    images: ImageUrlFactory,
    modifier: Modifier = Modifier,
) {
    val displayedImages = presentedImages(imageAssets)
    val displayedVideos = playableVideos(videos)
    if (displayedImages.isEmpty() && displayedVideos.isEmpty()) return

    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (displayedImages.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.images))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(displayedImages, key = ImageAsset::filePath) { asset ->
                        var focused by remember(asset.filePath) { mutableStateOf(false) }
                        val focusScale by animateFloatAsState(if (focused) 1.045f else 1f, label = "media-image-scale")
                        val focusBorder by animateColorAsState(
                            if (focused) CinemaColors.Accent else Color.Transparent,
                            label = "media-image-border",
                        )
                        val poster = asset.kind.equals("poster", ignoreCase = true)
                        val kind = if (poster) ImageKind.POSTER else ImageKind.BACKDROP
                        val shape = RoundedCornerShape(18.dp)
                        RemoteArtwork(
                            images.url(asset.filePath, kind),
                            stringResource(R.string.catalog_image),
                            Modifier.width(if (poster) 140.dp else 240.dp)
                                .height(if (poster) 210.dp else 135.dp)
                                .graphicsLayer(scaleX = focusScale, scaleY = focusScale)
                                .border(3.dp, focusBorder, shape)
                                .clip(shape)
                                .onFocusChanged { focused = it.isFocused }
                                .focusable(),
                            contentScale = if (asset.kind.equals("logo", ignoreCase = true)) {
                                ContentScale.Fit
                            } else {
                                ContentScale.Crop
                            },
                        )
                    }
                }
            }
        }
        if (displayedVideos.isNotEmpty()) {
            val context = LocalContext.current
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.videos))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(displayedVideos, key = Video::key) { video ->
                        var focused by remember(video.key) { mutableStateOf(false) }
                        val focusScale by animateFloatAsState(if (focused) 1.045f else 1f, label = "media-video-scale")
                        val focusBorder by animateColorAsState(
                            if (focused) CinemaColors.Accent else Color.Transparent,
                            label = "media-video-border",
                        )
                        val shape = RoundedCornerShape(18.dp)
                        Surface(
                            modifier = Modifier.graphicsLayer(scaleX = focusScale, scaleY = focusScale)
                                .border(3.dp, focusBorder, shape),
                            shape = shape,
                            color = CinemaColors.Surface,
                        ) {
                            TextButton(
                                onClick = {
                                    val appIntent = Intent(Intent.ACTION_VIEW, "vnd.youtube:${video.key}".toUri())
                                    val webIntent = Intent(
                                        Intent.ACTION_VIEW,
                                        "https://www.youtube.com/watch?v=${video.key}".toUri(),
                                    )
                                    context.startActivity(
                                        if (appIntent.resolveActivity(context.packageManager) != null) appIntent else webIntent,
                                    )
                                },
                                modifier = Modifier.width(240.dp)
                                    .onFocusChanged { focused = it.isFocused }
                                    .padding(4.dp),
                            ) {
                                Icon(Icons.Default.PlayCircle, null, tint = CinemaColors.Accent)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(video.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        video.type,
                                        color = CinemaColors.Muted,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CatalogMetadataSection(
    values: List<Pair<String, String>>,
    externalIds: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val identifiers = presentedExternalIds(externalIds)
    if (values.isEmpty() && identifiers.isEmpty()) return

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.details))
        values.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(value, color = CinemaColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (identifiers.isNotEmpty()) {
            Text(stringResource(R.string.external_identifiers), style = MaterialTheme.typography.labelLarge)
            identifiers.forEach { (source, value) ->
                Text("$source: $value", color = CinemaColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
