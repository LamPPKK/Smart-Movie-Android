package com.lamndt.smartmovie.feature.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lamndt.smartmovie.data.ImageUrlFactory
import com.lamndt.smartmovie.designsystem.CinemaCardShape
import com.lamndt.smartmovie.designsystem.CinemaColors
import com.lamndt.smartmovie.designsystem.RemoteArtwork
import com.lamndt.smartmovie.designsystem.SectionTitle
import com.lamndt.smartmovie.model.Credit
import com.lamndt.smartmovie.model.ImageKind

@Composable
fun CreditShelf(
    label: String,
    credits: List<Credit>,
    images: ImageUrlFactory,
    onCreditClick: (Credit) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (credits.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(label)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(credits.take(40), key = { index, credit -> credit.creditId ?: "$index:${credit.id}" }) { _, credit ->
                val profile = credit.mediaType == null
                val artwork = if (profile) images.url(credit.profilePath, ImageKind.PROFILE)
                else images.url(credit.posterPath, ImageKind.POSTER)
                Column(
                    Modifier.width(116.dp).clickable(enabled = credit.creditId != null) { onCreditClick(credit) },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RemoteArtwork(
                        artwork,
                        credit.title.orEmpty(),
                        Modifier.fillMaxWidth().aspectRatio(if (profile) .78f else .68f).clip(CinemaCardShape),
                    )
                    Text(
                        credit.title.orEmpty(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    (credit.character ?: credit.job ?: credit.department)?.let { role ->
                        Text(
                            role,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = CinemaColors.Muted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}
