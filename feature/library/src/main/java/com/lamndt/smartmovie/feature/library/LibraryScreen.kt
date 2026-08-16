package com.lamndt.smartmovie.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.lamndt.smartmovie.data.ImageUrlFactory
import com.lamndt.smartmovie.designsystem.PosterCard
import com.lamndt.smartmovie.designsystem.R
import com.lamndt.smartmovie.designsystem.StateMessage
import com.lamndt.smartmovie.designsystem.isWindowWidthAtLeast
import com.lamndt.smartmovie.model.ImageKind
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.model.LibrarySort
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.TitleSummary

@Composable
fun LibraryRoute(
    library: LibraryRepository,
    images: ImageUrlFactory,
    onTitleClick: (TitleSummary) -> Unit,
    modifier: Modifier = Modifier,
    libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(library)),
) {
    val state by libraryViewModel.state.collectAsStateWithLifecycle()
    val wide = isWindowWidthAtLeast(600)
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = if (wide) 40.dp else 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.library), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryCollection.entries.forEach { collection ->
                    FilterChip(
                        selected = state.collection == collection,
                        onClick = { libraryViewModel.selectCollection(collection) },
                        leadingIcon = { Icon(if (collection == LibraryCollection.FAVORITES) Icons.Default.Favorite else Icons.AutoMirrored.Filled.PlaylistAddCheck, null) },
                        label = { Text(stringResource(if (collection == LibraryCollection.FAVORITES) R.string.favorites else R.string.watchlist)) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf<MediaType?>(null, MediaType.MOVIE, MediaType.TV).forEach { type ->
                    FilterChip(
                        selected = state.mediaType == type,
                        onClick = { libraryViewModel.selectMediaType(type) },
                        label = { Text(stringResource(when (type) { null -> R.string.all; MediaType.MOVIE -> R.string.movies; MediaType.TV -> R.string.tv_series })) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibrarySort.entries.forEach { sort ->
                    FilterChip(
                        selected = state.sort == sort,
                        onClick = { libraryViewModel.selectSort(sort) },
                        label = { Text(stringResource(when (sort) { LibrarySort.RECENTLY_ADDED -> R.string.recently_added; LibrarySort.TITLE -> R.string.title_sort; LibrarySort.RELEASE_DATE -> R.string.release_date })) },
                    )
                }
            }
        }
        if (state.items.isEmpty()) {
            StateMessage(
                stringResource(if (state.collection == LibraryCollection.FAVORITES) R.string.no_favorites else R.string.watchlist_empty),
                modifier = Modifier.weight(1f),
                message = stringResource(R.string.add_from_detail),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (wide) 170.dp else 145.dp),
                contentPadding = PaddingValues(start = if (wide) 40.dp else 20.dp, end = if (wide) 40.dp else 20.dp, bottom = 112.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.weight(1f),
            ) {
                items(state.items, key = { it.id }) { item ->
                    PosterCard(item.title, images.url(item.title.posterPath, ImageKind.POSTER), { onTitleClick(item.title) }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
