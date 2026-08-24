package com.lamndt.smartmovie.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.lamndt.smartmovie.designsystem.PosterCard
import com.lamndt.smartmovie.designsystem.R
import com.lamndt.smartmovie.designsystem.StateMessage
import com.lamndt.smartmovie.designsystem.isWindowWidthAtLeast
import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.CatalogEntity
import com.lamndt.smartmovie.model.CatalogSearchMode
import com.lamndt.smartmovie.model.ExternalIdSource
import com.lamndt.smartmovie.model.ImageKind
import com.lamndt.smartmovie.model.SearchScopeV2
import com.lamndt.smartmovie.model.TitleSummary

@Composable
fun SearchRoute(
    catalog: CatalogRepository,
    images: ImageUrlFactory,
    language: String,
    onTitleClick: (TitleSummary) -> Unit,
    modifier: Modifier = Modifier,
    onEntityClick: (CatalogEntity) -> Unit = { entity -> if (entity is CatalogEntity.Title) onTitleClick(entity.value) },
    searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(catalog, language)),
) {
    val state by searchViewModel.state.collectAsStateWithLifecycle()
    val results = searchViewModel.entityResults.collectAsLazyPagingItems()
    val wide = isWindowWidthAtLeast(600)
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = if (wide) 40.dp else 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.search), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CatalogSearchMode.entries.size) { index ->
                    val mode = CatalogSearchMode.entries[index]
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = { searchViewModel.setMode(mode) },
                        label = { Text(stringResource(if (mode == CatalogSearchMode.CATALOG) R.string.catalog else R.string.external_id)) },
                    )
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = searchViewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton(onClick = { searchViewModel.setQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                },
                placeholder = {
                    Text(
                        if (state.mode == CatalogSearchMode.CATALOG) stringResource(R.string.search_hint)
                        else state.externalIdSource.example,
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CinemaColors.Accent,
                    unfocusedBorderColor = CinemaColors.Muted.copy(alpha = 0.5f),
                    focusedContainerColor = CinemaColors.Elevated,
                    unfocusedContainerColor = CinemaColors.Elevated,
                ),
            )
            if (state.mode == CatalogSearchMode.CATALOG) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchScopeV2.entries.size) { index ->
                        val scope = SearchScopeV2.entries[index]
                        val label = when (scope) {
                            SearchScopeV2.ALL -> R.string.all
                            SearchScopeV2.MOVIE -> R.string.movies
                            SearchScopeV2.TV -> R.string.tv_series
                            SearchScopeV2.PERSON -> R.string.people
                            SearchScopeV2.COLLECTION -> R.string.collections
                            SearchScopeV2.COMPANY -> R.string.companies
                            SearchScopeV2.KEYWORD -> R.string.keywords
                        }
                        FilterChip(selected = state.entityScope == scope, onClick = { searchViewModel.setEntityScope(scope) }, label = { Text(stringResource(label)) })
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ExternalIdSource.entries.size) { index ->
                        val source = ExternalIdSource.entries[index]
                        FilterChip(
                            selected = state.externalIdSource == source,
                            onClick = { searchViewModel.setExternalIdSource(source) },
                            label = { Text(source.displayName) },
                        )
                    }
                }
                Button(
                    onClick = searchViewModel::findExternalId,
                    enabled = state.query.isNotBlank() && !state.externalLoading,
                ) { Text(stringResource(R.string.find_matches)) }
            }
        }
        if (state.mode == CatalogSearchMode.EXTERNAL_ID) when {
            !state.externalSubmitted -> StateMessage(
                stringResource(R.string.search_by_external_id),
                Modifier.weight(1f),
                stringResource(R.string.external_id_hint),
            )
            state.externalLoading -> LoadingMessage(Modifier.weight(1f))
            state.externalError != null -> StateMessage(
                stringResource(R.string.search_failed),
                Modifier.weight(1f),
                state.externalError,
                searchViewModel::findExternalId,
            )
            state.externalResults.isEmpty() -> StateMessage(
                stringResource(R.string.no_results),
                Modifier.weight(1f),
                stringResource(R.string.try_another_external_id),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(if (wide) 170.dp else 145.dp),
                contentPadding = PaddingValues(start = if (wide) 40.dp else 20.dp, end = if (wide) 40.dp else 20.dp, bottom = 112.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(state.externalResults, key = CatalogEntity::stableKey) { entity ->
                    EntitySearchCard(entity, images, { onEntityClick(entity) })
                }
            }
        } else when {
            state.query.isBlank() -> StateMessage(stringResource(R.string.find_next_story), Modifier.weight(1f), stringResource(R.string.search_hint))
            results.loadState.refresh is LoadState.Loading && results.itemCount == 0 -> LoadingMessage(Modifier.weight(1f))
            results.loadState.refresh is LoadState.Error && results.itemCount == 0 -> StateMessage(
                stringResource(R.string.search_failed),
                Modifier.weight(1f),
                (results.loadState.refresh as LoadState.Error).error.message,
                results::retry,
            )
            results.itemCount == 0 -> StateMessage(stringResource(R.string.no_results), Modifier.weight(1f), stringResource(R.string.try_another_search))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(if (wide) 170.dp else 145.dp),
                contentPadding = PaddingValues(start = if (wide) 40.dp else 20.dp, end = if (wide) 40.dp else 20.dp, bottom = 112.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.weight(1f),
            ) {
                items(results.itemCount, key = { index -> results[index]?.stableKey ?: "placeholder:$index" }) { index ->
                    results[index]?.let { entity -> EntitySearchCard(entity, images, { onEntityClick(entity) }) }
                }
                if (results.loadState.append is LoadState.Loading) item { LoadingMessage() }
            }
        }
    }
}

@Composable
private fun EntitySearchCard(entity: CatalogEntity, images: ImageUrlFactory, onClick: () -> Unit) {
    if (entity is CatalogEntity.Title) {
        PosterCard(entity.value, images.url(entity.value.posterPath, ImageKind.POSTER), onClick, Modifier.fillMaxWidth())
        return
    }
    val (name, path) = when (entity) {
        is CatalogEntity.Person -> entity.value.name to entity.value.profilePath
        is CatalogEntity.Collection -> entity.value.name to (entity.value.posterPath ?: entity.value.backdropPath)
        is CatalogEntity.Organization -> entity.value.name to entity.value.logoPath
        is CatalogEntity.Keyword -> entity.value.name to null
        is CatalogEntity.Season -> entity.value.name to entity.value.posterPath
        is CatalogEntity.Episode -> entity.value.name to entity.value.stillPath
        is CatalogEntity.Title -> error("handled")
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.lamndt.smartmovie.designsystem.RemoteArtwork(
            images.url(path, if (entity is CatalogEntity.Person || entity is CatalogEntity.Organization) ImageKind.PROFILE else ImageKind.POSTER),
            name,
            Modifier.fillMaxWidth().aspectRatio(.82f),
        )
        Text(entity.entityKind.wireValue.uppercase(), color = CinemaColors.Accent, style = MaterialTheme.typography.labelMedium)
        Text(name, maxLines = 2, style = MaterialTheme.typography.titleMedium)
    }
}
