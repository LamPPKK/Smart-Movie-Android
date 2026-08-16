package com.lamndt.smartmovie.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lamndt.smartmovie.data.DiscoverPagingSource
import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.DiscoverFilter
import com.lamndt.smartmovie.model.DiscoverSort
import com.lamndt.smartmovie.model.Genre
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.TitleSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreUiState(
    val mediaType: MediaType = MediaType.MOVIE,
    val genres: List<Genre> = emptyList(),
    val appliedFilter: DiscoverFilter = DiscoverFilter(),
    val draftFilter: DiscoverFilter = DiscoverFilter(),
    val isGrid: Boolean = true,
    val showFilters: Boolean = false,
    val genresLoading: Boolean = false,
    val genresError: String? = null,
)

private data class DiscoverRequest(val mediaType: MediaType, val filter: DiscoverFilter)

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModel(
    private val catalog: CatalogRepository,
    private val language: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = mutableState.asStateFlow()
    private val request = MutableStateFlow(DiscoverRequest(MediaType.MOVIE, DiscoverFilter()))

    val titles: Flow<PagingData<TitleSummary>> = request.flatMapLatest { current ->
        Pager(PagingConfig(pageSize = 20, prefetchDistance = 6, enablePlaceholders = false)) {
            DiscoverPagingSource(catalog, current.mediaType, current.filter, language)
        }.flow
    }.cachedIn(viewModelScope)

    init { loadGenres() }

    fun selectMediaType(type: MediaType) {
        if (type == mutableState.value.mediaType) return
        mutableState.update {
            it.copy(mediaType = type, genres = emptyList(), appliedFilter = DiscoverFilter(), draftFilter = DiscoverFilter())
        }
        request.value = DiscoverRequest(type, DiscoverFilter())
        loadGenres()
    }

    fun setGrid(grid: Boolean) = mutableState.update { it.copy(isGrid = grid) }
    fun showFilters() = mutableState.update { it.copy(showFilters = true, draftFilter = it.appliedFilter) }
    fun dismissFilters() = mutableState.update { it.copy(showFilters = false) }
    fun updateDraft(transform: (DiscoverFilter) -> DiscoverFilter) = mutableState.update { it.copy(draftFilter = transform(it.draftFilter)) }
    fun resetDraft() = mutableState.update { it.copy(draftFilter = DiscoverFilter()) }

    fun applyFilters() {
        val current = mutableState.value
        mutableState.update { it.copy(appliedFilter = current.draftFilter, showFilters = false) }
        request.value = DiscoverRequest(current.mediaType, current.draftFilter)
    }

    private fun loadGenres() = viewModelScope.launch {
        mutableState.update { it.copy(genresLoading = true, genresError = null) }
        try {
            val genres = catalog.genres(mutableState.value.mediaType, language)
            mutableState.update { it.copy(genres = genres, genresLoading = false) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            mutableState.update { it.copy(genresLoading = false, genresError = failure.message) }
        }
    }

    companion object {
        fun factory(catalog: CatalogRepository, language: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ExploreViewModel(catalog, language) as T
        }
    }
}
