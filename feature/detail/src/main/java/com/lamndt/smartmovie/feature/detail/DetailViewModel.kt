package com.lamndt.smartmovie.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibraryMembership
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.model.Loadable
import com.lamndt.smartmovie.model.TitleDetail
import com.lamndt.smartmovie.model.TitleSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val title: TitleSummary,
    val detail: Loadable<TitleDetail> = Loadable.Idle,
    val membership: LibraryMembership = LibraryMembership(),
)

class DetailViewModel(
    private val initialTitle: TitleSummary,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val language: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DetailUiState(initialTitle))
    val state: StateFlow<DetailUiState> = mutableState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            library.observeMembership(initialTitle.libraryKey).collect { membership ->
                mutableState.update { it.copy(membership = membership) }
            }
        }
    }

    fun refresh() = viewModelScope.launch {
        mutableState.update { it.copy(detail = Loadable.Loading) }
        try {
            val result = catalog.detail(initialTitle.mediaType, initialTitle.id, language)
            mutableState.update { it.copy(title = result.summary, detail = Loadable.Loaded(result)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            mutableState.update { it.copy(detail = Loadable.Failed(failure.message.orEmpty())) }
        }
    }

    fun toggle(collection: LibraryCollection) = viewModelScope.launch {
        val title = (mutableState.value.detail as? Loadable.Loaded)?.value?.summary ?: mutableState.value.title
        library.toggle(title, collection)
    }

    companion object {
        fun factory(title: TitleSummary, catalog: CatalogRepository, library: LibraryRepository, language: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = DetailViewModel(title, catalog, library, language) as T
            }
    }
}
