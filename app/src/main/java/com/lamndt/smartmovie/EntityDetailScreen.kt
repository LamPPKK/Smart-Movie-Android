package com.lamndt.smartmovie

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lamndt.smartmovie.data.ImageUrlFactory
import com.lamndt.smartmovie.designsystem.CinemaColors
import com.lamndt.smartmovie.designsystem.LoadingMessage
import com.lamndt.smartmovie.designsystem.PosterCard
import com.lamndt.smartmovie.designsystem.R
import com.lamndt.smartmovie.designsystem.RemoteArtwork
import com.lamndt.smartmovie.designsystem.SectionTitle
import com.lamndt.smartmovie.designsystem.StateMessage
import com.lamndt.smartmovie.feature.detail.AccountRatingControl
import com.lamndt.smartmovie.feature.detail.CreditShelf
import com.lamndt.smartmovie.model.CatalogEntity
import com.lamndt.smartmovie.model.CatalogV2Repository
import com.lamndt.smartmovie.model.CollectionDetail
import com.lamndt.smartmovie.model.Credit
import com.lamndt.smartmovie.model.EntityKind
import com.lamndt.smartmovie.model.EpisodeDetail
import com.lamndt.smartmovie.model.ImageKind
import com.lamndt.smartmovie.model.KeywordDetail
import com.lamndt.smartmovie.model.OrganizationDetail
import com.lamndt.smartmovie.model.PersonDetail
import com.lamndt.smartmovie.model.SeasonDetail
import com.lamndt.smartmovie.model.TitleSummary

private sealed interface EntityDetailState {
    data object Loading : EntityDetailState
    data class Failed(val message: String) : EntityDetailState
    data class Person(val value: PersonDetail) : EntityDetailState
    data class Collection(val value: CollectionDetail) : EntityDetailState
    data class Organization(val value: OrganizationDetail) : EntityDetailState
    data class Keyword(val value: KeywordDetail) : EntityDetailState
    data class Season(val value: SeasonDetail) : EntityDetailState
    data class Episode(val value: EpisodeDetail) : EntityDetailState
}

@Composable
internal fun EntityDetailScreen(
    key: EntityKey,
    catalog: CatalogV2Repository,
    images: ImageUrlFactory,
    language: String,
    onBack: () -> Unit,
    onTitle: (TitleSummary) -> Unit,
    onEntity: (CatalogEntity) -> Unit,
    onCredit: (Credit) -> Unit,
    appContainer: AppContainer? = null,
    watchRemote: PhoneWatchRemoteController? = null,
) {
    var state by remember(key) { mutableStateOf<EntityDetailState>(EntityDetailState.Loading) }
    val episodeRating = if (key.kind == "episode") {
        rememberEpisodeAccountRating(
            appContainer,
            requireNotNull(key.seriesId),
            requireNotNull(key.seasonNumber),
            requireNotNull(key.episodeNumber),
        )
    } else {
        AccountRatingBinding()
    }
    LaunchedEffect(key) {
        state = runCatching {
            when (key.kind) {
                "person" -> EntityDetailState.Person(catalog.person(key.id, language))
                "collection" -> EntityDetailState.Collection(catalog.collection(key.id, language))
                "company" -> EntityDetailState.Organization(catalog.organization(EntityKind.COMPANY, key.id, language, 1))
                "network" -> EntityDetailState.Organization(catalog.organization(EntityKind.NETWORK, key.id, language, 1))
                "keyword" -> EntityDetailState.Keyword(catalog.keyword(key.id, language, 1))
                "season" -> EntityDetailState.Season(catalog.season(requireNotNull(key.seriesId), requireNotNull(key.seasonNumber), language))
                "episode" -> EntityDetailState.Episode(catalog.episode(requireNotNull(key.seriesId), requireNotNull(key.seasonNumber), requireNotNull(key.episodeNumber), language))
                else -> error("Unsupported catalog entity")
            }
        }.getOrElse { EntityDetailState.Failed(it.message.orEmpty()) }
    }
    val activeEpisode = (state as? EntityDetailState.Episode)?.value
    LaunchedEffect(activeEpisode, key.series, watchRemote) {
        val episode = activeEpisode ?: return@LaunchedEffect
        val series = key.series?.takeUnless { it.adult } ?: return@LaunchedEffect
        watchRemote?.publishEpisode(
            series = series,
            episode = episode,
            artworkUrl = images.url(episode.stillPath, ImageKind.BACKDROP),
        )
    }
    DisposableEffect(key) {
        onDispose {
            if (key.kind == "episode") {
                watchRemote?.clear("episode:${key.seriesId}:${key.seasonNumber}:${key.episodeNumber}")
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(key.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        }
        when (val value = state) {
            EntityDetailState.Loading -> LoadingMessage(Modifier.fillMaxSize())
            is EntityDetailState.Failed -> StateMessage(stringResource(R.string.details_unavailable), Modifier.fillMaxSize(), value.message)
            is EntityDetailState.Person -> PersonContent(value.value, images, onTitle, onCredit)
            is EntityDetailState.Collection -> TitleCatalog(value.value.overview, value.value.parts, images, onTitle)
            is EntityDetailState.Organization -> TitleCatalog(value.value.description, value.value.titles.results, images, onTitle)
            is EntityDetailState.Keyword -> TitleCatalog("", value.value.titles.results, images, onTitle)
            is EntityDetailState.Season -> SeasonContent(value.value, images, onEntity, onCredit)
            is EntityDetailState.Episode -> EpisodeContent(value.value, images, episodeRating, onCredit)
        }
    }
}

@Composable
private fun PersonContent(
    value: PersonDetail,
    images: ImageUrlFactory,
    onTitle: (TitleSummary) -> Unit,
    onCredit: (Credit) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                RemoteArtwork(images.url(value.profilePath, ImageKind.PROFILE), value.name, Modifier.width(180.dp).aspectRatio(.75f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(value.name, style = MaterialTheme.typography.displayMedium)
                    value.knownForDepartment?.let { Text(it, color = CinemaColors.Accent) }
                    value.placeOfBirth?.let { Text(it, color = CinemaColors.Muted) }
                }
            }
        }
        if (value.biography.isNotBlank()) item { SectionTitle(stringResource(R.string.biography)); Text(value.biography, color = CinemaColors.Muted) }
        item { TitleShelf(stringResource(R.string.known_for), value.knownFor, images, onTitle) }
        item { CreditShelf(stringResource(R.string.cast), value.credits.cast, images, onCredit) }
        item { CreditShelf(stringResource(R.string.crew), value.credits.crew, images, onCredit) }
    }
}

@Composable
private fun TitleCatalog(description: String, titles: List<TitleSummary>, images: ImageUrlFactory, onTitle: (TitleSummary) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (description.isNotBlank()) item { Text(description, color = CinemaColors.Muted) }
        item { TitleShelf(stringResource(R.string.related_titles), titles, images, onTitle) }
    }
}

@Composable
private fun TitleShelf(label: String, titles: List<TitleSummary>, images: ImageUrlFactory, onTitle: (TitleSummary) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(label)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(titles, key = { it.libraryKey }) { title ->
                PosterCard(title, images.url(title.posterPath, ImageKind.POSTER), { onTitle(title) })
            }
        }
    }
}

@Composable
private fun SeasonContent(
    value: SeasonDetail,
    images: ImageUrlFactory,
    onEntity: (CatalogEntity) -> Unit,
    onCredit: (Credit) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (value.overview.isNotBlank()) item { Text(value.overview, color = CinemaColors.Muted) }
        item { CreditShelf(stringResource(R.string.cast), value.credits.cast, images, onCredit) }
        item { CreditShelf(stringResource(R.string.crew), value.credits.crew, images, onCredit) }
        items(value.episodes, key = { it.episodeKey }) { episode ->
            Row(
                Modifier.fillMaxWidth().clickable { onEntity(CatalogEntity.Episode(episode)) }.padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RemoteArtwork(images.url(episode.stillPath, ImageKind.BACKDROP), episode.name, Modifier.width(190.dp).aspectRatio(1.77f))
                Column {
                    Text("E${episode.episodeNumber} · ${episode.name}", style = MaterialTheme.typography.titleMedium)
                    Text(episode.overview, color = CinemaColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EpisodeContent(
    value: EpisodeDetail,
    images: ImageUrlFactory,
    rating: AccountRatingBinding,
    onCredit: (Credit) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { RemoteArtwork(images.url(value.stillPath, ImageKind.BACKDROP), value.name, Modifier.fillMaxWidth().aspectRatio(1.77f)) }
        item { Text("S${value.seasonNumber} · E${value.episodeNumber}", color = CinemaColors.Accent, fontWeight = FontWeight.Black) }
        item { Text(value.name, style = MaterialTheme.typography.displayMedium) }
        item { Text(value.overview, color = CinemaColors.Muted) }
        item { CreditShelf(stringResource(R.string.guest_stars), value.guestStars, images, onCredit) }
        item { CreditShelf(stringResource(R.string.crew), value.crew, images, onCredit) }
        if (rating.signedIn) item {
            AccountRatingControl(
                value = rating.value,
                pending = rating.pending,
                error = rating.error,
                onChange = rating.onChange,
            )
        }
    }
}
