package com.lamndt.smartmovie.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.lamndt.smartmovie.data.ImageUrlFactory
import com.lamndt.smartmovie.designsystem.CinemaColors
import com.lamndt.smartmovie.designsystem.LoadingMessage
import com.lamndt.smartmovie.designsystem.MediaTypeSelector
import com.lamndt.smartmovie.designsystem.PosterCard
import com.lamndt.smartmovie.designsystem.R
import com.lamndt.smartmovie.designsystem.StateMessage
import com.lamndt.smartmovie.designsystem.TitleRow
import com.lamndt.smartmovie.designsystem.isWindowWidthAtLeast
import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.DiscoverSort
import com.lamndt.smartmovie.model.ImageKind
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.model.WatchMonetizationType
import java.time.Year
import kotlin.math.roundToInt

@Composable
fun ExploreRoute(
    catalog: CatalogRepository,
    images: ImageUrlFactory,
    language: String,
    onTitleClick: (TitleSummary) -> Unit,
    modifier: Modifier = Modifier,
    region: String = "US",
    includeAdult: Boolean = false,
    exploreViewModel: ExploreViewModel = viewModel(
        key = "explore:$language",
        factory = ExploreViewModel.factory(catalog, language),
    ),
) {
    val state by exploreViewModel.state.collectAsStateWithLifecycle()
    val titles = exploreViewModel.titles.collectAsLazyPagingItems()
    LaunchedEffect(language, region, includeAdult) {
        exploreViewModel.updateContext(region, includeAdult)
    }
    ExploreScreen(
        state = state,
        itemCount = titles.itemCount,
        item = { titles[it] },
        refreshState = titles.loadState.refresh,
        appendState = titles.loadState.append,
        onRetry = titles::retry,
        images = images,
        onTitleClick = onTitleClick,
        onMediaType = exploreViewModel::selectMediaType,
        onGrid = exploreViewModel::setGrid,
        onShowFilters = exploreViewModel::showFilters,
        onDismissFilters = exploreViewModel::dismissFilters,
        onUpdateDraft = exploreViewModel::updateDraft,
        onReset = exploreViewModel::resetDraft,
        onApply = exploreViewModel::applyFilters,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExploreScreen(
    state: ExploreUiState,
    itemCount: Int,
    item: (Int) -> TitleSummary?,
    refreshState: LoadState,
    appendState: LoadState,
    onRetry: () -> Unit,
    images: ImageUrlFactory,
    onTitleClick: (TitleSummary) -> Unit,
    onMediaType: (MediaType) -> Unit,
    onGrid: (Boolean) -> Unit,
    onShowFilters: () -> Unit,
    onDismissFilters: () -> Unit,
    onUpdateDraft: ((com.lamndt.smartmovie.model.DiscoverFilter) -> com.lamndt.smartmovie.model.DiscoverFilter) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wide = isWindowWidthAtLeast(600)
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = if (wide) 40.dp else 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.explore), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f).semantics { heading() })
                IconButton(onClick = onShowFilters) { Icon(Icons.Default.FilterList, stringResource(R.string.filters), tint = CinemaColors.Accent) }
                IconButton(onClick = { onGrid(!state.isGrid) }) {
                    Icon(if (state.isGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, stringResource(if (state.isGrid) R.string.list else R.string.grid))
                }
            }
            MediaTypeSelector(state.mediaType, onMediaType, Modifier.width(330.dp).fillMaxWidth())
        }
        when {
            refreshState is LoadState.Loading && itemCount == 0 -> LoadingMessage(Modifier.weight(1f))
            refreshState is LoadState.Error && itemCount == 0 -> StateMessage(
                stringResource(R.string.explore_unavailable), Modifier.weight(1f), refreshState.error.message, onRetry,
            )
            itemCount == 0 -> StateMessage(stringResource(R.string.no_results), Modifier.weight(1f), stringResource(R.string.try_another_search))
            state.isGrid -> LazyVerticalGrid(
                columns = GridCells.Adaptive(if (wide) 170.dp else 145.dp),
                contentPadding = PaddingValues(start = if (wide) 40.dp else 20.dp, end = if (wide) 40.dp else 20.dp, bottom = 112.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(itemCount, key = { index -> item(index)?.libraryKey ?: "placeholder:$index" }) { index ->
                    item(index)?.let { title -> PosterCard(title, images.url(title.posterPath, ImageKind.POSTER), { onTitleClick(title) }, Modifier.fillMaxWidth()) }
                }
                if (appendState is LoadState.Loading) item { LoadingMessage(Modifier.height(120.dp)) }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = if (wide) 40.dp else 20.dp, end = if (wide) 40.dp else 20.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f),
            ) {
                items(itemCount, key = { index -> item(index)?.libraryKey ?: "placeholder:$index" }) { index ->
                    item(index)?.let { title -> TitleRow(title, images.url(title.posterPath, ImageKind.POSTER), { onTitleClick(title) }) }
                }
                if (appendState is LoadState.Loading) item { LoadingMessage(Modifier.height(100.dp)) }
            }
        }
    }
    if (state.showFilters) {
        ModalBottomSheet(onDismissRequest = onDismissFilters, containerColor = CinemaColors.Elevated) {
            FilterSheet(state, onUpdateDraft, onReset, onApply)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    state: ExploreUiState,
    onUpdate: ((com.lamndt.smartmovie.model.DiscoverFilter) -> com.lamndt.smartmovie.model.DiscoverFilter) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    val currentYear = Year.now().value
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.filters), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).semantics { heading() })
            TextButton(onClick = onReset) { Text(stringResource(R.string.reset), color = CinemaColors.Accent) }
        }
        Text(stringResource(R.string.genres), style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            state.genres.forEach { genre ->
                FilterChip(
                    selected = genre.id in state.draftFilter.genres,
                    onClick = { onUpdate { filter -> filter.copy(genres = if (genre.id in filter.genres) filter.genres - genre.id else filter.genres + genre.id) } },
                    label = { Text(genre.name) },
                )
            }
        }
        Text("${stringResource(R.string.minimum_rating)}: ${"%.1f".format(state.draftFilter.minimumRating)}", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = state.draftFilter.minimumRating.toFloat(), onValueChange = { value -> onUpdate { it.copy(minimumRating = (value * 2).roundToInt() / 2.0) } },
            valueRange = 0f..9f, steps = 17,
        )
        Text("${stringResource(R.string.release_year)}: ${state.draftFilter.year?.toString() ?: stringResource(R.string.any_year)}", style = MaterialTheme.typography.titleMedium)
        RangeSlider(
            value = 1950f..(state.draftFilter.year ?: currentYear).toFloat(),
            onValueChange = { range -> onUpdate { it.copy(year = range.endInclusive.roundToInt().takeUnless { year -> year == currentYear }) } },
            valueRange = 1950f..currentYear.toFloat(),
        )
        if (state.advancedDiscoverEnabled) {
            Text(stringResource(R.string.release_date_range), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterTextField(
                value = state.draftFilter.releaseDateFrom.orEmpty(),
                label = stringResource(R.string.date_from),
                onValueChange = { value -> onUpdate { it.copy(releaseDateFrom = value) } },
                modifier = Modifier.weight(1f),
            )
            FilterTextField(
                value = state.draftFilter.releaseDateThrough.orEmpty(),
                label = stringResource(R.string.date_through),
                onValueChange = { value -> onUpdate { it.copy(releaseDateThrough = value) } },
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterTextField(
                value = state.draftFilter.originalLanguage.orEmpty(),
                label = stringResource(R.string.original_language),
                onValueChange = { value -> onUpdate { it.copy(originalLanguage = value) } },
                modifier = Modifier.weight(1f),
            )
            FilterTextField(
                value = state.draftFilter.originCountry.orEmpty(),
                label = stringResource(R.string.origin_country),
                onValueChange = { value -> onUpdate { it.copy(originCountry = value) } },
                modifier = Modifier.weight(1f),
            )
        }
        if (state.mediaType == MediaType.MOVIE) {
            Text(stringResource(R.string.certification), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterTextField(
                    value = state.draftFilter.certificationMinimum.orEmpty(),
                    label = stringResource(R.string.minimum),
                    onValueChange = { value -> onUpdate { it.copy(certificationMinimum = value) } },
                    modifier = Modifier.weight(1f),
                )
                FilterTextField(
                    value = state.draftFilter.certificationMaximum.orEmpty(),
                    label = stringResource(R.string.maximum),
                    onValueChange = { value -> onUpdate { it.copy(certificationMaximum = value) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(stringResource(R.string.runtime_and_votes), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterTextField(
                value = state.draftFilter.minimumRuntime?.toString().orEmpty(),
                label = stringResource(R.string.minimum_runtime),
                onValueChange = { value -> onUpdate { it.copy(minimumRuntime = value.toIntOrNull()) } },
                modifier = Modifier.weight(1f),
            )
            FilterTextField(
                value = state.draftFilter.maximumRuntime?.toString().orEmpty(),
                label = stringResource(R.string.maximum_runtime),
                onValueChange = { value -> onUpdate { it.copy(maximumRuntime = value.toIntOrNull()) } },
                modifier = Modifier.weight(1f),
            )
        }
        FilterTextField(
            value = state.draftFilter.minimumVoteCount.takeIf { it > 0 }?.toString().orEmpty(),
            label = stringResource(R.string.minimum_vote_count),
            onValueChange = { value -> onUpdate { it.copy(minimumVoteCount = value.toIntOrNull() ?: 0) } },
        )
        Text(stringResource(R.string.watch_providers), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.discover_provider_region, state.draftFilter.region.orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaColors.Muted,
        )
        val providers = state.configuration?.watchProviders?.values(state.mediaType).orEmpty()
        if (providers.isEmpty()) {
            Text(stringResource(R.string.providers_unavailable), color = CinemaColors.Muted)
        } else {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                providers.forEach { provider ->
                    FilterChip(
                        selected = provider.id in state.draftFilter.watchProviderIds,
                        onClick = {
                            onUpdate { filter ->
                                filter.copy(
                                    watchProviderIds = if (provider.id in filter.watchProviderIds) {
                                        filter.watchProviderIds - provider.id
                                    } else {
                                        filter.watchProviderIds + provider.id
                                    },
                                )
                            }
                        },
                        label = { Text(provider.name) },
                    )
                }
            }
        }
        Text(stringResource(R.string.availability), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            WatchMonetizationType.entries.forEach { type ->
                val label = when (type) {
                    WatchMonetizationType.SUBSCRIPTION -> R.string.streaming
                    WatchMonetizationType.FREE -> R.string.free
                    WatchMonetizationType.ADS -> R.string.with_ads
                    WatchMonetizationType.RENT -> R.string.rent
                    WatchMonetizationType.BUY -> R.string.buy
                }
                FilterChip(
                    selected = type in state.draftFilter.monetizationTypes,
                    onClick = {
                        onUpdate { filter ->
                            filter.copy(
                                monetizationTypes = if (type in filter.monetizationTypes) {
                                    filter.monetizationTypes - type
                                } else {
                                    filter.monetizationTypes + type
                                },
                            )
                        }
                    },
                    label = { Text(stringResource(label)) },
                )
            }
        }
            Text(stringResource(R.string.justwatch_attribution), style = MaterialTheme.typography.labelSmall, color = CinemaColors.Muted)
        }
        Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            DiscoverSort.entries.forEach { sort ->
                val label = when (sort) {
                    DiscoverSort.POPULARITY -> R.string.popularity
                    DiscoverSort.RATING -> R.string.rating
                    DiscoverSort.RELEASE_DATE -> R.string.release_date
                }
                FilterChip(selected = state.draftFilter.sort == sort, onClick = { onUpdate { it.copy(sort = sort) } }, label = { Text(stringResource(label)) })
            }
        }
        Button(onClick = onApply, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = CinemaColors.Accent)) {
            Text(stringResource(R.string.apply))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FilterTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.filter_value_hint)) },
        singleLine = true,
        modifier = modifier,
    )
}
