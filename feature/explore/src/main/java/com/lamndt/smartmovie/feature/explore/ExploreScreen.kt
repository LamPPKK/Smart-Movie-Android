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
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import java.time.Year
import kotlin.math.roundToInt

@Composable
fun ExploreRoute(
    catalog: CatalogRepository,
    images: ImageUrlFactory,
    language: String,
    onTitleClick: (TitleSummary) -> Unit,
    modifier: Modifier = Modifier,
    exploreViewModel: ExploreViewModel = viewModel(factory = ExploreViewModel.factory(catalog, language)),
) {
    val state by exploreViewModel.state.collectAsStateWithLifecycle()
    val titles = exploreViewModel.titles.collectAsLazyPagingItems()
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
