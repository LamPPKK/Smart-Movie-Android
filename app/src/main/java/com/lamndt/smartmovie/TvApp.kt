package com.lamndt.smartmovie

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText
import com.lamndt.smartmovie.data.ImageUrlFactory
import com.lamndt.smartmovie.designsystem.CinemaBackground
import com.lamndt.smartmovie.designsystem.CinemaColors
import com.lamndt.smartmovie.designsystem.LoadingMessage
import com.lamndt.smartmovie.designsystem.R
import com.lamndt.smartmovie.designsystem.RatingBadge
import com.lamndt.smartmovie.designsystem.RemoteArtwork
import com.lamndt.smartmovie.designsystem.SectionTitle
import com.lamndt.smartmovie.designsystem.StateMessage
import com.lamndt.smartmovie.feature.detail.DetailUiState
import com.lamndt.smartmovie.feature.detail.DetailViewModel
import com.lamndt.smartmovie.model.CatalogLocale
import com.lamndt.smartmovie.model.HomeFeed
import com.lamndt.smartmovie.model.ImageKind
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.Loadable
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.SearchScope
import com.lamndt.smartmovie.model.TitleDetail
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.model.preferredTrailer

@Composable
fun TvApp(container: AppContainer) {
    val locale = LocalConfiguration.current.locales[0]
    val language = CatalogLocale.from(locale.language, locale.country)
    val tvViewModel: TvCatalogViewModel = viewModel(factory = TvCatalogViewModel.factory(container, language))
    val state by tvViewModel.state.collectAsStateWithLifecycle()
    var detailTitle by remember { mutableStateOf<TitleSummary?>(null) }

    CinemaBackground {
        NavigationDrawer(
            drawerContent = {
                Column(
                    Modifier.fillMaxHeight().width(if (hasFocus) 230.dp else 82.dp).background(CinemaColors.Elevated).padding(vertical = 34.dp, horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TvTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            TvTab.HOME -> Icons.Default.Home
                            TvTab.EXPLORE -> Icons.Default.Explore
                            TvTab.SEARCH -> Icons.Default.Search
                            TvTab.LIBRARY -> Icons.Default.CollectionsBookmark
                        }
                        val label = when (tab) {
                            TvTab.HOME -> R.string.home
                            TvTab.EXPLORE -> R.string.explore
                            TvTab.SEARCH -> R.string.search
                            TvTab.LIBRARY -> R.string.library
                        }
                        NavigationDrawerItem(
                            selected = state.tab == tab,
                            onClick = { tvViewModel.selectTab(tab) },
                            leadingContent = { TvIcon(icon, null) },
                        ) { if (hasFocus) TvText(stringResource(label)) }
                    }
                }
            },
        ) {
            when (state.tab) {
                TvTab.HOME -> TvHome(state, container.images, tvViewModel::selectMediaType, tvViewModel::refreshHome, { detailTitle = it })
                TvTab.EXPLORE -> TvExplore(state, container.images, tvViewModel::selectMediaType, tvViewModel::refreshExplore, tvViewModel::loadMoreExplore, { detailTitle = it })
                TvTab.SEARCH -> TvSearch(state, container.images, tvViewModel::setQuery, tvViewModel::setScope, tvViewModel::loadMoreSearch, { detailTitle = it })
                TvTab.LIBRARY -> TvLibrary(state, container.images, tvViewModel::selectCollection, { detailTitle = it })
            }
        }
        detailTitle?.let { title ->
            TvDetailOverlay(title, container, language, onBack = { detailTitle = null }, onTitleClick = { detailTitle = it })
        }
    }
}

@Composable
private fun TvHeader(title: String, type: MediaType?, onType: ((MediaType) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.displayMedium, modifier = Modifier.weight(1f).semantics { heading() })
        if (type != null && onType != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MediaType.entries.forEach { option ->
                    TvButton(onClick = { onType(option) }) { TvText(stringResource(if (option == MediaType.MOVIE) R.string.movies else R.string.tv_series), color = if (type == option) CinemaColors.Accent else CinemaColors.Foreground) }
                }
            }
        }
    }
}

@Composable
private fun TvHome(
    state: TvCatalogUiState,
    images: ImageUrlFactory,
    onType: (MediaType) -> Unit,
    onRetry: () -> Unit,
    onTitle: (TitleSummary) -> Unit,
) {
    when (val home = state.home) {
        Loadable.Idle, Loadable.Loading -> LoadingMessage()
        is Loadable.Failed -> StateMessage(stringResource(R.string.unable_home), message = home.message, retry = onRetry)
        is Loadable.Loaded -> TvHomeContent(home.value, images, state.mediaType, onType, onTitle)
    }
}

@Composable
internal fun TvHomeContent(feed: HomeFeed, images: ImageUrlFactory, type: MediaType, onType: (MediaType) -> Unit, onTitle: (TitleSummary) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 54.dp, top = 34.dp, end = 54.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item { TvHeader(stringResource(R.string.app_name), type, onType) }
        feed.hero?.let { hero -> item { TvHero(hero, images, onTitle) } }
        items(feed.sections, key = { it.id }) { section -> TvShelf(section.title, section.items, images, onTitle) }
    }
}

@Composable
private fun TvHero(title: TitleSummary, images: ImageUrlFactory, onTitle: (TitleSummary) -> Unit) {
    TvFocusableCard(onClick = { onTitle(title) }, modifier = Modifier.fillMaxWidth().height(400.dp)) {
        RemoteArtwork(images.url(title.backdropPath, ImageKind.BACKDROP), title.displayTitle, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(.92f), Color.Black.copy(.35f), Color.Transparent))))
        Column(Modifier.align(Alignment.CenterStart).width(570.dp).padding(42.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(if (title.mediaType == MediaType.MOVIE) R.string.featured_film else R.string.featured_series), color = CinemaColors.Accent, style = MaterialTheme.typography.labelLarge)
            Text(title.displayTitle, style = MaterialTheme.typography.displayLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            RatingBadge(title.voteAverage)
            Text(title.overview, maxLines = 3, overflow = TextOverflow.Ellipsis, color = CinemaColors.Muted, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TvShelf(label: String, titles: List<TitleSummary>, images: ImageUrlFactory, onTitle: (TitleSummary) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(label)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(titles, key = { it.libraryKey }) { title -> TvPoster(title, images, { onTitle(title) }) }
        }
    }
}

@Composable
private fun TvExplore(
    state: TvCatalogUiState,
    images: ImageUrlFactory,
    onType: (MediaType) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onTitle: (TitleSummary) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(start = 54.dp, top = 34.dp, end = 54.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        TvHeader(stringResource(R.string.explore), state.mediaType, onType)
        when (val results = state.explore) {
            Loadable.Idle, Loadable.Loading -> LoadingMessage(Modifier.weight(1f))
            is Loadable.Failed -> StateMessage(stringResource(R.string.explore_unavailable), Modifier.weight(1f), results.message, onRetry)
            is Loadable.Loaded -> LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(vertical = 10.dp), modifier = Modifier.weight(1f)) {
                items(results.value, key = { it.libraryKey }) { title ->
                    TvPoster(title, images, { onTitle(title) }, Modifier.width(220.dp), onFocus = { if (title == results.value.lastOrNull()) onLoadMore() })
                }
            }
        }
    }
}

@Composable
private fun TvSearch(
    state: TvCatalogUiState,
    images: ImageUrlFactory,
    onQuery: (String) -> Unit,
    onScope: (SearchScope) -> Unit,
    onLoadMore: () -> Unit,
    onTitle: (TitleSummary) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(start = 54.dp, top = 34.dp, end = 54.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        TvHeader(stringResource(R.string.search), null)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query, onValueChange = onQuery, singleLine = true, placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.width(620.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CinemaColors.Accent, focusedContainerColor = CinemaColors.Elevated, unfocusedContainerColor = CinemaColors.Elevated),
            )
            SearchScope.entries.forEach { scope -> TvButton(onClick = { onScope(scope) }) {
                TvText(stringResource(when (scope) { SearchScope.ALL -> R.string.all; SearchScope.MOVIE -> R.string.movies; SearchScope.TV -> R.string.tv_series }), color = if (state.scope == scope) CinemaColors.Accent else CinemaColors.Foreground)
            } }
        }
        when (val results = state.search) {
            Loadable.Idle -> StateMessage(stringResource(R.string.find_next_story), Modifier.weight(1f), stringResource(R.string.search_hint))
            Loadable.Loading -> LoadingMessage(Modifier.weight(1f))
            is Loadable.Failed -> StateMessage(stringResource(R.string.search_failed), Modifier.weight(1f), results.message)
            is Loadable.Loaded -> if (results.value.isEmpty()) StateMessage(stringResource(R.string.no_results), Modifier.weight(1f), stringResource(R.string.try_another_search)) else LazyRow(
                horizontalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(vertical = 10.dp), modifier = Modifier.weight(1f),
            ) {
                items(results.value, key = { it.libraryKey }) { title -> TvPoster(title, images, { onTitle(title) }, Modifier.width(220.dp), { if (title == results.value.lastOrNull()) onLoadMore() }) }
            }
        }
    }
}

@Composable
private fun TvLibrary(state: TvCatalogUiState, images: ImageUrlFactory, onCollection: (LibraryCollection) -> Unit, onTitle: (TitleSummary) -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 54.dp, top = 34.dp, end = 54.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        TvHeader(stringResource(R.string.library), null)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LibraryCollection.entries.forEach { collection ->
                TvButton(onClick = { onCollection(collection) }) {
                    TvIcon(if (collection == LibraryCollection.FAVORITES) Icons.Default.Favorite else Icons.AutoMirrored.Filled.PlaylistAddCheck, null)
                    Spacer(Modifier.width(8.dp))
                    TvText(stringResource(if (collection == LibraryCollection.FAVORITES) R.string.favorites else R.string.watchlist), color = if (state.collection == collection) CinemaColors.Accent else CinemaColors.Foreground)
                }
            }
        }
        if (state.libraryItems.isEmpty()) StateMessage(
            stringResource(if (state.collection == LibraryCollection.FAVORITES) R.string.no_favorites else R.string.watchlist_empty), Modifier.weight(1f), stringResource(R.string.add_from_detail),
        ) else LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(vertical = 10.dp), modifier = Modifier.weight(1f)) {
            items(state.libraryItems, key = { it.id }) { item -> TvPoster(item.title, images, { onTitle(item.title) }, Modifier.width(220.dp)) }
        }
    }
}

@Composable
private fun TvPoster(title: TitleSummary, images: ImageUrlFactory, onClick: () -> Unit, modifier: Modifier = Modifier, onFocus: () -> Unit = {}) {
    Column(modifier.width(190.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TvFocusableCard(onClick, Modifier.fillMaxWidth().aspectRatio(.68f), onFocus) {
            RemoteArtwork(images.url(title.posterPath, ImageKind.POSTER), title.displayTitle, Modifier.fillMaxSize())
            RatingBadge(title.voteAverage, Modifier.align(Alignment.BottomEnd).padding(9.dp))
        }
        Text(title.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        title.releaseYear?.let { Text(it, color = CinemaColors.Muted, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun TvFocusableCard(onClick: () -> Unit, modifier: Modifier = Modifier, onFocus: () -> Unit = {}, content: @Composable BoxScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "tv-focus-scale")
    val border by animateColorAsState(if (focused) CinemaColors.Accent else Color.Transparent, label = "tv-focus-border")
    Box(
        modifier.graphicsLayer(scaleX = scale, scaleY = scale).clip(RoundedCornerShape(18.dp)).border(BorderStroke(3.dp, border), RoundedCornerShape(18.dp))
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }.focusable().clickable(onClick = onClick),
        content = content,
    )
}

@Composable
private fun TvDetailOverlay(
    title: TitleSummary,
    container: AppContainer,
    language: String,
    onBack: () -> Unit,
    onTitleClick: (TitleSummary) -> Unit,
    detailViewModel: DetailViewModel = viewModel(key = "tv:${title.libraryKey}", factory = DetailViewModel.factory(title, container.catalog, container.library, language)),
) {
    val state by detailViewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(CinemaColors.Background)) {
        RemoteArtwork(container.images.url(state.title.backdropPath, ImageKind.BACKDROP), state.title.displayTitle, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(CinemaColors.Background, CinemaColors.Background.copy(.92f), Color.Black.copy(.25f)))))
        when (val detail = state.detail) {
            Loadable.Idle, Loadable.Loading -> LoadingMessage()
            is Loadable.Failed -> StateMessage(stringResource(R.string.details_unavailable), Modifier.align(Alignment.Center), detail.message, detailViewModel::refresh)
            is Loadable.Loaded -> TvDetailContent(state, detail.value, container.images, language, detailViewModel::toggle, onTitleClick)
        }
    }
}

@Composable
private fun TvDetailContent(
    state: DetailUiState,
    detail: TitleDetail,
    images: ImageUrlFactory,
    language: String,
    onToggle: (LibraryCollection) -> Unit,
    onTitleClick: (TitleSummary) -> Unit,
) {
    val context = LocalContext.current
    val trailer = preferredTrailer(detail.videos, language.substringBefore('-'))
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(64.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteArtwork(images.url(detail.posterPath, ImageKind.POSTER), detail.title, Modifier.width(250.dp).aspectRatio(.68f).clip(RoundedCornerShape(22.dp)))
                Column(Modifier.width(680.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(detail.title, style = MaterialTheme.typography.displayLarge, modifier = Modifier.semantics { heading() })
                    RatingBadge(detail.voteAverage)
                    Text(detail.overview.ifBlank { stringResource(R.string.no_overview) }, color = CinemaColors.Muted, style = MaterialTheme.typography.bodyLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TvButton(onClick = {
                            trailer?.let {
                                val app = Intent(Intent.ACTION_VIEW, "vnd.youtube:${it.key}".toUri())
                                val web = Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${it.key}".toUri())
                                context.startActivity(if (app.resolveActivity(context.packageManager) != null) app else web)
                            }
                        }, enabled = trailer != null) { TvIcon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); TvText(stringResource(R.string.trailer)) }
                        TvButton(onClick = { onToggle(LibraryCollection.FAVORITES) }) { TvIcon(Icons.Default.Favorite, null); Spacer(Modifier.width(8.dp)); TvText(stringResource(R.string.favorite), color = if (state.membership.isFavorite) CinemaColors.Accent else CinemaColors.Foreground) }
                        TvButton(onClick = { onToggle(LibraryCollection.WATCHLIST) }) { TvIcon(Icons.AutoMirrored.Filled.PlaylistAddCheck, null); Spacer(Modifier.width(8.dp)); TvText(stringResource(R.string.watchlist), color = if (state.membership.isWatchlisted) CinemaColors.Accent else CinemaColors.Foreground) }
                    }
                }
            }
        }
        if (detail.cast.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.cast))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(detail.cast, key = { it.id }) { person ->
                        Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RemoteArtwork(images.url(person.profilePath, ImageKind.PROFILE), person.name, Modifier.size(150.dp).clip(RoundedCornerShape(18.dp)))
                            Text(person.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (detail.similar.isNotEmpty()) item { TvShelf(stringResource(R.string.more_like_this), detail.similar, images, onTitleClick) }
    }
}
