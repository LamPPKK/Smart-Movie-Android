package com.lamndt.smartmovie.multiplatform.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lamndt.smartmovie.multiplatform.model.ImageUrlFactory
import com.lamndt.smartmovie.multiplatform.model.TitleSummary

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
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Star, contentDescription = null, tint = CinemaColors.Gold, modifier = Modifier.size(16.dp))
        Text(formatRating(rating), color = CinemaColors.Gold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun RemoteArtwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier = modifier.background(CinemaColors.Elevated), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.BrokenImage,
            contentDescription = if (url == null) contentDescription else null,
            tint = CinemaColors.Muted,
            modifier = Modifier.size(38.dp).alpha(0.22f),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun PosterCard(
    title: TitleSummary,
    images: ImageUrlFactory,
    typeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val border by animateColorAsState(
        if (focused) CinemaColors.Accent else if (hovered) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.08f),
        label = "posterBorder",
    )
    val overlay by animateFloatAsState(if (hovered || focused) 1f else 0f, label = "posterHover")

    Column(
        modifier = modifier
            .width(160.dp)
            .hoverable(interactions)
            .focusable(interactionSource = interactions)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.48f)
                .clip(CinemaCardShape)
                .border(if (focused) 3.dp else 1.dp, border, CinemaCardShape),
        ) {
            RemoteArtwork(
                url = images.poster(title.posterPath),
                contentDescription = title.displayTitle,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = overlay * 0.07f)))
            RatingBadge(title.voteAverage, Modifier.align(Alignment.BottomEnd).padding(9.dp))
        }
        Text(
            text = title.displayTitle,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CinemaColors.Foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(typeLabel.uppercase(), style = MaterialTheme.typography.labelMedium, color = CinemaColors.Muted)
            title.releaseYear?.let { Text("• $it", style = MaterialTheme.typography.labelMedium, color = CinemaColors.Muted) }
        }
    }
}

@Composable
fun LoadingPane(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = CinemaColors.Accent)
    }
}

@Composable
fun MessagePane(
    title: String,
    message: String? = null,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = CinemaColors.Foreground)
        if (!message.isNullOrBlank()) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaColors.Muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (retryLabel != null && onRetry != null) {
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CinemaColors.Accent),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text(retryLabel, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

fun formatRating(value: Double): String {
    val rounded = (value * 10).toInt()
    return "${rounded / 10}.${rounded % 10}"
}
