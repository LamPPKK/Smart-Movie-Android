package com.lamndt.smartmovie.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.TitleSummary
import java.util.Locale

val CinemaCardShape = RoundedCornerShape(18.dp)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = CinemaColors.Foreground,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Star, contentDescription = null, tint = CinemaColors.Gold, modifier = Modifier.size(16.dp))
        Text(String.format(Locale.US, "%.1f", rating), color = CinemaColors.Gold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun RemoteArtwork(url: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(CinemaColors.Elevated), contentAlignment = Alignment.Center) {
        if (url == null) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = contentDescription,
                tint = CinemaColors.Muted.copy(alpha = 0.16f),
                modifier = Modifier.size(38.dp),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun PosterCard(
    title: TitleSummary,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(150.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            RemoteArtwork(
                url = imageUrl,
                contentDescription = title.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / 1.48f)
                    .clip(CinemaCardShape),
            )
            RatingBadge(title.voteAverage, Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
        Text(
            text = title.displayTitle,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CinemaColors.Foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(if (title.mediaType == MediaType.MOVIE) R.string.movie_badge else R.string.tv_badge),
                style = MaterialTheme.typography.labelMedium,
                color = CinemaColors.Muted,
            )
            title.releaseYear?.let { Text("• $it", style = MaterialTheme.typography.labelMedium, color = CinemaColors.Muted) }
        }
    }
}

@Composable
fun TitleRow(title: TitleSummary, imageUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CinemaCardShape,
        colors = CardDefaults.cardColors(containerColor = CinemaColors.Surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RemoteArtwork(
                imageUrl,
                title.displayTitle,
                Modifier.width(88.dp).aspectRatio(0.69f).clip(RoundedCornerShape(13.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RatingBadge(title.voteAverage)
                    title.releaseYear?.let { Text(it, color = CinemaColors.Muted) }
                }
                Text(title.overview, style = MaterialTheme.typography.bodyMedium, color = CinemaColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun MediaTypeSelector(selection: MediaType, onSelection: (MediaType) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        MediaType.entries.forEachIndexed { index, type ->
            val selected = type == selection
            val container by animateColorAsState(if (selected) Color.White.copy(alpha = 0.34f) else CinemaColors.Surface, label = "media")
            SegmentedButton(
                selected = selected,
                onClick = { onSelection(type) },
                shape = SegmentedButtonDefaults.itemShape(index, MediaType.entries.size),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = container, inactiveContainerColor = CinemaColors.Surface),
            ) { Text(stringResource(if (type == MediaType.MOVIE) R.string.movies else R.string.tv_series)) }
        }
    }
}

@Composable
fun StateMessage(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    retry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = CinemaColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        if (retry != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = retry, colors = ButtonDefaults.buttonColors(containerColor = CinemaColors.Accent)) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.try_again))
            }
        }
    }
}

@Composable
fun LoadingMessage(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CinemaColors.Accent) }
}
