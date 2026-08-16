package com.lamndt.smartmovie.multiplatform

import com.lamndt.smartmovie.multiplatform.data.CatalogApi
import com.lamndt.smartmovie.multiplatform.data.KtorCatalogApi
import com.lamndt.smartmovie.multiplatform.data.LibraryCollection
import com.lamndt.smartmovie.multiplatform.data.LibraryRecord
import com.lamndt.smartmovie.multiplatform.data.PersistentLibrary
import com.lamndt.smartmovie.multiplatform.data.createInstallationId
import com.lamndt.smartmovie.multiplatform.model.AppLocale
import com.lamndt.smartmovie.multiplatform.model.DiscoverFilter
import com.lamndt.smartmovie.multiplatform.model.DiscoverSort
import com.lamndt.smartmovie.multiplatform.model.Genre
import com.lamndt.smartmovie.multiplatform.model.HomeFeed
import com.lamndt.smartmovie.multiplatform.model.ImageConfiguration
import com.lamndt.smartmovie.multiplatform.model.MediaType
import com.lamndt.smartmovie.multiplatform.model.SearchScope
import com.lamndt.smartmovie.multiplatform.model.TitleDetail
import com.lamndt.smartmovie.multiplatform.model.TitleSummary
import com.lamndt.smartmovie.multiplatform.platform.KeyValueStore
import com.lamndt.smartmovie.multiplatform.platform.catalogBaseUrl
import com.lamndt.smartmovie.multiplatform.platform.createKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab { HOME, EXPLORE, SEARCH, LIBRARY }

sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Content<T>(val value: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

data class SmartMovieState(
    val selectedTab: AppTab = AppTab.HOME,
    val locale: AppLocale = AppLocale.ENGLISH,
    val imageConfiguration: ImageConfiguration = ImageConfiguration.Fallback,
    val homeType: MediaType = MediaType.MOVIE,
    val home: LoadState<HomeFeed> = LoadState.Idle,
    val exploreType: MediaType = MediaType.MOVIE,
    val exploreFilter: DiscoverFilter = DiscoverFilter(),
    val genres: List<Genre> = emptyList(),
    val explore: LoadState<List<TitleSummary>> = LoadState.Idle,
    val explorePage: Int = 0,
    val exploreTotalPages: Int = 1,
    val searchQuery: String = "",
    val searchScope: SearchScope = SearchScope.ALL,
    val search: LoadState<List<TitleSummary>> = LoadState.Idle,
    val searchPage: Int = 0,
    val searchTotalPages: Int = 1,
    val libraryCollection: LibraryCollection = LibraryCollection.FAVORITES,
    val library: List<LibraryRecord> = emptyList(),
    val detail: LoadState<TitleDetail> = LoadState.Idle,
    val detailSelection: TitleSummary? = null,
)

class AppController(
    private val store: KeyValueStore = createKeyValueStore(),
    apiFactory: (String) -> CatalogApi = { KtorCatalogApi(baseUrl = catalogBaseUrl(), clientId = it) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val library = PersistentLibrary(store)
    private val clientId = store.getString(INSTALLATION_ID_KEY)
        ?.takeIf { it.length == 36 }
        ?: createInstallationId().also { store.putString(INSTALLATION_ID_KEY, it) }
    private val api = apiFactory(clientId)
    private val mutableState = MutableStateFlow(
        SmartMovieState(locale = AppLocale.fromTag(store.getString(LOCALE_KEY))),
    )
    val state: StateFlow<SmartMovieState> = mutableState.asStateFlow()

    private var homeJob: Job? = null
    private var exploreJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null

    init {
        scope.launch { library.records.collect { records -> mutableState.update { it.copy(library = records) } } }
        scope.launch {
            val configuration = runCatching { api.imageConfiguration() }
                .propagateCancellation()
                .getOrDefault(ImageConfiguration.Fallback)
            mutableState.update { it.copy(imageConfiguration = configuration) }
        }
        reloadHome()
        reloadExplore()
    }

    fun selectTab(tab: AppTab) {
        mutableState.update { it.copy(selectedTab = tab, detailSelection = null, detail = LoadState.Idle) }
        if (tab == AppTab.HOME && state.value.home is LoadState.Idle) reloadHome()
        if (tab == AppTab.EXPLORE && state.value.explore is LoadState.Idle) reloadExplore()
    }

    fun changeLocale(locale: AppLocale) {
        if (locale == state.value.locale) return
        store.putString(LOCALE_KEY, locale.tag)
        mutableState.update {
            it.copy(locale = locale, home = LoadState.Idle, explore = LoadState.Idle, search = LoadState.Idle)
        }
        reloadHome()
        reloadGenresAndExplore()
        if (state.value.searchQuery.isNotBlank()) scheduleSearch(immediate = true)
    }

    fun changeHomeType(mediaType: MediaType) {
        if (mediaType == state.value.homeType) return
        mutableState.update { it.copy(homeType = mediaType) }
        reloadHome()
    }

    fun reloadHome() {
        homeJob?.cancel()
        homeJob = scope.launch {
            mutableState.update { it.copy(home = LoadState.Loading) }
            val snapshot = state.value
            runCatching { api.home(snapshot.homeType, snapshot.locale.backendTag) }
                .propagateCancellation()
                .onSuccess { feed -> mutableState.update { it.copy(home = LoadState.Content(feed)) } }
                .onFailure { failure -> mutableState.update { it.copy(home = LoadState.Error(failure.message.orEmpty())) } }
        }
    }

    fun changeExploreType(mediaType: MediaType) {
        if (mediaType == state.value.exploreType) return
        mutableState.update { it.copy(exploreType = mediaType, exploreFilter = DiscoverFilter()) }
        reloadGenresAndExplore()
    }

    fun setMinimumRating(rating: Double) {
        mutableState.update { it.copy(exploreFilter = it.exploreFilter.copy(minimumRating = rating)) }
        reloadExplore()
    }

    fun setExploreYear(year: Int?) {
        mutableState.update { it.copy(exploreFilter = it.exploreFilter.copy(year = year)) }
        reloadExplore()
    }

    fun setExploreSort(sort: DiscoverSort) {
        mutableState.update { it.copy(exploreFilter = it.exploreFilter.copy(sort = sort)) }
        reloadExplore()
    }

    fun toggleGenre(genreId: Int) {
        mutableState.update {
            val selected = it.exploreFilter.genres.toMutableSet().apply {
                if (!add(genreId)) remove(genreId)
            }
            it.copy(exploreFilter = it.exploreFilter.copy(genres = selected))
        }
        reloadExplore()
    }

    fun resetExplore() {
        mutableState.update { it.copy(exploreFilter = DiscoverFilter()) }
        reloadExplore()
    }

    fun reloadExplore() {
        exploreJob?.cancel()
        exploreJob = scope.launch {
            mutableState.update { it.copy(explore = LoadState.Loading, explorePage = 0) }
            val snapshot = state.value
            runCatching { api.discover(snapshot.exploreType, snapshot.exploreFilter, 1, snapshot.locale.backendTag) }
                .propagateCancellation()
                .onSuccess { page -> mutableState.update {
                    it.copy(
                        explore = LoadState.Content(page.results.distinctBy(TitleSummary::libraryKey)),
                        explorePage = page.page,
                        exploreTotalPages = page.totalPages,
                    )
                } }
                .onFailure { failure -> mutableState.update { it.copy(explore = LoadState.Error(failure.message.orEmpty())) } }
        }
    }

    fun loadMoreExplore() {
        val snapshot = state.value
        val content = (snapshot.explore as? LoadState.Content)?.value ?: return
        if (snapshot.explorePage >= snapshot.exploreTotalPages || exploreJob?.isActive == true) return
        exploreJob = scope.launch {
            runCatching {
                api.discover(snapshot.exploreType, snapshot.exploreFilter, snapshot.explorePage + 1, snapshot.locale.backendTag)
            }.propagateCancellation().onSuccess { page -> mutableState.update {
                it.copy(
                    explore = LoadState.Content((content + page.results).distinctBy(TitleSummary::libraryKey)),
                    explorePage = page.page,
                    exploreTotalPages = page.totalPages,
                )
            } }
        }
    }

    fun updateSearchQuery(query: String) {
        mutableState.update { it.copy(searchQuery = query) }
        scheduleSearch(immediate = false)
    }

    fun changeSearchScope(scope: SearchScope) {
        mutableState.update { it.copy(searchScope = scope) }
        scheduleSearch(immediate = true)
    }

    fun retrySearch() = scheduleSearch(immediate = true)

    fun loadMoreSearch() {
        val snapshot = state.value
        val content = (snapshot.search as? LoadState.Content)?.value ?: return
        if (snapshot.searchPage >= snapshot.searchTotalPages || searchJob?.isActive == true) return
        searchJob = scope.launch {
            runCatching {
                api.search(snapshot.searchQuery, snapshot.searchScope, snapshot.searchPage + 1, snapshot.locale.backendTag)
            }.propagateCancellation().onSuccess { page -> mutableState.update {
                it.copy(
                    search = LoadState.Content((content + page.results).distinctBy(TitleSummary::libraryKey)),
                    searchPage = page.page,
                    searchTotalPages = page.totalPages,
                )
            } }
        }
    }

    fun openDetail(title: TitleSummary) {
        detailJob?.cancel()
        mutableState.update { it.copy(detailSelection = title, detail = LoadState.Loading) }
        detailJob = scope.launch {
            val snapshot = state.value
            runCatching { api.detail(title.mediaType, title.id, snapshot.locale.backendTag) }
                .propagateCancellation()
                .onSuccess { detail -> mutableState.update { it.copy(detail = LoadState.Content(detail)) } }
                .onFailure { failure -> mutableState.update { it.copy(detail = LoadState.Error(failure.message.orEmpty())) } }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        mutableState.update { it.copy(detailSelection = null, detail = LoadState.Idle) }
    }

    fun retryDetail() = state.value.detailSelection?.let(::openDetail)

    fun toggleLibrary(title: TitleSummary, collection: LibraryCollection) = library.toggle(title, collection)

    fun changeLibraryCollection(collection: LibraryCollection) {
        mutableState.update { it.copy(libraryCollection = collection) }
    }

    fun close() = scope.cancel()

    private fun reloadGenresAndExplore() {
        scope.launch {
            val snapshot = state.value
            val genres = runCatching { api.genres(snapshot.exploreType, snapshot.locale.backendTag) }
                .propagateCancellation()
                .getOrDefault(emptyList())
            mutableState.update { it.copy(genres = genres) }
        }
        reloadExplore()
    }

    private fun scheduleSearch(immediate: Boolean) {
        searchJob?.cancel()
        val query = state.value.searchQuery.trim()
        if (query.isEmpty()) {
            mutableState.update { it.copy(search = LoadState.Idle, searchPage = 0) }
            return
        }
        searchJob = scope.launch {
            if (!immediate) delay(350)
            mutableState.update { it.copy(search = LoadState.Loading, searchPage = 0) }
            val snapshot = state.value
            runCatching { api.search(query, snapshot.searchScope, 1, snapshot.locale.backendTag) }
                .propagateCancellation()
                .onSuccess { page -> mutableState.update {
                    it.copy(
                        search = LoadState.Content(page.results.distinctBy(TitleSummary::libraryKey)),
                        searchPage = page.page,
                        searchTotalPages = page.totalPages,
                    )
                } }
                .onFailure { failure -> mutableState.update { it.copy(search = LoadState.Error(failure.message.orEmpty())) } }
        }
    }

    private companion object {
        const val INSTALLATION_ID_KEY = "smartmovie_installation_id"
        const val LOCALE_KEY = "smartmovie_locale"
    }
}

private fun <T> Result<T>.propagateCancellation(): Result<T> = also { result ->
    if (result.exceptionOrNull() is CancellationException) throw result.exceptionOrNull()!!
}
