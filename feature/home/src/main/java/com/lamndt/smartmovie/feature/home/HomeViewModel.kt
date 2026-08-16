package com.lamndt.smartmovie.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.HomeFeed
import com.lamndt.smartmovie.model.Loadable
import com.lamndt.smartmovie.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val mediaType: MediaType = MediaType.MOVIE,
    val feed: Loadable<HomeFeed> = Loadable.Idle,
)

class HomeViewModel(
    private val catalog: CatalogRepository,
    private val language: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init { refresh() }

    fun selectMediaType(type: MediaType) {
        if (type == mutableState.value.mediaType) return
        mutableState.update { it.copy(mediaType = type) }
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableState.update { it.copy(feed = Loadable.Loading) }
            try {
                val result = catalog.home(mutableState.value.mediaType, language)
                mutableState.update { it.copy(feed = Loadable.Loaded(result)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                mutableState.update { it.copy(feed = Loadable.Failed(failure.message.orEmpty())) }
            }
        }
    }

    companion object {
        fun factory(catalog: CatalogRepository, language: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(catalog, language) as T
            }
    }
}
