package com.lamndt.smartmovie.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lamndt.smartmovie.data.ImageUrlFactory
import com.lamndt.smartmovie.designsystem.CinemaColors
import com.lamndt.smartmovie.designsystem.PosterCard
import com.lamndt.smartmovie.designsystem.R
import com.lamndt.smartmovie.designsystem.RatingBadge
import com.lamndt.smartmovie.designsystem.SectionTitle
import com.lamndt.smartmovie.model.ImageKind
import com.lamndt.smartmovie.model.Review
import com.lamndt.smartmovie.model.TitleSummary

fun presentedReviews(values: List<Review>, limit: Int = 4): List<Review> =
    values.filter { it.content.isNotBlank() }.distinctBy(Review::id).take(limit)

fun presentedEditorialTitles(
    values: List<TitleSummary>,
    currentLibraryKey: String? = null,
    includeAdult: Boolean = false,
    limit: Int = 20,
): List<TitleSummary> = values.filter { (includeAdult || !it.adult) && it.libraryKey != currentLibraryKey }
    .distinctBy(TitleSummary::libraryKey)
    .take(limit)

@Composable
fun CatalogReviewSection(
    values: List<Review>,
    modifier: Modifier = Modifier,
) {
    val reviews = presentedReviews(values)
    if (reviews.isEmpty()) return

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(stringResource(R.string.reviews))
        reviews.forEach { review ->
            Surface(shape = RoundedCornerShape(18.dp), color = CinemaColors.Surface) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                review.author.takeIf(String::isNotBlank) ?: stringResource(R.string.tmdb_member),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            review.createdAt?.let {
                                Text(it.take(10), color = CinemaColors.Muted, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        review.rating?.let { RatingBadge(it) }
                    }
                    Text(
                        review.content,
                        color = CinemaColors.Muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
fun CatalogRecommendationSection(
    values: List<TitleSummary>,
    currentLibraryKey: String,
    includeAdult: Boolean,
    images: ImageUrlFactory,
    onTitleClick: (TitleSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val titles = presentedEditorialTitles(values, currentLibraryKey, includeAdult)
    if (titles.isEmpty()) return

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(stringResource(R.string.recommendations))
        LazyRow(
            contentPadding = PaddingValues(end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(titles, key = TitleSummary::libraryKey) { title ->
                PosterCard(
                    title = title,
                    imageUrl = images.url(title.posterPath, ImageKind.POSTER),
                    onClick = { onTitleClick(title) },
                    modifier = Modifier.width(150.dp),
                )
            }
        }
    }
}
