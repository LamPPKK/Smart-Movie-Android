package com.lamndt.smartmovie.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
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
import com.lamndt.smartmovie.model.ImageKind
import com.lamndt.smartmovie.model.SearchScope
import com.lamndt.smartmovie.model.TitleSummary

@Composable
fun SearchRoute(
    catalog: CatalogRepository,
    images: ImageUrlFactory,
    language: String,
    onTitleClick: (TitleSummary) -> Unit,
    modifier: Modifier = Modifier,
    searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(catalog, language)),
) {
    val state by searchViewModel.state.collectAsStateWithLifecycle()
    val results = searchViewModel.results.collectAsLazyPagingItems()
    val wide = isWindowWidthAtLeast(600)
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = if (wide) 40.dp else 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.search), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
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
                placeholder = { Text(stringResource(R.string.search_hint)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CinemaColors.Accent,
                    unfocusedBorderColor = CinemaColors.Muted.copy(alpha = 0.5f),
                    focusedContainerColor = CinemaColors.Elevated,
                    unfocusedContainerColor = CinemaColors.Elevated,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchScope.entries.forEach { scope ->
                    val label = when (scope) {
                        SearchScope.ALL -> R.string.all
                        SearchScope.MOVIE -> R.string.movies
                        SearchScope.TV -> R.string.tv_series
                    }
                    FilterChip(selected = state.scope == scope, onClick = { searchViewModel.setScope(scope) }, label = { Text(stringResource(label)) })
                }
            }
        }
        when {
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
                items(results.itemCount, key = { index -> results[index]?.libraryKey ?: "placeholder:$index" }) { index ->
                    results[index]?.let { title -> PosterCard(title, images.url(title.posterPath, ImageKind.POSTER), { onTitleClick(title) }, Modifier.fillMaxWidth()) }
                }
                if (results.loadState.append is LoadState.Loading) item { LoadingMessage() }
            }
        }
    }
}
