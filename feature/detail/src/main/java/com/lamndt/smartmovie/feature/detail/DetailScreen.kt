package com.lamndt.smartmovie.feature.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lamndt.smartmovie.data.ImageUrlFactory
import com.lamndt.smartmovie.designsystem.CinemaColors
import com.lamndt.smartmovie.designsystem.LoadingMessage
import com.lamndt.smartmovie.designsystem.PosterCard
import com.lamndt.smartmovie.designsystem.R
import com.lamndt.smartmovie.designsystem.RatingBadge
import com.lamndt.smartmovie.designsystem.RemoteArtwork
import com.lamndt.smartmovie.designsystem.SectionTitle
import com.lamndt.smartmovie.designsystem.StateMessage
import com.lamndt.smartmovie.designsystem.isWindowWidthAtLeast
import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.CatalogEntity
import com.lamndt.smartmovie.model.ImageKind
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibraryMembership
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.model.Loadable
import com.lamndt.smartmovie.model.TitleDetail
import com.lamndt.smartmovie.model.TitleDetailV2
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.model.preferredTrailer

data class DetailRemoteState(
    val title: TitleSummary,
    val membership: LibraryMembership,
    val trailerKey: String?,
)

@Composable
fun DetailRoute(
    title: TitleSummary,
    catalog: CatalogRepository,
    library: LibraryRepository,
    images: ImageUrlFactory,
    language: String,
    onBack: () -> Unit,
    onTitleClick: (TitleSummary) -> Unit,
    modifier: Modifier = Modifier,
    onRemoteStateChange: (DetailRemoteState) -> Unit = {},
    onRemoteClosed: (String) -> Unit = {},
    region: String? = null,
    includeAdult: Boolean = false,
    onEntityClick: (CatalogEntity) -> Unit = {},
    detailViewModel: DetailViewModel = viewModel(
        key = title.libraryKey,
        factory = DetailViewModel.factory(title, catalog, library, language, region, includeAdult),
    ),
) {
    val state by detailViewModel.state.collectAsStateWithLifecycle()
    val trailerKey = (state.detail as? Loadable.Loaded)
        ?.value
        ?.let { preferredTrailer(it.videos, language.substringBefore('-')) }
        ?.key
    LaunchedEffect(state.title, state.membership, trailerKey) {
        if (!state.title.adult) onRemoteStateChange(DetailRemoteState(state.title, state.membership, trailerKey))
    }
    DisposableEffect(title.libraryKey) {
        onDispose { onRemoteClosed(title.libraryKey) }
    }
    DetailScreen(state, images, language, onBack, onTitleClick, onEntityClick, detailViewModel::refresh, detailViewModel::toggle, modifier)
}

@Composable
fun DetailScreen(
    state: DetailUiState,
    images: ImageUrlFactory,
    language: String,
    onBack: () -> Unit,
    onTitleClick: (TitleSummary) -> Unit,
    onEntityClick: (CatalogEntity) -> Unit = {},
    onRetry: () -> Unit,
    onToggle: (LibraryCollection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        item { DetailHero(state, images, language, onBack, onToggle) }
        when (val detail = state.detail) {
            Loadable.Idle, Loadable.Loading -> item { LoadingMessage(Modifier.height(260.dp)) }
            is Loadable.Failed -> item { StateMessage(stringResource(R.string.details_unavailable), message = detail.message, retry = onRetry) }
            is Loadable.Loaded -> item { DetailBody(detail.value, state.deepDetail, images, onTitleClick, onEntityClick) }
        }
    }
}

@Composable
private fun DetailHero(
    state: DetailUiState,
    images: ImageUrlFactory,
    language: String,
    onBack: () -> Unit,
    onToggle: (LibraryCollection) -> Unit,
) {
    val detail = (state.detail as? Loadable.Loaded)?.value
    val trailer = detail?.let { preferredTrailer(it.videos, language.substringBefore('-')) }
    val context = LocalContext.current
    val wide = isWindowWidthAtLeast(700)
    Box(Modifier.fillMaxWidth().height(if (wide) 590.dp else 620.dp)) {
        RemoteArtwork(images.url(state.title.backdropPath, ImageKind.BACKDROP), state.title.displayTitle, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .12f), Color.Black.copy(alpha = .46f), CinemaColors.Background))))
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(18.dp).size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = .58f)).align(Alignment.TopStart),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = if (wide) 48.dp else 20.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Bottom,
        ) {
            if (wide) RemoteArtwork(
                images.url(state.title.posterPath, ImageKind.POSTER), state.title.displayTitle,
                Modifier.width(190.dp).aspectRatio(.68f).clip(RoundedCornerShape(20.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.title.displayTitle, style = if (wide) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RatingBadge(state.title.voteAverage)
                    state.title.releaseYear?.let { Text(it, color = CinemaColors.Muted) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            trailer?.let {
                                val appIntent = Intent(Intent.ACTION_VIEW, "vnd.youtube:${it.key}".toUri())
                                val webIntent = Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${it.key}".toUri())
                                context.startActivity(if (appIntent.resolveActivity(context.packageManager) != null) appIntent else webIntent)
                            }
                        },
                        enabled = trailer != null,
                        colors = ButtonDefaults.buttonColors(containerColor = CinemaColors.Accent), modifier = Modifier.height(52.dp),
                    ) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.trailer)) }
                    LibraryButton(
                        selected = state.membership.isFavorite,
                        selectedIcon = { Icon(Icons.Default.Favorite, null) },
                        idleIcon = { Icon(Icons.Default.FavoriteBorder, null) },
                        label = stringResource(R.string.favorite),
                    ) { onToggle(LibraryCollection.FAVORITES) }
                    LibraryButton(
                        selected = state.membership.isWatchlisted,
                        selectedIcon = { Icon(Icons.Default.Bookmark, null) },
                        idleIcon = { Icon(Icons.Default.BookmarkBorder, null) },
                        label = stringResource(R.string.watchlist),
                    ) { onToggle(LibraryCollection.WATCHLIST) }
                }
            }
        }
    }
}

@Composable
private fun LibraryButton(
    selected: Boolean,
    selectedIcon: @Composable () -> Unit,
    idleIcon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(52.dp),
        contentPadding = PaddingValues(horizontal = 13.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (selected) CinemaColors.Accent else CinemaColors.Foreground),
    ) { if (selected) selectedIcon() else idleIcon(); if (isWindowWidthAtLeast(500)) { Spacer(Modifier.width(6.dp)); Text(label) } }
}

@Composable
private fun DetailBody(
    detail: TitleDetail,
    deep: TitleDetailV2?,
    images: ImageUrlFactory,
    onTitleClick: (TitleSummary) -> Unit,
    onEntityClick: (CatalogEntity) -> Unit,
) {
    val wide = isWindowWidthAtLeast(700)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = if (wide) 48.dp else 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        if (detail.genres.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            detail.genres.take(if (wide) 6 else 3).forEach { genre ->
                Surface(shape = CircleShape, color = CinemaColors.Surface) { Text(genre.name, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(stringResource(R.string.story))
                Text(detail.overview.ifBlank { stringResource(R.string.no_overview) }, color = CinemaColors.Muted, style = MaterialTheme.typography.bodyLarge)
            }
            if (wide) Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.status))
                detail.status?.let { Text(it, color = CinemaColors.Muted) }
                detail.runtimeMinutes?.let { Text(stringResource(R.string.runtime_minutes, it), color = CinemaColors.Muted) }
                detail.numberOfSeasons?.let { Text(pluralStringResource(R.plurals.seasons, it, it), color = CinemaColors.Muted) }
            }
        }
        if (!wide && (detail.status != null || detail.runtimeMinutes != null || detail.numberOfSeasons != null)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.status))
                Text(listOfNotNull(detail.status, detail.runtimeMinutes?.let { stringResource(R.string.runtime_minutes, it) }, detail.numberOfSeasons?.let { pluralStringResource(R.plurals.seasons, it, it) }).joinToString(" • "), color = CinemaColors.Muted)
            }
        }
        if (detail.cast.isNotEmpty()) {
            SectionTitle(stringResource(R.string.cast))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(detail.cast, key = { it.id }) { member ->
                    Column(
                        Modifier.width(116.dp).clickable {
                            onEntityClick(CatalogEntity.Person(com.lamndt.smartmovie.model.PersonSummary(member.id, member.name, member.profilePath)))
                        },
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RemoteArtwork(images.url(member.profilePath, ImageKind.PROFILE), member.name, Modifier.size(116.dp).clip(RoundedCornerShape(18.dp)))
                        Text(member.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                        member.character?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, color = CinemaColors.Muted, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
        deep?.let { value ->
            if (value.tagline.isNotBlank()) Text("“${value.tagline}”", style = MaterialTheme.typography.headlineMedium, color = CinemaColors.Muted)
            value.collection?.let { collection ->
                SectionTitle(stringResource(R.string.collection))
                Text(
                    collection.name,
                    Modifier.clickable { onEntityClick(CatalogEntity.Collection(collection)) }
                        .fillMaxWidth().background(CinemaColors.Surface, RoundedCornerShape(16.dp)).padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (value.seasons.isNotEmpty()) {
                SectionTitle(stringResource(R.string.seasons_episodes))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(value.seasons, key = { it.id }) { season ->
                        Column(
                            Modifier.width(150.dp).clickable {
                                onEntityClick(CatalogEntity.Season(season.copy(seriesId = value.id)))
                            },
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RemoteArtwork(images.url(season.posterPath, ImageKind.POSTER), season.name, Modifier.fillMaxWidth().aspectRatio(.68f))
                            Text(season.name, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            value.watchProviders.firstOrNull()?.let { providers ->
                if (providers.stream.isNotEmpty() || providers.rent.isNotEmpty() || providers.buy.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.where_to_watch))
                    Text(
                        (providers.stream + providers.rent + providers.buy).distinctBy { it.providerId }.joinToString(" · ") { it.providerName },
                        color = CinemaColors.Muted,
                    )
                    Text(stringResource(R.string.justwatch_attribution), style = MaterialTheme.typography.labelMedium, color = CinemaColors.Muted)
                }
            }
        }
        if (detail.similar.isNotEmpty()) {
            SectionTitle(stringResource(R.string.more_like_this))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(detail.similar, key = { it.libraryKey }) { title ->
                    PosterCard(title, images.url(title.posterPath, ImageKind.POSTER), { onTitleClick(title) })
                }
            }
        }
    }
}
