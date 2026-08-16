package com.lamndt.smartmovie.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.model.LibrarySnapshot
import com.lamndt.smartmovie.model.LibrarySort
import com.lamndt.smartmovie.model.MediaType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LibraryUiState(
    val collection: LibraryCollection = LibraryCollection.FAVORITES,
    val mediaType: MediaType? = null,
    val sort: LibrarySort = LibrarySort.RECENTLY_ADDED,
    val items: List<LibrarySnapshot> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val library: LibraryRepository) : ViewModel() {
    private val collection = MutableStateFlow(LibraryCollection.FAVORITES)
    private val mediaType = MutableStateFlow<MediaType?>(null)
    private val sort = MutableStateFlow(LibrarySort.RECENTLY_ADDED)

    val state: StateFlow<LibraryUiState> = combine(collection, mediaType, sort) { selectedCollection, selectedType, selectedSort ->
        Triple(selectedCollection, selectedType, selectedSort)
    }.flatMapLatest { (selectedCollection, selectedType, selectedSort) ->
        library.observeItems(selectedCollection, selectedType, selectedSort).map { items ->
            LibraryUiState(selectedCollection, selectedType, selectedSort, items)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun selectCollection(value: LibraryCollection) { collection.value = value }
    fun selectMediaType(value: MediaType?) { mediaType.value = value }
    fun selectSort(value: LibrarySort) { sort.value = value }

    companion object {
        fun factory(library: LibraryRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LibraryViewModel(library) as T
        }
    }
}
