package com.lamndt.smartmovie.multiplatform

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lamndt.smartmovie.multiplatform.data.LibraryCollection
import com.lamndt.smartmovie.multiplatform.data.LibraryRecord
import com.lamndt.smartmovie.multiplatform.model.AppLocale
import com.lamndt.smartmovie.multiplatform.model.DiscoverSort
import com.lamndt.smartmovie.multiplatform.model.HomeFeed
import com.lamndt.smartmovie.multiplatform.model.ImageUrlFactory
import com.lamndt.smartmovie.multiplatform.model.MediaType
import com.lamndt.smartmovie.multiplatform.model.SearchScope
import com.lamndt.smartmovie.multiplatform.model.TitleDetail
import com.lamndt.smartmovie.multiplatform.model.TitleSummary
import com.lamndt.smartmovie.multiplatform.model.UiStrings
import com.lamndt.smartmovie.multiplatform.model.preferredTrailer
import com.lamndt.smartmovie.multiplatform.model.strings
import com.lamndt.smartmovie.multiplatform.platform.openExternalUrl
import com.lamndt.smartmovie.multiplatform.platform.platformName
import com.lamndt.smartmovie.multiplatform.ui.CinemaBackground
import com.lamndt.smartmovie.multiplatform.ui.CinemaCardShape
import com.lamndt.smartmovie.multiplatform.ui.CinemaColors
import com.lamndt.smartmovie.multiplatform.ui.LoadingPane
import com.lamndt.smartmovie.multiplatform.ui.MessagePane
import com.lamndt.smartmovie.multiplatform.ui.PosterCard
import com.lamndt.smartmovie.multiplatform.ui.RatingBadge
import com.lamndt.smartmovie.multiplatform.ui.RemoteArtwork
import com.lamndt.smartmovie.multiplatform.ui.SectionTitle
import com.lamndt.smartmovie.multiplatform.ui.SmartMovieTheme

@Composable
fun SmartMovieApp(controller: AppController = remember { AppController() }) {
    val state by controller.state.collectAsState()
    val copy = strings(state.locale)

    DisposableEffect(controller) { onDispose(controller::close) }

    SmartMovieTheme {
        CinemaBackground {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val expanded = maxWidth >= 860.dp
                val splitDetail = maxWidth >= 1220.dp
                if (expanded) {
                    Row(Modifier.fillMaxSize()) {
                        DesktopNavigation(state.selectedTab, copy, controller::selectTab)
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            AppContent(state, copy, controller, Modifier.fillMaxSize())
                            if (state.detailSelection != null) {
                                DetailPane(
                                    state = state,
                                    copy = copy,
                                    controller = controller,
                                    modifier = if (splitDetail) {
                                        Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(570.dp)
                                    } else Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            AppContent(state, copy, controller, Modifier.fillMaxSize())
                            if (state.detailSelection != null) {
                                DetailPane(state, copy, controller, Modifier.fillMaxSize())
                            }
                        }
                        CompactNavigation(state.selectedTab, copy, controller::selectTab)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppContent(
    state: SmartMovieState,
    copy: UiStrings,
    controller: AppController,
    modifier: Modifier,
) {
    Column(modifier) {
        AppHeader(state.locale, copy, controller::changeLocale)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.selectedTab) {
                AppTab.HOME -> HomeScreen(state, copy, controller)
                AppTab.EXPLORE -> ExploreScreen(state, copy, controller)
                AppTab.SEARCH -> SearchScreen(state, copy, controller)
                AppTab.LIBRARY -> LibraryScreen(state, copy, controller)
            }
        }
    }
}

@Composable
private fun AppHeader(locale: AppLocale, copy: UiStrings, onLocale: (AppLocale) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(WindowInsets.safeDrawing.asPaddingValues()).padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = CinemaColors.Accent, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.padding(9.dp))
            }
            Column {
                Text(
                    "SMARTMOVIE",
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaColors.Foreground,
                    letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
                )
                Text("${copy.platformEdition} · ${platformName()}", style = MaterialTheme.typography.labelMedium, color = CinemaColors.Muted)
            }
        }
        LocalePicker(locale, copy, onLocale)
    }
}

@Composable
private fun LocalePicker(locale: AppLocale, copy: UiStrings, onLocale: (AppLocale) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(locale.nativeName) },
            trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, Modifier.size(18.dp)) },
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLocale.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.nativeName) },
                    onClick = { expanded = false; onLocale(option) },
                    leadingIcon = if (option == locale) ({ Icon(Icons.Default.Star, contentDescription = copy.language) }) else null,
                )
            }
        }
    }
}

@Composable
private fun DesktopNavigation(selected: AppTab, copy: UiStrings, onSelect: (AppTab) -> Unit) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight().width(92.dp),
        containerColor = CinemaColors.Elevated.copy(alpha = 0.92f),
        header = { Spacer(Modifier.height(78.dp)) },
    ) {
        AppTab.entries.forEach { tab ->
            val selectedNow = tab == selected
            NavigationRailItem(
                selected = selectedNow,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon(selectedNow), contentDescription = tab.label(copy)) },
                label = { Text(tab.label(copy), maxLines = 1) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun CompactNavigation(selected: AppTab, copy: UiStrings, onSelect: (AppTab) -> Unit) {
    NavigationBar(
        containerColor = CinemaColors.Elevated,
        modifier = Modifier.padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
    ) {
        AppTab.entries.forEach { tab ->
            val selectedNow = tab == selected
            NavigationBarItem(
                selected = selectedNow,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon(selectedNow), contentDescription = tab.label(copy)) },
                label = { Text(tab.label(copy)) },
            )
        }
    }
}

private fun AppTab.label(copy: UiStrings): String = when (this) {
    AppTab.HOME -> copy.home
    AppTab.EXPLORE -> copy.explore
    AppTab.SEARCH -> copy.search
    AppTab.LIBRARY -> copy.library
}

private fun AppTab.icon(selected: Boolean): ImageVector = when (this) {
    AppTab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    AppTab.EXPLORE -> if (selected) Icons.Filled.Explore else Icons.Outlined.Explore
    AppTab.SEARCH -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
    AppTab.LIBRARY -> if (selected) Icons.AutoMirrored.Filled.LibraryBooks else Icons.AutoMirrored.Outlined.LibraryBooks
}

@Composable
private fun HomeScreen(state: SmartMovieState, copy: UiStrings, controller: AppController) {
    when (val home = state.home) {
        LoadState.Idle, LoadState.Loading -> LoadingPane()
        is LoadState.Error -> MessagePane(copy.serviceError, home.message, copy.retry, controller::reloadHome)
        is LoadState.Content -> HomeContent(home.value, state, copy, controller)
    }
}

@Composable
private fun HomeContent(feed: HomeFeed, state: SmartMovieState, copy: UiStrings, controller: AppController) {
    val images = remember(state.imageConfiguration) { ImageUrlFactory(state.imageConfiguration) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            MediaTypeChips(state.homeType, copy, controller::changeHomeType)
        }
        feed.hero?.let { hero ->
            item { HeroCard(hero, images, copy) { controller.openDetail(hero) } }
        }
        feed.sections.forEach { section ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    SectionTitle(section.title)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(section.items.distinctBy(TitleSummary::libraryKey), key = { it.libraryKey }) { title ->
                            PosterCard(title, images, typeLabel(title.mediaType, copy), { controller.openDetail(title) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    hero: TitleSummary,
    images: ImageUrlFactory,
    copy: UiStrings,
    onOpen: () -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(430.dp).clip(RoundedCornerShape(28.dp)).background(CinemaColors.Elevated),
    ) {
        val wideHero = maxWidth > 700.dp
        RemoteArtwork(images.backdrop(hero.backdropPath), hero.displayTitle, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.48f), Color.Transparent),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, CinemaColors.Background.copy(alpha = 0.52f))),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(30.dp).widthIn(max = if (wideHero) 620.dp else 470.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RatingBadge(hero.voteAverage)
                Text(typeLabel(hero.mediaType, copy).uppercase(), style = MaterialTheme.typography.labelLarge, color = CinemaColors.Muted)
                hero.releaseYear?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = CinemaColors.Muted) }
            }
            Text(
                hero.displayTitle,
                style = if (wideHero) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(hero.overview, style = MaterialTheme.typography.bodyLarge, color = CinemaColors.Foreground.copy(alpha = 0.82f), maxLines = 3)
            Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = CinemaColors.Accent), modifier = Modifier.height(50.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(copy.details, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun MediaTypeChips(selected: MediaType, copy: UiStrings, onSelect: (MediaType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MediaType.entries.forEach { mediaType ->
            FilterChip(
                selected = mediaType == selected,
                onClick = { onSelect(mediaType) },
                label = { Text(typeLabel(mediaType, copy)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CinemaColors.Accent),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ExploreScreen(state: SmartMovieState, copy: UiStrings, controller: AppController) {
    val images = remember(state.imageConfiguration) { ImageUrlFactory(state.imageConfiguration) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionTitle(copy.discover)
                MediaTypeChips(state.exploreType, copy, controller::changeExploreType)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    item {
                        FilterChip(
                            selected = state.exploreFilter.minimumRating == 0.0,
                            onClick = { controller.setMinimumRating(0.0) },
                            label = { Text("${copy.rating}: ${copy.all}") },
                        )
                    }
                    listOf(6.0, 7.0, 8.0, 9.0).forEach { rating ->
                        item {
                            FilterChip(
                                selected = state.exploreFilter.minimumRating == rating,
                                onClick = { controller.setMinimumRating(rating) },
                                label = { Text("${copy.rating} ${rating.toInt()}+") },
                            )
                        }
                    }
                    DiscoverSort.entries.forEach { sort ->
                        item {
                            FilterChip(
                                selected = state.exploreFilter.sort == sort,
                                onClick = { controller.setExploreSort(sort) },
                                label = { Text(sortLabel(sort, copy)) },
                            )
                        }
                    }
                    item { AssistChip(onClick = controller::resetExplore, label = { Text(copy.reset) }) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    item {
                        FilterChip(
                            selected = state.exploreFilter.year == null,
                            onClick = { controller.setExploreYear(null) },
                            label = { Text("${copy.year}: ${copy.all}") },
                        )
                    }
                    items((CURRENT_YEAR downTo 1950).toList()) { year ->
                        FilterChip(
                            selected = state.exploreFilter.year == year,
                            onClick = { controller.setExploreYear(year) },
                            label = { Text(year.toString()) },
                        )
                    }
                }
                if (state.genres.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.genres.forEach { genre ->
                            FilterChip(
                                selected = genre.id in state.exploreFilter.genres,
                                onClick = { controller.toggleGenre(genre.id) },
                                label = { Text(genre.name) },
                            )
                        }
                    }
                }
            }
        }
        when (val result = state.explore) {
            LoadState.Idle, LoadState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingPane(Modifier.height(320.dp)) }
            is LoadState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                MessagePane(copy.serviceError, result.message, copy.retry, controller::reloadExplore, Modifier.height(340.dp))
            }
            is LoadState.Content -> {
                if (result.value.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { MessagePane(copy.noResults, modifier = Modifier.height(320.dp)) }
                } else {
                    items(result.value, key = { it.libraryKey }) { title ->
                        PosterCard(title, images, typeLabel(title.mediaType, copy), { controller.openDetail(title) }, Modifier.fillMaxWidth())
                    }
                    if (state.explorePage < state.exploreTotalPages) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Button(
                                onClick = controller::loadMoreExplore,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CinemaColors.Surface),
                            ) { Text(copy.loadMore) }
                        }
                    }
                }
            }
        }
    }
}

private const val CURRENT_YEAR = 2026

@Composable
private fun SearchScreen(state: SmartMovieState, copy: UiStrings, controller: AppController) {
    val images = remember(state.imageConfiguration) { ImageUrlFactory(state.imageConfiguration) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                SectionTitle(copy.search)
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = controller::updateSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(copy.searchHint) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CinemaColors.Accent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                        focusedContainerColor = CinemaColors.Elevated,
                        unfocusedContainerColor = CinemaColors.Elevated,
                    ),
                    shape = RoundedCornerShape(17.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    SearchScope.entries.forEach { scope ->
                        FilterChip(
                            selected = state.searchScope == scope,
                            onClick = { controller.changeSearchScope(scope) },
                            label = { Text(scopeLabel(scope, copy)) },
                        )
                    }
                }
            }
        }
        when (val result = state.search) {
            LoadState.Idle -> item(span = { GridItemSpan(maxLineSpan) }) {
                MessagePane(copy.searchHint, modifier = Modifier.height(320.dp))
            }
            LoadState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingPane(Modifier.height(320.dp)) }
            is LoadState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                MessagePane(copy.serviceError, result.message, copy.retry, controller::retrySearch, Modifier.height(340.dp))
            }
            is LoadState.Content -> {
                if (result.value.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { MessagePane(copy.noResults, modifier = Modifier.height(320.dp)) }
                } else {
                    items(result.value, key = { it.libraryKey }) { title ->
                        PosterCard(title, images, typeLabel(title.mediaType, copy), { controller.openDetail(title) }, Modifier.fillMaxWidth())
                    }
                    if (state.searchPage < state.searchTotalPages) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Button(
                                onClick = controller::loadMoreSearch,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CinemaColors.Surface),
                            ) { Text(copy.loadMore) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(state: SmartMovieState, copy: UiStrings, controller: AppController) {
    val images = remember(state.imageConfiguration) { ImageUrlFactory(state.imageConfiguration) }
    val records = state.library.filter {
        if (state.libraryCollection == LibraryCollection.FAVORITES) it.isFavorite else it.isWatchlisted
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                SectionTitle(copy.library)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    LibraryCollection.entries.forEach { collection ->
                        FilterChip(
                            selected = state.libraryCollection == collection,
                            onClick = { controller.changeLibraryCollection(collection) },
                            label = { Text(if (collection == LibraryCollection.FAVORITES) copy.favorites else copy.watchlist) },
                        )
                    }
                }
            }
        }
        if (records.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { MessagePane(copy.emptyLibrary, modifier = Modifier.height(320.dp)) }
        } else {
            items(records, key = { it.title.libraryKey }) { record ->
                PosterCard(
                    record.title,
                    images,
                    typeLabel(record.title.mediaType, copy),
                    { controller.openDetail(record.title) },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DetailPane(
    state: SmartMovieState,
    copy: UiStrings,
    controller: AppController,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        color = CinemaColors.Background,
        shadowElevation = 24.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        when (val detail = state.detail) {
            LoadState.Idle, LoadState.Loading -> Box(Modifier.fillMaxSize()) {
                DetailClose(copy.close, controller::closeDetail, Modifier.align(Alignment.TopEnd).padding(18.dp))
                LoadingPane()
            }
            is LoadState.Error -> Box(Modifier.fillMaxSize()) {
                DetailClose(copy.close, controller::closeDetail, Modifier.align(Alignment.TopEnd).padding(18.dp))
                MessagePane(copy.serviceError, detail.message, copy.retry, controller::retryDetail)
            }
            is LoadState.Content -> DetailContent(detail.value, state, copy, controller)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(detail: TitleDetail, state: SmartMovieState, copy: UiStrings, controller: AppController) {
    val images = remember(state.imageConfiguration) { ImageUrlFactory(state.imageConfiguration) }
    val record = state.library.firstOrNull { it.title.libraryKey == detail.summary.libraryKey }
    val trailer = preferredTrailer(detail.videos, state.locale.backendTag)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(330.dp)) {
                RemoteArtwork(images.backdrop(detail.backdropPath), detail.title, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, CinemaColors.Background.copy(alpha = 0.36f), CinemaColors.Background)),
                    ),
                )
                DetailClose(copy.close, controller::closeDetail, Modifier.align(Alignment.TopEnd).padding(18.dp))
                Column(
                    Modifier.align(Alignment.BottomStart).padding(horizontal = 28.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RatingBadge(detail.voteAverage)
                        detail.releaseDate?.take(4)?.let { Text(it, color = CinemaColors.Muted) }
                        detail.status?.let { Text("• $it", color = CinemaColors.Muted) }
                    }
                    Text(detail.title, style = MaterialTheme.typography.displayMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    trailer?.let {
                        Button(
                            onClick = { openExternalUrl("https://www.youtube.com/watch?v=${it.key}") },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaColors.Accent),
                            modifier = Modifier.height(50.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(copy.trailer, Modifier.padding(start = 7.dp))
                        }
                    }
                    Button(
                        onClick = { controller.toggleLibrary(detail.summary, LibraryCollection.FAVORITES) },
                        colors = ButtonDefaults.buttonColors(containerColor = CinemaColors.Surface),
                        modifier = Modifier.height(50.dp),
                    ) {
                        Icon(if (record?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null)
                        Text(if (record?.isFavorite == true) copy.removeFavorite else copy.favorite, Modifier.padding(start = 7.dp))
                    }
                    Button(
                        onClick = { controller.toggleLibrary(detail.summary, LibraryCollection.WATCHLIST) },
                        colors = ButtonDefaults.buttonColors(containerColor = CinemaColors.Surface),
                        modifier = Modifier.height(50.dp),
                    ) {
                        Icon(if (record?.isWatchlisted == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null)
                        Text(if (record?.isWatchlisted == true) copy.removeWatchlist else copy.watchLater, Modifier.padding(start = 7.dp))
                    }
                }
                if (detail.genres.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        detail.genres.forEach { genre -> AssistChip(onClick = {}, label = { Text(genre.name) }) }
                    }
                }
                SectionTitle(copy.story)
                Text(detail.overview, style = MaterialTheme.typography.bodyLarge, color = CinemaColors.Foreground.copy(alpha = 0.82f))
            }
        }
        if (detail.cast.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionTitle(copy.cast, Modifier.padding(horizontal = 28.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(detail.cast, key = { it.id }) { member ->
                            Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                RemoteArtwork(
                                    images.profile(member.profilePath),
                                    member.name,
                                    Modifier.fillMaxWidth().aspectRatio(0.78f).clip(CinemaCardShape),
                                )
                                Text(member.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                member.character?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = CinemaColors.Muted, maxLines = 2) }
                            }
                        }
                    }
                }
            }
        }
        if (detail.similar.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionTitle(copy.similar, Modifier.padding(horizontal = 28.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(detail.similar.distinctBy(TitleSummary::libraryKey), key = { it.libraryKey }) { title ->
                            PosterCard(title, images, typeLabel(title.mediaType, copy), { controller.openDetail(title) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailClose(label: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClose,
        modifier = modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.74f)).semantics {
            contentDescription = label
        },
    ) { Icon(Icons.Default.Close, contentDescription = null, tint = Color.White) }
}

private fun typeLabel(type: MediaType, copy: UiStrings): String = if (type == MediaType.MOVIE) copy.movies else copy.tvSeries

private fun scopeLabel(scope: SearchScope, copy: UiStrings): String = when (scope) {
    SearchScope.ALL -> copy.all
    SearchScope.MOVIE -> copy.movies
    SearchScope.TV -> copy.tvSeries
}

private fun sortLabel(sort: DiscoverSort, copy: UiStrings): String = when (sort) {
    DiscoverSort.POPULARITY -> copy.popularity
    DiscoverSort.RATING -> copy.topRated
    DiscoverSort.RELEASE_DATE -> copy.releaseDate
}
