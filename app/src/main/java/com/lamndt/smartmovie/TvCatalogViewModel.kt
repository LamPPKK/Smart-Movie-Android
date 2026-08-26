package com.lamndt.smartmovie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.CatalogV2Repository
import com.lamndt.smartmovie.model.DiscoverConfiguration
import com.lamndt.smartmovie.model.DiscoverFilter
import com.lamndt.smartmovie.model.Genre
import com.lamndt.smartmovie.model.HomeFeed
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.model.LibrarySnapshot
import com.lamndt.smartmovie.model.LibrarySort
import com.lamndt.smartmovie.model.Loadable
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.SearchScope
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.model.WatchMonetizationType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class TvTab { HOME, EXPLORE, SEARCH, LIBRARY, PROFILE }

data class TvCatalogUiState(
    val tab: TvTab = TvTab.HOME,
    val mediaType: MediaType = MediaType.MOVIE,
    val home: Loadable<HomeFeed> = Loadable.Idle,
    val explore: Loadable<List<TitleSummary>> = Loadable.Idle,
    val exploreFilter: DiscoverFilter = DiscoverFilter(),
    val exploreDraft: DiscoverFilter = DiscoverFilter(),
    val genres: List<Genre> = emptyList(),
    val discoverConfiguration: DiscoverConfiguration? = null,
    val advancedDiscoverEnabled: Boolean = false,
    val showExploreFilters: Boolean = false,
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
    private var optionsJob: Job? = null
    private var searchJob: Job? = null
    private var libraryJob: Job? = null
    private var exploreRevision = 0
    private var optionsRevision = 0

    init {
        loadCapabilities()
        refreshHome()
        refreshExplore()
        observeLibrary()
    }

    fun selectTab(tab: TvTab) = mutableState.update { it.copy(tab = tab) }

    fun selectMediaType(type: MediaType) {
        if (type == mutableState.value.mediaType) return
        mutableState.update { state ->
            val current = state.exploreFilter
            val filter = DiscoverFilter(
                region = current.region,
                certificationCountry = current.region.takeIf {
                    type == MediaType.MOVIE && state.advancedDiscoverEnabled
                },
                includeAdult = current.includeAdult,
            )
            state.copy(
                mediaType = type,
                exploreFilter = filter,
                exploreDraft = filter,
                genres = emptyList(),
                explorePage = 0,
                exploreTotalPages = 1,
            )
        }
        refreshHome()
        refreshExplore()
    }

    fun updateContext(region: String, includeAdult: Boolean) {
        val normalizedRegion = region.trim().uppercase(Locale.ROOT).takeIf { it.length == 2 } ?: "US"
        val current = mutableState.value
        val regionChanged = current.exploreFilter.region != normalizedRegion
        val applied = current.exploreFilter.withContext(
            current.mediaType,
            normalizedRegion,
            includeAdult,
            regionChanged,
        ).let { if (current.advancedDiscoverEnabled) it else it.basic() }
        val draft = current.exploreDraft.withContext(
            current.mediaType,
            normalizedRegion,
            includeAdult,
            regionChanged,
        ).let { if (current.advancedDiscoverEnabled) it else it.basic() }
        if (applied == current.exploreFilter && draft == current.exploreDraft) return
        mutableState.update {
            it.copy(
                exploreFilter = applied,
                exploreDraft = draft,
                discoverConfiguration = if (regionChanged) null else it.discoverConfiguration,
            )
        }
        refreshExplore()
    }

    fun showExploreFilters() = mutableState.update {
        it.copy(showExploreFilters = true, exploreDraft = it.exploreFilter)
    }
    fun dismissExploreFilters() = mutableState.update {
        it.copy(showExploreFilters = false, exploreDraft = it.exploreFilter)
    }
    fun updateExploreFilter(transform: (DiscoverFilter) -> DiscoverFilter) = mutableState.update {
        it.copy(exploreDraft = transform(it.exploreDraft))
    }
    fun toggleGenre(id: Int) = updateExploreFilter { filter ->
        filter.copy(genres = filter.genres.toMutableSet().apply { if (!add(id)) remove(id) })
    }
    fun toggleWatchProvider(id: Int) = updateExploreFilter { filter ->
        filter.copy(watchProviderIds = filter.watchProviderIds.toMutableSet().apply { if (!add(id)) remove(id) })
    }
    fun toggleMonetization(type: WatchMonetizationType) = updateExploreFilter { filter ->
        filter.copy(monetizationTypes = filter.monetizationTypes.toMutableSet().apply { if (!add(type)) remove(type) })
    }
    fun resetExploreFilter() = mutableState.update {
        val current = it.exploreFilter
        it.copy(
            exploreDraft = DiscoverFilter(
                region = current.region,
                certificationCountry = if (it.mediaType == MediaType.MOVIE && it.advancedDiscoverEnabled) current.region else null,
                includeAdult = current.includeAdult,
            ),
        )
    }
    fun applyExploreFilter() {
        mutableState.update {
            val normalized = it.exploreDraft.normalized().let { filter ->
                if (it.advancedDiscoverEnabled) filter else filter.basic()
            }
            it.copy(exploreFilter = normalized, exploreDraft = normalized, showExploreFilters = false)
        }
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
        loadDiscoverOptions()
        exploreRevision += 1
        val requestedRevision = exploreRevision
        exploreJob = viewModelScope.launch {
            mutableState.update { it.copy(explore = Loadable.Loading, explorePage = 0) }
            loadExplorePage(1, replace = true, requestedRevision)
        }
    }

    fun loadMoreExplore() {
        val current = mutableState.value
        if (exploreJob?.isActive == true || current.explorePage >= current.exploreTotalPages) return
        val requestedRevision = exploreRevision
        exploreJob = viewModelScope.launch {
            loadExplorePage(current.explorePage + 1, replace = false, requestedRevision)
        }
    }

    private suspend fun loadExplorePage(page: Int, replace: Boolean, requestedRevision: Int) {
        val requestedType = mutableState.value.mediaType
        val requestedFilter = mutableState.value.exploreFilter
        val supportsAdvancedDiscover = mutableState.value.advancedDiscoverEnabled
        try {
            val result = if (supportsAdvancedDiscover) {
                catalog.discover(requestedType, requestedFilter, page, language)
            } else {
                catalog.discoverBasic(requestedType, requestedFilter, page, language)
            }
            mutableState.update { state ->
                if (exploreRevision != requestedRevision ||
                    state.mediaType != requestedType ||
                    state.exploreFilter != requestedFilter ||
                    state.advancedDiscoverEnabled != supportsAdvancedDiscover
                ) return@update state
                val previous = if (replace) emptyList() else (state.explore as? Loadable.Loaded)?.value.orEmpty()
                state.copy(
                    explore = Loadable.Loaded((previous + result.results).distinctBy { it.libraryKey }),
                    explorePage = page,
                    exploreTotalPages = result.totalPages,
                )
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Throwable) {
            mutableState.update { state ->
                if (exploreRevision == requestedRevision &&
                    state.mediaType == requestedType &&
                    state.exploreFilter == requestedFilter &&
                    state.advancedDiscoverEnabled == supportsAdvancedDiscover
                ) state.copy(explore = Loadable.Failed(failure.message.orEmpty())) else state
            }
        }
    }

    private fun loadDiscoverOptions() {
        optionsJob?.cancel()
        optionsRevision += 1
        val requestedRevision = optionsRevision
        val snapshot = mutableState.value
        optionsJob = viewModelScope.launch {
            val genres = runCatching { catalog.genres(snapshot.mediaType, language) }
            val configuration = runCatching {
                if (snapshot.advancedDiscoverEnabled) {
                    (catalog as? CatalogV2Repository)?.discoverConfiguration(language, snapshot.exploreFilter.region)
                } else {
                    null
                }
            }
            val current = mutableState.value
            if (optionsRevision == requestedRevision &&
                current.mediaType == snapshot.mediaType &&
                current.exploreFilter.region == snapshot.exploreFilter.region
            ) {
                mutableState.update { state ->
                    state.copy(
                        genres = genres.getOrElse { state.genres },
                        discoverConfiguration = configuration.getOrElse { state.discoverConfiguration },
                    )
                }
            }
        }
    }

    private fun loadCapabilities() {
        val v2 = catalog as? CatalogV2Repository ?: return
        viewModelScope.launch {
            runCatching { v2.capabilities() }
                .onSuccess { capabilities ->
                    val enabled = capabilities.supportsCatalog("advanced_discover")
                    mutableState.update { state ->
                        val certificationCountry = state.exploreFilter.region.takeIf {
                            enabled && state.mediaType == MediaType.MOVIE
                        }
                        val applied = if (enabled) {
                            state.exploreFilter.copy(certificationCountry = certificationCountry)
                        } else {
                            state.exploreFilter.basic()
                        }
                        val draft = if (enabled) {
                            state.exploreDraft.copy(certificationCountry = certificationCountry)
                        } else {
                            applied
                        }
                        state.copy(
                            advancedDiscoverEnabled = enabled,
                            exploreFilter = applied,
                            exploreDraft = draft,
                            discoverConfiguration = if (enabled) state.discoverConfiguration else null,
                        )
                    }
                    refreshExplore()
                }
        }
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

private fun DiscoverFilter.normalized(): DiscoverFilter {
    val minimum = minimumRuntime?.coerceAtLeast(0)
    val maximum = maximumRuntime?.coerceAtLeast(0)
    return copy(
        releaseDateFrom = releaseDateFrom.clean(),
        releaseDateThrough = releaseDateThrough.clean(),
        originalLanguage = originalLanguage.clean()?.lowercase(Locale.ROOT),
        originCountry = originCountry.clean()?.uppercase(Locale.ROOT),
        certificationCountry = certificationCountry.clean()?.uppercase(Locale.ROOT),
        certificationMinimum = certificationMinimum.clean(),
        certificationMaximum = certificationMaximum.clean(),
        minimumRuntime = if (minimum != null && maximum != null) minOf(minimum, maximum) else minimum,
        maximumRuntime = if (minimum != null && maximum != null) maxOf(minimum, maximum) else maximum,
        minimumVoteCount = minimumVoteCount.coerceAtLeast(0),
        region = region.clean()?.uppercase(Locale.ROOT),
    )
}

private fun DiscoverFilter.basic(): DiscoverFilter = DiscoverFilter(
    genres = genres,
    year = year,
    minimumRating = minimumRating,
    sort = sort,
    region = region,
    includeAdult = includeAdult,
)

private fun DiscoverFilter.withContext(
    mediaType: MediaType,
    region: String,
    includeAdult: Boolean,
    regionChanged: Boolean,
): DiscoverFilter = copy(
    region = region,
    certificationCountry = when {
        mediaType != MediaType.MOVIE -> null
        regionChanged -> region
        else -> certificationCountry
    },
    certificationMinimum = if (regionChanged) null else certificationMinimum,
    certificationMaximum = if (regionChanged) null else certificationMaximum,
    watchProviderIds = if (regionChanged) emptySet() else watchProviderIds,
    includeAdult = includeAdult,
)

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
