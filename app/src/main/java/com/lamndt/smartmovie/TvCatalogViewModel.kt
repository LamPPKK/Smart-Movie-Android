package com.lamndt.smartmovie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.DiscoverFilter
import com.lamndt.smartmovie.model.HomeFeed
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.model.LibrarySnapshot
import com.lamndt.smartmovie.model.LibrarySort
import com.lamndt.smartmovie.model.Loadable
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.SearchScope
import com.lamndt.smartmovie.model.TitleSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TvTab { HOME, EXPLORE, SEARCH, LIBRARY }

data class TvCatalogUiState(
    val tab: TvTab = TvTab.HOME,
    val mediaType: MediaType = MediaType.MOVIE,
    val home: Loadable<HomeFeed> = Loadable.Idle,
    val explore: Loadable<List<TitleSummary>> = Loadable.Idle,
    val explorePage: Int = 0,
    val exploreTotalPages: Int = 1,
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL,
    val search: Loadable<List<TitleSummary>> = Loadable.Idle,
    val searchPage: Int = 0,
    val searchTotalPages: Int = 1,
    val collection: LibraryCollection = LibraryCollection.FAVORITES,
    val libraryItems: List<LibrarySnapshot> = emptyList(),
)

class TvCatalogViewModel(
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val language: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TvCatalogUiState())
    val state: StateFlow<TvCatalogUiState> = mutableState.asStateFlow()
    private var homeJob: Job? = null
    private var exploreJob: Job? = null
    private var searchJob: Job? = null
    private var libraryJob: Job? = null

    init {
        refreshHome()
        refreshExplore()
        observeLibrary()
    }

    fun selectTab(tab: TvTab) = mutableState.update { it.copy(tab = tab) }

    fun selectMediaType(type: MediaType) {
        if (type == mutableState.value.mediaType) return
        mutableState.update { it.copy(mediaType = type, explorePage = 0, exploreTotalPages = 1) }
        refreshHome()
        refreshExplore()
    }

    fun refreshHome() {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            mutableState.update { it.copy(home = Loadable.Loading) }
            try {
                val feed = catalog.home(mutableState.value.mediaType, language)
                mutableState.update { it.copy(home = Loadable.Loaded(feed)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { mutableState.update { it.copy(home = Loadable.Failed(failure.message.orEmpty())) } }
        }
    }

    fun refreshExplore() {
        exploreJob?.cancel()
        exploreJob = viewModelScope.launch {
            mutableState.update { it.copy(explore = Loadable.Loading, explorePage = 0) }
            loadExplorePage(1, replace = true)
        }
    }

    fun loadMoreExplore() {
        val current = mutableState.value
        if (exploreJob?.isActive == true || current.explorePage >= current.exploreTotalPages) return
        exploreJob = viewModelScope.launch { loadExplorePage(current.explorePage + 1, replace = false) }
    }

    private suspend fun loadExplorePage(page: Int, replace: Boolean) {
        try {
            val result = catalog.discover(mutableState.value.mediaType, DiscoverFilter(), page, language)
            mutableState.update { state ->
                val previous = if (replace) emptyList() else (state.explore as? Loadable.Loaded)?.value.orEmpty()
                state.copy(
                    explore = Loadable.Loaded((previous + result.results).distinctBy { it.libraryKey }),
                    explorePage = page,
                    exploreTotalPages = result.totalPages,
                )
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Throwable) { mutableState.update { it.copy(explore = Loadable.Failed(failure.message.orEmpty())) } }
    }

    fun setQuery(query: String) {
        mutableState.update { it.copy(query = query, searchPage = 0, searchTotalPages = 1) }
        scheduleSearch()
    }

    fun setScope(scope: SearchScope) {
        mutableState.update { it.copy(scope = scope, searchPage = 0, searchTotalPages = 1) }
        scheduleSearch()
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        val query = mutableState.value.query.trim()
        if (query.isBlank()) {
            mutableState.update { it.copy(search = Loadable.Idle) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            mutableState.update { it.copy(search = Loadable.Loading) }
            loadSearchPage(1, replace = true)
        }
    }

    fun loadMoreSearch() {
        val current = mutableState.value
        if (searchJob?.isActive == true || current.searchPage >= current.searchTotalPages || current.query.isBlank()) return
        searchJob = viewModelScope.launch { loadSearchPage(current.searchPage + 1, replace = false) }
    }

    private suspend fun loadSearchPage(page: Int, replace: Boolean) {
        try {
            val current = mutableState.value
            val result = catalog.search(current.query.trim(), current.scope, page, language)
            mutableState.update { state ->
                val previous = if (replace) emptyList() else (state.search as? Loadable.Loaded)?.value.orEmpty()
                state.copy(
                    search = Loadable.Loaded((previous + result.results).distinctBy { it.libraryKey }),
                    searchPage = page,
                    searchTotalPages = result.totalPages,
                )
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Throwable) { mutableState.update { it.copy(search = Loadable.Failed(failure.message.orEmpty())) } }
    }

    fun selectCollection(collection: LibraryCollection) {
        mutableState.update { it.copy(collection = collection) }
        observeLibrary()
    }

    private fun observeLibrary() {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch {
            val selected = mutableState.value.collection
            library.observeItems(selected, null, LibrarySort.RECENTLY_ADDED).collect { items ->
                mutableState.update { it.copy(libraryItems = items) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, language: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TvCatalogViewModel(container.catalog, container.library, language) as T
        }
    }
}
