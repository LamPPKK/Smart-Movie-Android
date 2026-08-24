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

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL,
    val entityScope: SearchScopeV2 = SearchScopeV2.ALL,
)
private data class SearchRequest(val query: String, val scope: SearchScope)
private data class EntitySearchRequest(val query: String, val scope: SearchScopeV2)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val catalog: CatalogRepository,
    private val language: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()
    private val request = MutableStateFlow(SearchRequest("", SearchScope.ALL))
    private val entityRequest = MutableStateFlow(EntitySearchRequest("", SearchScopeV2.ALL))

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
                EntitySearchPagingSource(catalog, current.query.trim(), current.scope, language, null, false)
            }.flow
        }
        .cachedIn(viewModelScope)

    fun setQuery(query: String) {
        mutableState.update { it.copy(query = query) }
        request.value = SearchRequest(query, mutableState.value.scope)
        entityRequest.value = EntitySearchRequest(query, mutableState.value.entityScope)
    }

    fun setEntityScope(scope: SearchScopeV2) {
        mutableState.update { it.copy(entityScope = scope) }
        entityRequest.value = EntitySearchRequest(mutableState.value.query, scope)
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
