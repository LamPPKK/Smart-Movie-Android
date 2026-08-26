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
import com.lamndt.smartmovie.model.CatalogV2Repository
import com.lamndt.smartmovie.model.DiscoverConfiguration
import com.lamndt.smartmovie.model.DiscoverFilter
import com.lamndt.smartmovie.model.Genre
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.TitleSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class ExploreUiState(
    val mediaType: MediaType = MediaType.MOVIE,
    val genres: List<Genre> = emptyList(),
    val configuration: DiscoverConfiguration? = null,
    val advancedDiscoverEnabled: Boolean = false,
    val appliedFilter: DiscoverFilter = DiscoverFilter(),
    val draftFilter: DiscoverFilter = DiscoverFilter(),
    val isGrid: Boolean = true,
    val showFilters: Boolean = false,
    val genresLoading: Boolean = false,
    val genresError: String? = null,
)

private data class DiscoverRequest(
    val mediaType: MediaType,
    val filter: DiscoverFilter,
    val advancedDiscoverEnabled: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModel(
    private val catalog: CatalogRepository,
    private val language: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = mutableState.asStateFlow()
    private val request = MutableStateFlow(DiscoverRequest(MediaType.MOVIE, DiscoverFilter(), false))
    private var genreJob: Job? = null

    val titles: Flow<PagingData<TitleSummary>> = request.flatMapLatest { current ->
        Pager(PagingConfig(pageSize = 20, prefetchDistance = 6, enablePlaceholders = false)) {
            DiscoverPagingSource(catalog, current.mediaType, current.filter, language, current.advancedDiscoverEnabled)
        }.flow
    }.cachedIn(viewModelScope)

    init {
        loadCapabilities()
        loadGenres()
    }

    fun updateContext(region: String?, includeAdult: Boolean) {
        val normalizedRegion = region?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 }
        val current = mutableState.value
        val regionChanged = current.appliedFilter.region != normalizedRegion
        val applied = current.appliedFilter.withContext(current.mediaType, normalizedRegion, includeAdult, regionChanged).let {
            if (current.advancedDiscoverEnabled) it else it.basic()
        }
        val draft = current.draftFilter.withContext(current.mediaType, normalizedRegion, includeAdult, regionChanged).let {
            if (current.advancedDiscoverEnabled) it else it.basic()
        }
        if (applied == current.appliedFilter && draft == current.draftFilter) return

        mutableState.update {
            it.copy(
                appliedFilter = applied,
                draftFilter = draft,
                configuration = if (regionChanged) null else it.configuration,
            )
        }
        request.value = DiscoverRequest(current.mediaType, applied, current.advancedDiscoverEnabled)
        if (regionChanged) loadConfiguration(normalizedRegion)
    }

    fun selectMediaType(type: MediaType) {
        if (type == mutableState.value.mediaType) return
        val context = mutableState.value.appliedFilter
        val filter = DiscoverFilter(
            region = context.region,
            certificationCountry = context.region.takeIf {
                type == MediaType.MOVIE && mutableState.value.advancedDiscoverEnabled
            },
            includeAdult = context.includeAdult,
        )
        mutableState.update {
            it.copy(mediaType = type, genres = emptyList(), appliedFilter = filter, draftFilter = filter)
        }
        request.value = DiscoverRequest(type, filter, mutableState.value.advancedDiscoverEnabled)
        loadGenres()
    }

    fun setGrid(grid: Boolean) = mutableState.update { it.copy(isGrid = grid) }
    fun showFilters() = mutableState.update { it.copy(showFilters = true, draftFilter = it.appliedFilter) }
    fun dismissFilters() = mutableState.update { it.copy(showFilters = false) }
    fun updateDraft(transform: (DiscoverFilter) -> DiscoverFilter) = mutableState.update { it.copy(draftFilter = transform(it.draftFilter)) }
    fun resetDraft() = mutableState.update {
        val current = it.appliedFilter
        it.copy(
            draftFilter = DiscoverFilter(
                region = current.region,
                certificationCountry = if (it.mediaType == MediaType.MOVIE && it.advancedDiscoverEnabled) current.region else null,
                includeAdult = current.includeAdult,
            ),
        )
    }

    fun applyFilters() {
        val current = mutableState.value
        val normalized = current.draftFilter.normalized().let {
            if (current.advancedDiscoverEnabled) it else it.basic()
        }
        mutableState.update { it.copy(appliedFilter = normalized, draftFilter = normalized, showFilters = false) }
        request.value = DiscoverRequest(current.mediaType, normalized, current.advancedDiscoverEnabled)
    }

    private fun loadConfiguration(region: String?) = viewModelScope.launch {
        if (!mutableState.value.advancedDiscoverEnabled) return@launch
        val v2 = catalog as? CatalogV2Repository ?: return@launch
        runCatching { v2.discoverConfiguration(language, region) }
            .onSuccess { configuration ->
                if (mutableState.value.appliedFilter.region == region) {
                    mutableState.update { it.copy(configuration = configuration) }
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
                        val applied = if (enabled) state.appliedFilter else state.appliedFilter.basic()
                        state.copy(
                            advancedDiscoverEnabled = enabled,
                            appliedFilter = applied,
                            draftFilter = if (enabled) state.draftFilter else applied,
                            configuration = if (enabled) state.configuration else null,
                        )
                    }
                    val state = mutableState.value
                    request.value = DiscoverRequest(state.mediaType, state.appliedFilter, enabled)
                    if (enabled) loadConfiguration(state.appliedFilter.region)
                }
        }
    }

    private fun loadGenres() {
        genreJob?.cancel()
        val requestedType = mutableState.value.mediaType
        genreJob = viewModelScope.launch {
            mutableState.update { it.copy(genresLoading = true, genresError = null) }
            try {
                val genres = catalog.genres(requestedType, language)
                mutableState.update {
                    if (it.mediaType == requestedType) it.copy(genres = genres, genresLoading = false) else it
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                mutableState.update {
                    if (it.mediaType == requestedType) {
                        it.copy(genresLoading = false, genresError = failure.message)
                    } else it
                }
            }
        }
    }

    companion object {
        fun factory(catalog: CatalogRepository, language: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ExploreViewModel(catalog, language) as T
        }
    }
}

private fun DiscoverFilter.withContext(
    mediaType: MediaType,
    region: String?,
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

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
