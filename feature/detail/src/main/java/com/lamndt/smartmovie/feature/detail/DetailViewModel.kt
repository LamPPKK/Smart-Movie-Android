package com.lamndt.smartmovie.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.CatalogV2Repository
import com.lamndt.smartmovie.model.LibraryCollection
import com.lamndt.smartmovie.model.LibraryMembership
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.model.Loadable
import com.lamndt.smartmovie.model.TitleDetail
import com.lamndt.smartmovie.model.TitleDetailV2
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
    val deepDetail: TitleDetailV2? = null,
    val membership: LibraryMembership = LibraryMembership(),
)

class DetailViewModel(
    private val initialTitle: TitleSummary,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val language: String,
    private val region: String? = null,
    private val includeAdult: Boolean = false,
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
            if (catalog is CatalogV2Repository) {
                val deep = catalog.deepDetail(initialTitle.mediaType, initialTitle.id, language, region, includeAdult)
                val result = deep.toLegacy()
                mutableState.update { it.copy(title = deep.summary, detail = Loadable.Loaded(result), deepDetail = deep) }
            } else {
                val result = catalog.detail(initialTitle.mediaType, initialTitle.id, language)
                mutableState.update { it.copy(title = result.summary, detail = Loadable.Loaded(result)) }
            }
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
        fun factory(
            title: TitleSummary,
            catalog: CatalogRepository,
            library: LibraryRepository,
            language: String,
            region: String? = null,
            includeAdult: Boolean = false,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DetailViewModel(title, catalog, library, language, region, includeAdult) as T
            }
    }
}

private fun TitleDetailV2.toLegacy() = TitleDetail(
    id = id,
    mediaType = mediaType,
    title = title,
    originalTitle = originalTitle,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    genres = genres,
    runtimeMinutes = runtimeMinutes,
    numberOfSeasons = numberOfSeasons,
    status = status,
    cast = cast.mapNotNull { credit ->
        val personId = credit.id ?: return@mapNotNull null
        com.lamndt.smartmovie.model.CastMember(personId, credit.title.orEmpty(), credit.character, credit.profilePath)
    },
    videos = videos,
    similar = similar,
)
