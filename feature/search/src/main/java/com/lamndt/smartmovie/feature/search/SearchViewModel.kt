package com.lamndt.smartmovie.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lamndt.smartmovie.data.SearchPagingSource
import com.lamndt.smartmovie.data.EntitySearchPagingSource
import com.lamndt.smartmovie.model.CatalogEntity
import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.CatalogSearchMode
import com.lamndt.smartmovie.model.CatalogV2Repository
import com.lamndt.smartmovie.model.ExternalIdSource
import com.lamndt.smartmovie.model.SearchScope
import com.lamndt.smartmovie.model.SearchScopeV2
import com.lamndt.smartmovie.model.TitleSummary
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL,
    val entityScope: SearchScopeV2 = SearchScopeV2.ALL,
    val mode: CatalogSearchMode = CatalogSearchMode.CATALOG,
    val externalIdSource: ExternalIdSource = ExternalIdSource.IMDB,
    val externalResults: List<CatalogEntity> = emptyList(),
    val externalLoading: Boolean = false,
    val externalError: String? = null,
    val externalSubmitted: Boolean = false,
)
private data class SearchRequest(val query: String, val scope: SearchScope)
private data class EntitySearchRequest(val query: String, val scope: SearchScopeV2, val includeAdult: Boolean)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val catalog: CatalogRepository,
    private val language: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()
    private val request = MutableStateFlow(SearchRequest("", SearchScope.ALL))
    private val entityRequest = MutableStateFlow(EntitySearchRequest("", SearchScopeV2.ALL, false))
    private var externalJob: Job? = null

    val results: Flow<PagingData<TitleSummary>> = request
        .debounce(350)
        .distinctUntilChanged()
        .flatMapLatest { current ->
            if (current.query.isBlank()) flowOf(PagingData.empty())
            else Pager(PagingConfig(pageSize = 20, prefetchDistance = 6, enablePlaceholders = false)) {
                SearchPagingSource(catalog, current.query.trim(), current.scope, language)
            }.flow
        }
        .cachedIn(viewModelScope)

    val entityResults: Flow<PagingData<CatalogEntity>> = entityRequest
        .debounce(350)
        .distinctUntilChanged()
        .flatMapLatest { current ->
            if (current.query.isBlank()) flowOf(PagingData.empty())
            else Pager(PagingConfig(pageSize = 20, prefetchDistance = 6, enablePlaceholders = false)) {
                EntitySearchPagingSource(catalog, current.query.trim(), current.scope, language, null, current.includeAdult)
            }.flow
        }
        .cachedIn(viewModelScope)

    fun setQuery(query: String) {
        val current = mutableState.value
        if (current.mode == CatalogSearchMode.CATALOG) {
            mutableState.update { it.copy(query = query) }
            request.value = SearchRequest(query, current.scope)
            entityRequest.value = EntitySearchRequest(query, current.entityScope, entityRequest.value.includeAdult)
        } else {
            externalJob?.cancel()
            mutableState.update {
                it.copy(
                    query = query,
                    externalResults = emptyList(),
                    externalLoading = false,
                    externalError = null,
                    externalSubmitted = false,
                )
            }
        }
    }

    fun setMode(mode: CatalogSearchMode) {
        if (mutableState.value.mode == mode) return
        externalJob?.cancel()
        val current = mutableState.value
        mutableState.value = SearchUiState(
            scope = current.scope,
            entityScope = current.entityScope,
            mode = mode,
            externalIdSource = current.externalIdSource,
        )
        request.value = SearchRequest("", current.scope)
        entityRequest.value = EntitySearchRequest("", current.entityScope, entityRequest.value.includeAdult)
    }

    fun setExternalIdSource(source: ExternalIdSource) {
        if (mutableState.value.externalIdSource == source) return
        externalJob?.cancel()
        mutableState.update {
            it.copy(
                externalIdSource = source,
                externalResults = emptyList(),
                externalLoading = false,
                externalError = null,
                externalSubmitted = false,
            )
        }
    }

    fun findExternalId() {
        val externalId = mutableState.value.query.trim()
        val source = mutableState.value.externalIdSource
        if (externalId.isEmpty() || mutableState.value.mode != CatalogSearchMode.EXTERNAL_ID) return
        externalJob?.cancel()
        externalJob = viewModelScope.launch {
            mutableState.update { it.copy(externalLoading = true, externalError = null, externalSubmitted = true) }
            try {
                val result = (catalog as? CatalogV2Repository)?.findExternalId(externalId, source, language)
                    ?: error("External ID search requires the /v2 catalog")
                val current = mutableState.value
                val includeAdult = entityRequest.value.includeAdult
                if (current.mode == CatalogSearchMode.EXTERNAL_ID && current.query.trim() == externalId && current.externalIdSource == source) {
                    mutableState.update {
                        it.copy(
                            externalResults = result.results.filter { entity ->
                                includeAdult || (entity as? CatalogEntity.Title)?.value?.adult != true
                            },
                            externalLoading = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                val current = mutableState.value
                if (current.mode == CatalogSearchMode.EXTERNAL_ID && current.query.trim() == externalId && current.externalIdSource == source) {
                    mutableState.update { it.copy(externalLoading = false, externalError = failure.message.orEmpty()) }
                }
            }
        }
    }

    fun setEntityScope(scope: SearchScopeV2) {
        mutableState.update { it.copy(entityScope = scope) }
        entityRequest.value = EntitySearchRequest(mutableState.value.query, scope, entityRequest.value.includeAdult)
    }

    fun setIncludeAdult(includeAdult: Boolean) {
        if (entityRequest.value.includeAdult == includeAdult) return
        externalJob?.cancel()
        if (!includeAdult) {
            mutableState.update { state ->
                state.copy(
                    externalResults = state.externalResults.filter { entity ->
                        (entity as? CatalogEntity.Title)?.value?.adult != true
                    },
                    externalLoading = false,
                )
            }
        }
        entityRequest.value = EntitySearchRequest(
            mutableState.value.query,
            mutableState.value.entityScope,
            includeAdult,
        )
    }

    fun setScope(scope: SearchScope) {
        mutableState.update { it.copy(scope = scope) }
        request.value = SearchRequest(mutableState.value.query, scope)
    }

    companion object {
        fun factory(catalog: CatalogRepository, language: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(catalog, language) as T
        }
    }
}
