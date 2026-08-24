package com.lamndt.smartmovie.multiplatform

import com.lamndt.smartmovie.multiplatform.data.CatalogApi
import com.lamndt.smartmovie.multiplatform.data.CatalogApiV2
import com.lamndt.smartmovie.multiplatform.data.AccountApi
import com.lamndt.smartmovie.multiplatform.data.AccountMutationPayload
import com.lamndt.smartmovie.multiplatform.data.KtorAccountApi
import com.lamndt.smartmovie.multiplatform.data.KtorCatalogApi
import com.lamndt.smartmovie.multiplatform.data.LibraryCollection
import com.lamndt.smartmovie.multiplatform.data.LibraryRecord
import com.lamndt.smartmovie.multiplatform.data.PersistentLibrary
import com.lamndt.smartmovie.multiplatform.data.PersistentAccountMutationOutbox
import com.lamndt.smartmovie.multiplatform.data.PendingAccountMutation
import com.lamndt.smartmovie.multiplatform.data.ListItemMutation
import com.lamndt.smartmovie.multiplatform.data.applyPendingLists
import com.lamndt.smartmovie.multiplatform.data.createInstallationId
import com.lamndt.smartmovie.multiplatform.data.pinDigest
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
import com.lamndt.smartmovie.multiplatform.model.UserList
import com.lamndt.smartmovie.multiplatform.model.AccountProfile
import com.lamndt.smartmovie.multiplatform.model.AuthAttempt
import com.lamndt.smartmovie.multiplatform.model.CatalogEntity
import com.lamndt.smartmovie.multiplatform.model.CapabilitiesV2
import com.lamndt.smartmovie.multiplatform.model.CastMember
import com.lamndt.smartmovie.multiplatform.model.CollectionDetail
import com.lamndt.smartmovie.multiplatform.model.CatalogSearchMode
import com.lamndt.smartmovie.multiplatform.model.EpisodeDetail
import com.lamndt.smartmovie.multiplatform.model.ExternalIdSource
import com.lamndt.smartmovie.multiplatform.model.KeywordDetail
import com.lamndt.smartmovie.multiplatform.model.OrganizationDetail
import com.lamndt.smartmovie.multiplatform.model.PersonDetail
import com.lamndt.smartmovie.multiplatform.model.SearchScopeV2
import com.lamndt.smartmovie.multiplatform.model.SeasonDetail
import com.lamndt.smartmovie.multiplatform.model.TitleDetailV2
import com.lamndt.smartmovie.multiplatform.platform.KeyValueStore
import com.lamndt.smartmovie.multiplatform.platform.catalogBaseUrl
import com.lamndt.smartmovie.multiplatform.platform.createKeyValueStore
import com.lamndt.smartmovie.multiplatform.platform.authReturnUri
import com.lamndt.smartmovie.multiplatform.platform.authMode
import com.lamndt.smartmovie.multiplatform.platform.openExternalUrl
import com.lamndt.smartmovie.multiplatform.platform.systemTimeMillis
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
import kotlin.math.roundToInt

enum class AppTab { HOME, EXPLORE, SEARCH, LIBRARY, PROFILE }

sealed interface AccountState {
    data object Checking : AccountState
    data object SignedOut : AccountState
    data class Authorizing(val attempt: AuthAttempt) : AccountState
    data class SignedIn(val profile: AccountProfile) : AccountState
    data class Error(val message: String) : AccountState
}

sealed interface EntityDetail {
    data class Person(val value: PersonDetail) : EntityDetail
    data class Collection(val value: CollectionDetail) : EntityDetail
    data class Organization(val value: OrganizationDetail) : EntityDetail
    data class Keyword(val value: KeywordDetail) : EntityDetail
    data class Season(val value: SeasonDetail) : EntityDetail
    data class Episode(val value: EpisodeDetail) : EntityDetail
}

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
    val searchMode: CatalogSearchMode = CatalogSearchMode.CATALOG,
    val searchScope: SearchScope = SearchScope.ALL,
    val searchScopeV2: SearchScopeV2 = SearchScopeV2.ALL,
    val search: LoadState<List<TitleSummary>> = LoadState.Idle,
    val entitySearch: LoadState<List<CatalogEntity>> = LoadState.Idle,
    val externalIdSource: ExternalIdSource = ExternalIdSource.IMDB,
    val externalIdSearch: LoadState<List<CatalogEntity>> = LoadState.Idle,
    val searchPage: Int = 0,
    val searchTotalPages: Int = 1,
    val libraryCollection: LibraryCollection = LibraryCollection.FAVORITES,
    val library: List<LibraryRecord> = emptyList(),
    val detail: LoadState<TitleDetail> = LoadState.Idle,
    val deepDetail: TitleDetailV2? = null,
    val detailSelection: TitleSummary? = null,
    val detailRating: AccountRatingState = AccountRatingState(),
    val entitySelection: CatalogEntity? = null,
    val entityDetail: LoadState<EntityDetail> = LoadState.Idle,
    val episodeRating: AccountRatingState = AccountRatingState(),
    val capabilities: CapabilitiesV2? = null,
    val account: AccountState = AccountState.Checking,
    val accountLists: LoadState<List<UserList>> = LoadState.Idle,
    val regionOverride: String? = null,
    val adultConfigured: Boolean = false,
    val adultUnlocked: Boolean = false,
    val adultFailures: Int = 0,
    val adultLockUntil: Long = 0,
)

data class AccountRatingState(
    val value: Double? = null,
    val pending: Boolean = false,
    val error: String? = null,
)

class AppController(
    private val store: KeyValueStore = createKeyValueStore(),
    apiFactory: (String) -> CatalogApi = { KtorCatalogApi(baseUrl = catalogBaseUrl(), clientId = it) },
    accountFactory: ((String) -> AccountApi)? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val library = PersistentLibrary(store)
    private val accountOutbox = PersistentAccountMutationOutbox(store)
    private val clientId = store.getString(INSTALLATION_ID_KEY)
        ?.takeIf { it.length == 36 }
        ?: createInstallationId().also { store.putString(INSTALLATION_ID_KEY, it) }
    private val api = apiFactory(clientId)
    private val apiV2 = api as? CatalogApiV2
    private val accountApi = accountFactory?.invoke(clientId)
        ?: if (api is KtorCatalogApi) KtorAccountApi(catalogBaseUrl(), clientId) else null
    private val mutableState = MutableStateFlow(
        SmartMovieState(
            locale = AppLocale.fromTag(store.getString(LOCALE_KEY)),
            regionOverride = store.getString(REGION_KEY)?.takeIf(String::isNotBlank),
            adultConfigured = store.getString(ADULT_DIGEST_KEY) != null,
            adultFailures = store.getString(ADULT_FAILURES_KEY)?.toIntOrNull() ?: 0,
            adultLockUntil = store.getString(ADULT_LOCK_UNTIL_KEY)?.toLongOrNull() ?: 0,
        ),
    )
    val state: StateFlow<SmartMovieState> = mutableState.asStateFlow()

    private var homeJob: Job? = null
    private var exploreJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var titleRatingJob: Job? = null
    private var episodeRatingJob: Job? = null

    init {
        scope.launch { library.records.collect { records -> mutableState.update { it.copy(library = records) } } }
        scope.launch {
            val configuration = runCatching { api.imageConfiguration() }
                .propagateCancellation()
                .getOrDefault(ImageConfiguration.Fallback)
            mutableState.update { it.copy(imageConfiguration = configuration) }
        }
        scope.launch {
            apiV2?.let { v2 -> runCatching { v2.capabilities() }.onSuccess { value -> mutableState.update { it.copy(capabilities = value) } } }
            refreshAccount()
        }
        reloadHome()
        reloadExplore()
    }

    fun selectTab(tab: AppTab) {
        mutableState.update {
            it.copy(
                selectedTab = tab,
                detailSelection = null,
                detail = LoadState.Idle,
                deepDetail = null,
                entitySelection = null,
                entityDetail = LoadState.Idle,
            )
        }
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
        if (state.value.searchQuery.isNotBlank()) {
            if (state.value.searchMode == CatalogSearchMode.CATALOG) scheduleSearch(immediate = true)
            else if (state.value.externalIdSearch !is LoadState.Idle) findExternalId()
        }
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
        if (state.value.searchMode == CatalogSearchMode.CATALOG) {
            mutableState.update { it.copy(searchQuery = query) }
            scheduleSearch(immediate = false)
        } else {
            searchJob?.cancel()
            mutableState.update { it.copy(searchQuery = query, externalIdSearch = LoadState.Idle) }
        }
    }

    fun changeSearchMode(mode: CatalogSearchMode) {
        if (mode == state.value.searchMode) return
        searchJob?.cancel()
        mutableState.update {
            it.copy(
                searchMode = mode,
                searchQuery = "",
                search = LoadState.Idle,
                entitySearch = LoadState.Idle,
                externalIdSearch = LoadState.Idle,
                searchPage = 0,
                searchTotalPages = 1,
            )
        }
    }

    fun changeExternalIdSource(source: ExternalIdSource) {
        if (source == state.value.externalIdSource) return
        searchJob?.cancel()
        mutableState.update { it.copy(externalIdSource = source, externalIdSearch = LoadState.Idle) }
    }

    fun findExternalId() {
        val snapshot = state.value
        val externalId = snapshot.searchQuery.trim()
        if (snapshot.searchMode != CatalogSearchMode.EXTERNAL_ID || externalId.isEmpty()) return
        val catalog = apiV2
        if (catalog == null) {
            mutableState.update {
                it.copy(externalIdSearch = LoadState.Error("External ID search requires the /v2 catalog"))
            }
            return
        }
        searchJob?.cancel()
        searchJob = scope.launch {
            mutableState.update { it.copy(externalIdSearch = LoadState.Loading) }
            runCatching { catalog.findExternalId(externalId, snapshot.externalIdSource, snapshot.locale.backendTag) }
                .propagateCancellation()
                .onSuccess { result ->
                    val current = state.value
                    if (current.searchMode == CatalogSearchMode.EXTERNAL_ID &&
                        current.searchQuery.trim() == externalId &&
                        current.externalIdSource == snapshot.externalIdSource
                    ) {
                        mutableState.update {
                            it.copy(externalIdSearch = LoadState.Content(result.results.distinctBy(CatalogEntity::stableKey)))
                        }
                    }
                }
                .onFailure { failure ->
                    val current = state.value
                    if (current.searchMode == CatalogSearchMode.EXTERNAL_ID &&
                        current.searchQuery.trim() == externalId &&
                        current.externalIdSource == snapshot.externalIdSource
                    ) {
                        mutableState.update { it.copy(externalIdSearch = LoadState.Error(failure.message.orEmpty())) }
                    }
                }
        }
    }

    fun changeSearchScope(scope: SearchScope) {
        mutableState.update { it.copy(searchScope = scope) }
        scheduleSearch(immediate = true)
    }

    fun changeSearchScope(scope: SearchScopeV2) {
        mutableState.update { it.copy(searchScopeV2 = scope) }
        scheduleSearch(immediate = true)
    }

    fun retrySearch() {
        if (state.value.searchMode == CatalogSearchMode.EXTERNAL_ID) findExternalId()
        else scheduleSearch(immediate = true)
    }

    fun loadMoreSearch() {
        val snapshot = state.value
        if (snapshot.searchMode != CatalogSearchMode.CATALOG) return
        val content = (snapshot.search as? LoadState.Content)?.value.orEmpty()
        val entityContent = (snapshot.entitySearch as? LoadState.Content)?.value.orEmpty()
        if (snapshot.searchPage >= snapshot.searchTotalPages || searchJob?.isActive == true) return
        searchJob = scope.launch {
            if (apiV2 != null) {
                runCatching {
                    apiV2.searchEntities(
                        snapshot.searchQuery,
                        snapshot.searchScopeV2,
                        snapshot.searchPage + 1,
                        snapshot.locale.backendTag,
                        snapshot.regionOverride,
                        includeAdult(snapshot),
                    )
                }.propagateCancellation().onSuccess { page -> mutableState.update {
                    val titles = page.results.mapNotNull { entity -> (entity as? CatalogEntity.Title)?.value }
                    it.copy(
                        search = LoadState.Content((content + titles).distinctBy(TitleSummary::libraryKey)),
                        entitySearch = LoadState.Content((entityContent + page.results).distinctBy(CatalogEntity::stableKey)),
                        searchPage = page.page,
                        searchTotalPages = page.totalPages,
                    )
                } }
            } else {
                runCatching {
                    api.search(
                        snapshot.searchQuery,
                        snapshot.searchScope,
                        snapshot.searchPage + 1,
                        snapshot.locale.backendTag,
                    )
                }.propagateCancellation().onSuccess { page -> mutableState.update {
                    it.copy(
                        search = LoadState.Content((content + page.results).distinctBy(TitleSummary::libraryKey)),
                        entitySearch = LoadState.Content((entityContent + page.results.map(CatalogEntity::Title)).distinctBy(CatalogEntity::stableKey)),
                        searchPage = page.page,
                        searchTotalPages = page.totalPages,
                    )
                } }
            }
        }
    }

    fun openDetail(title: TitleSummary) {
        detailJob?.cancel()
        mutableState.update { it.copy(detailSelection = title, detail = LoadState.Loading, detailRating = AccountRatingState()) }
        refreshTitleRating(title)
        detailJob = scope.launch {
            val snapshot = state.value
            runCatching {
                apiV2?.deepDetail(
                    title.mediaType,
                    title.id,
                    snapshot.locale.backendTag,
                    snapshot.regionOverride,
                    includeAdult(snapshot),
                )
            }
                .propagateCancellation()
                .onSuccess { deep ->
                    if (deep != null) mutableState.update { it.copy(detail = LoadState.Content(deep.toLegacy()), deepDetail = deep) }
                    else runCatching { api.detail(title.mediaType, title.id, snapshot.locale.backendTag) }
                        .onSuccess { detail -> mutableState.update { it.copy(detail = LoadState.Content(detail)) } }
                        .onFailure { failure -> mutableState.update { it.copy(detail = LoadState.Error(failure.message.orEmpty())) } }
                }
                .onFailure { failure -> mutableState.update { it.copy(detail = LoadState.Error(failure.message.orEmpty())) } }
        }
    }

    fun openEntity(entity: CatalogEntity) {
        if (entity is CatalogEntity.Title) {
            openDetail(entity.value)
            return
        }
        val catalog = apiV2 ?: return
        detailJob?.cancel()
        mutableState.update { it.copy(entitySelection = entity, entityDetail = LoadState.Loading, episodeRating = AccountRatingState()) }
        if (entity is CatalogEntity.Episode) refreshEpisodeRating(entity.value.seriesId, entity.value.seasonNumber, entity.value.episodeNumber)
        detailJob = scope.launch {
            val language = state.value.locale.backendTag
            runCatching {
                when (entity) {
                    is CatalogEntity.Person -> EntityDetail.Person(catalog.person(entity.value.id, language))
                    is CatalogEntity.Collection -> EntityDetail.Collection(catalog.collection(entity.value.id, language))
                    is CatalogEntity.Organization -> EntityDetail.Organization(catalog.organization(entity.value.entityKind, entity.value.id, language, 1))
                    is CatalogEntity.Keyword -> EntityDetail.Keyword(catalog.keyword(entity.value.id, language, 1))
                    is CatalogEntity.Season -> {
                        val seriesId = requireNotNull(entity.value.seriesId) { "Season is missing its series context." }
                        EntityDetail.Season(catalog.season(seriesId, entity.value.seasonNumber, language))
                    }
                    is CatalogEntity.Episode -> EntityDetail.Episode(
                        catalog.episode(entity.value.seriesId, entity.value.seasonNumber, entity.value.episodeNumber, language),
                    )
                    is CatalogEntity.Title -> error("Title detail is handled separately")
                }
            }.propagateCancellation()
                .onSuccess { detail -> mutableState.update { it.copy(entityDetail = LoadState.Content(detail)) } }
                .onFailure { error -> mutableState.update { it.copy(entityDetail = LoadState.Error(error.message.orEmpty())) } }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        titleRatingJob?.cancel()
        episodeRatingJob?.cancel()
        mutableState.update {
            it.copy(
                detailSelection = null,
                detail = LoadState.Idle,
                deepDetail = null,
                detailRating = AccountRatingState(),
                entitySelection = null,
                entityDetail = LoadState.Idle,
                episodeRating = AccountRatingState(),
            )
        }
    }

    fun retryDetail() = state.value.detailSelection?.let(::openDetail)

    fun retryEntityDetail() = state.value.entitySelection?.let(::openEntity)

    fun toggleLibrary(title: TitleSummary, collection: LibraryCollection) {
        library.toggle(title, collection)
        scope.launch { flushLibraryOutbox() }
    }

    fun rateTitle(value: Double?) {
        val profile = (state.value.account as? AccountState.SignedIn)?.profile ?: return
        val title = state.value.detailSelection ?: return
        val rating = normalizeRating(value)
        accountOutbox.enqueue(profile.id, AccountMutationPayload.TitleRating(title.mediaType, title.id, rating))
        mutableState.update { it.copy(detailRating = AccountRatingState(rating, pending = true)) }
        scope.launch { flushAccountOutbox(profile.id) }
    }

    fun rateEpisode(value: Double?) {
        val profile = (state.value.account as? AccountState.SignedIn)?.profile ?: return
        val episode = (state.value.entitySelection as? CatalogEntity.Episode)?.value ?: return
        val rating = normalizeRating(value)
        accountOutbox.enqueue(
            profile.id,
            AccountMutationPayload.EpisodeRating(
                episode.seriesId,
                episode.seasonNumber,
                episode.episodeNumber,
                rating,
            ),
        )
        mutableState.update { it.copy(episodeRating = AccountRatingState(rating, pending = true)) }
        scope.launch { flushAccountOutbox(profile.id) }
    }

    fun changeLibraryCollection(collection: LibraryCollection) {
        mutableState.update { it.copy(libraryCollection = collection) }
    }

    fun setRegion(region: String?) {
        val normalized = region?.trim()?.uppercase()?.takeIf { it.matches(Regex("[A-Z]{2}")) }
        store.putString(REGION_KEY, normalized.orEmpty())
        mutableState.update { it.copy(regionOverride = normalized) }
    }

    fun configureAdultPin(pin: String): Boolean {
        if (!pin.matches(Regex("[0-9]{6}"))) return false
        val salt = createInstallationId()
        store.putString(ADULT_SALT_KEY, salt)
        store.putString(ADULT_DIGEST_KEY, pinDigest(salt, pin))
        resetAdultFailures()
        mutableState.update { it.copy(adultConfigured = true, adultUnlocked = true) }
        return true
    }

    fun unlockAdult(pin: String): Boolean {
        val snapshot = state.value
        if (snapshot.adultLockUntil > systemTimeMillis()) return false
        val salt = store.getString(ADULT_SALT_KEY) ?: return false
        val digest = store.getString(ADULT_DIGEST_KEY) ?: return false
        if (pinDigest(salt, pin) == digest) {
            resetAdultFailures()
            mutableState.update { it.copy(adultUnlocked = true) }
            return true
        }
        val failures = snapshot.adultFailures + 1
        val lockUntil = if (failures >= MAX_ADULT_FAILURES) systemTimeMillis() + ADULT_LOCK_MILLIS else 0
        store.putString(ADULT_FAILURES_KEY, failures.toString())
        store.putString(ADULT_LOCK_UNTIL_KEY, lockUntil.toString())
        mutableState.update { it.copy(adultFailures = failures, adultLockUntil = lockUntil, adultUnlocked = false) }
        return false
    }

    fun lockAdult() {
        mutableState.update { it.copy(adultUnlocked = false) }
    }

    fun beginSignIn(mode: String = authMode()) {
        val account = accountApi ?: return
        scope.launch {
            mutableState.update { it.copy(account = AccountState.Checking) }
            runCatching { account.createAuthAttempt(authReturnUri(), mode) }
                .propagateCancellation()
                .onSuccess { attempt ->
                    mutableState.update { it.copy(account = AccountState.Authorizing(attempt)) }
                    openExternalUrl(attempt.authorizationUrl)
                    pollAndCompleteAuth(attempt)
                }
                .onFailure { error -> mutableState.update { it.copy(account = AccountState.Error(error.message.orEmpty())) } }
        }
    }

    fun cancelSignIn() {
        mutableState.update { it.copy(account = AccountState.SignedOut) }
    }

    fun signOut(keepAsLocal: Boolean) {
        val account = accountApi ?: return
        val accountId = (state.value.account as? AccountState.SignedIn)?.profile?.id
        scope.launch {
            runCatching { account.logout() }
            library.deactivateAccount(removeAccountData = !keepAsLocal)
            if (!keepAsLocal && accountId != null) accountOutbox.clear(accountId)
            mutableState.update {
                it.copy(
                    account = AccountState.SignedOut,
                    accountLists = LoadState.Idle,
                    detailRating = AccountRatingState(),
                    episodeRating = AccountRatingState(),
                )
            }
        }
    }

    fun refreshLists() {
        val profile = (state.value.account as? AccountState.SignedIn)?.profile ?: return
        scope.launch { refreshLists(profile.id) }
    }

    fun createList(name: String, description: String) {
        val profile = (state.value.account as? AccountState.SignedIn)?.profile ?: return
        val normalized = name.trim().takeIf(String::isNotEmpty) ?: return
        accountOutbox.enqueue(
            profile.id,
            AccountMutationPayload.CreateList(
                normalized,
                description.trim(),
                public = false,
                region = state.value.regionOverride ?: "US",
                language = state.value.locale.tag.substringBefore('-'),
            ),
        )
        publishOptimisticLists(profile.id)
        scope.launch { flushAccountOutbox(profile.id) }
    }

    fun deleteList(id: Int) {
        val profile = (state.value.account as? AccountState.SignedIn)?.profile ?: return
        if (id < 0) {
            accountOutbox.pending(profile.id).firstOrNull { it.localListId == id }?.let { accountOutbox.cancel(it.id) }
        } else {
            accountOutbox.enqueue(profile.id, AccountMutationPayload.DeleteList(id))
        }
        publishOptimisticLists(profile.id)
        scope.launch { flushAccountOutbox(profile.id) }
    }

    fun updateList(id: Int, name: String, description: String, public: Boolean) {
        val profile = (state.value.account as? AccountState.SignedIn)?.profile ?: return
        val normalized = name.trim().takeIf(String::isNotEmpty) ?: return
        accountOutbox.enqueue(
            profile.id,
            AccountMutationPayload.UpdateList(id, normalized, description.trim(), public),
        )
        publishOptimisticLists(profile.id)
        scope.launch { flushAccountOutbox(profile.id) }
    }

    fun mutateListItems(id: Int, items: List<ListItemMutation>, remove: Boolean) {
        val profile = (state.value.account as? AccountState.SignedIn)?.profile ?: return
        if (items.isEmpty()) return
        accountOutbox.enqueue(profile.id, AccountMutationPayload.MutateListItems(id, items, remove))
        scope.launch { flushAccountOutbox(profile.id) }
    }

    fun close() = scope.cancel()

    private suspend fun refreshLists(accountId: Int) {
        val account = accountApi ?: return
        val pending = accountOutbox.pending(accountId)
        val current = (state.value.accountLists as? LoadState.Content)?.value.orEmpty().filter { it.id > 0 }
        val hasPendingListMutation = pending.any { it.payload.isListMutation() }
        if (current.isEmpty() && !hasPendingListMutation) {
            mutableState.update { it.copy(accountLists = LoadState.Loading) }
        }
        runCatching { account.lists(1) }
            .propagateCancellation()
            .onSuccess { page ->
                mutableState.update { it.copy(accountLists = LoadState.Content(applyPendingLists(page.results, pending))) }
            }
            .onFailure { error ->
                mutableState.update {
                    it.copy(
                        accountLists = if (hasPendingListMutation) {
                            LoadState.Content(applyPendingLists(current, pending))
                        } else {
                            LoadState.Error(error.message.orEmpty())
                        },
                    )
                }
            }
    }

    private fun publishOptimisticLists(accountId: Int) {
        val remote = (state.value.accountLists as? LoadState.Content)?.value.orEmpty().filter { it.id > 0 }
        val pending = accountOutbox.pending(accountId)
        mutableState.update { it.copy(accountLists = LoadState.Content(applyPendingLists(remote, pending))) }
    }

    private fun refreshTitleRating(title: TitleSummary) {
        titleRatingJob?.cancel()
        val account = accountApi
        val profile = (state.value.account as? AccountState.SignedIn)?.profile
        if (account == null || profile == null) {
            mutableState.update { it.copy(detailRating = AccountRatingState()) }
            return
        }
        titleRatingJob = scope.launch {
            val local = accountOutbox.pending(profile.id).lastOrNull { mutation ->
                val payload = mutation.payload as? AccountMutationPayload.TitleRating
                payload?.mediaType == title.mediaType && payload.mediaId == title.id
            }
            if (local != null) {
                val payload = local.payload as AccountMutationPayload.TitleRating
                mutableState.update {
                    it.copy(detailRating = AccountRatingState(payload.value, pending = true, error = local.lastError))
                }
                return@launch
            }
            runCatching { account.accountState(title.mediaType, title.id).ratingValue }
                .propagateCancellation()
                .onSuccess { value -> mutableState.update { it.copy(detailRating = AccountRatingState(value)) } }
                .onFailure { error -> mutableState.update {
                    it.copy(detailRating = it.detailRating.copy(pending = false, error = error.message))
                } }
        }
    }

    private fun refreshEpisodeRating(seriesId: Int, season: Int, episode: Int) {
        episodeRatingJob?.cancel()
        val account = accountApi
        val profile = (state.value.account as? AccountState.SignedIn)?.profile
        if (account == null || profile == null) {
            mutableState.update { it.copy(episodeRating = AccountRatingState()) }
            return
        }
        episodeRatingJob = scope.launch {
            val local = accountOutbox.pending(profile.id).lastOrNull { mutation ->
                val payload = mutation.payload as? AccountMutationPayload.EpisodeRating
                payload?.seriesId == seriesId && payload.seasonNumber == season && payload.episodeNumber == episode
            }
            if (local != null) {
                val payload = local.payload as AccountMutationPayload.EpisodeRating
                mutableState.update {
                    it.copy(episodeRating = AccountRatingState(payload.value, pending = true, error = local.lastError))
                }
                return@launch
            }
            runCatching { account.episodeAccountState(seriesId, season, episode).ratingValue }
                .propagateCancellation()
                .onSuccess { value -> mutableState.update { it.copy(episodeRating = AccountRatingState(value)) } }
                .onFailure { error -> mutableState.update {
                    it.copy(episodeRating = it.episodeRating.copy(pending = false, error = error.message))
                } }
        }
    }

    private suspend fun flushAccountOutbox(accountId: Int) {
        val account = accountApi ?: return
        if ((state.value.account as? AccountState.SignedIn)?.profile?.id != accountId) return
        val before = accountOutbox.pending(accountId)
        accountOutbox.flush(accountId) { mutation -> dispatchAccountMutation(account, mutation) }
        if (before.any { it.payload.isListMutation() }) refreshLists(accountId)
        state.value.detailSelection?.let(::refreshTitleRating)
        (state.value.entitySelection as? CatalogEntity.Episode)?.value?.let { episode ->
            refreshEpisodeRating(episode.seriesId, episode.seasonNumber, episode.episodeNumber)
        }
    }

    private suspend fun dispatchAccountMutation(
        account: AccountApi,
        mutation: PendingAccountMutation,
    ) = when (val payload = mutation.payload) {
        is AccountMutationPayload.TitleRating -> account.setRating(
            payload.mediaType,
            payload.mediaId,
            payload.value,
            mutation.id,
        )
        is AccountMutationPayload.EpisodeRating -> account.setEpisodeRating(
            payload.seriesId,
            payload.seasonNumber,
            payload.episodeNumber,
            payload.value,
            mutation.id,
        )
        is AccountMutationPayload.CreateList -> account.createList(
            payload.name,
            payload.description,
            payload.public,
            payload.region,
            payload.language,
            mutation.id,
        )
        is AccountMutationPayload.UpdateList -> account.updateList(
            payload.listId,
            payload.name,
            payload.description,
            payload.public,
            mutation.id,
        )
        is AccountMutationPayload.DeleteList -> account.deleteList(payload.listId, mutation.id)
        is AccountMutationPayload.MutateListItems -> account.mutateListItems(
            payload.listId,
            payload.items,
            payload.remove,
            mutation.id,
        )
    }

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
        if (state.value.searchMode != CatalogSearchMode.CATALOG) return
        val query = state.value.searchQuery.trim()
        if (query.isEmpty()) {
            mutableState.update { it.copy(search = LoadState.Idle, entitySearch = LoadState.Idle, searchPage = 0) }
            return
        }
        searchJob = scope.launch {
            if (!immediate) delay(350)
            mutableState.update { it.copy(search = LoadState.Loading, entitySearch = LoadState.Loading, searchPage = 0) }
            val snapshot = state.value
            runCatching {
                apiV2?.searchEntities(
                    query,
                    snapshot.searchScopeV2,
                    1,
                    snapshot.locale.backendTag,
                    snapshot.regionOverride,
                    includeAdult(snapshot),
                )
            }
                .propagateCancellation()
                .onSuccess { entityPage ->
                    if (entityPage != null) mutableState.update {
                        it.copy(
                            search = LoadState.Content(entityPage.results.mapNotNull { value -> (value as? CatalogEntity.Title)?.value }.distinctBy(TitleSummary::libraryKey)),
                            entitySearch = LoadState.Content(entityPage.results.distinctBy(CatalogEntity::stableKey)),
                            searchPage = entityPage.page,
                            searchTotalPages = entityPage.totalPages,
                        )
                    } else runCatching { api.search(query, snapshot.searchScope, 1, snapshot.locale.backendTag) }
                        .onSuccess { page -> mutableState.update {
                            it.copy(
                                search = LoadState.Content(page.results.distinctBy(TitleSummary::libraryKey)),
                                entitySearch = LoadState.Content(page.results.map(CatalogEntity::Title)),
                                searchPage = page.page,
                                searchTotalPages = page.totalPages,
                            )
                        } }
                        .onFailure { failure -> mutableState.update { it.copy(search = LoadState.Error(failure.message.orEmpty()), entitySearch = LoadState.Error(failure.message.orEmpty())) } }
                }
                .onFailure { failure -> mutableState.update {
                    it.copy(search = LoadState.Error(failure.message.orEmpty()), entitySearch = LoadState.Error(failure.message.orEmpty()))
                } }
        }
    }

    private suspend fun refreshAccount() {
        val account = accountApi ?: run {
            mutableState.update { it.copy(account = AccountState.SignedOut) }
            return
        }
        runCatching { account.profile() }
            .propagateCancellation()
            .onSuccess { profile ->
                library.activateAccount(profile.id)
                mutableState.update { it.copy(account = AccountState.SignedIn(profile)) }
                syncAccountLibrary(profile.id)
                flushAccountOutbox(profile.id)
                refreshLists(profile.id)
            }
            .onFailure { mutableState.update { it.copy(account = AccountState.SignedOut) } }
    }

    private suspend fun pollAndCompleteAuth(attempt: AuthAttempt) {
        val account = accountApi ?: return
        val interval = (attempt.pollingInterval ?: DEFAULT_AUTH_POLL_SECONDS).coerceAtLeast(2)
        repeat(MAX_AUTH_POLLS) {
            delay(interval * 1_000L)
            val status = runCatching { account.authAttempt(attempt.attemptId, attempt.deviceCode) }.getOrNull()
            when (status) {
                "approved" -> {
                    val session = runCatching { account.completeAuth(attempt.attemptId, attempt.deviceCode) }
                        .getOrElse { error ->
                            mutableState.update { it.copy(account = AccountState.Error(error.message.orEmpty())) }
                            return
                        }
                    library.activateAccount(session.profile.id)
                    mutableState.update { it.copy(account = AccountState.SignedIn(session.profile)) }
                    syncAccountLibrary(session.profile.id)
                    flushAccountOutbox(session.profile.id)
                    refreshLists(session.profile.id)
                    return
                }
                "expired", "denied" -> {
                    mutableState.update { it.copy(account = AccountState.Error("TMDb authorization $status.")) }
                    return
                }
            }
        }
        mutableState.update { it.copy(account = AccountState.Error("TMDb authorization timed out.")) }
    }

    private suspend fun syncAccountLibrary(accountId: Int) {
        val account = accountApi ?: return
        LibraryCollection.entries.forEach { collection ->
            MediaType.entries.forEach { mediaType ->
                var pageNumber = 1
                do {
                    val page = runCatching {
                        account.library(collection, mediaType, pageNumber, state.value.locale.backendTag)
                    }.getOrNull() ?: break
                    library.mergeRemote(page.results, collection, mediaType, accountId)
                    pageNumber += 1
                } while (pageNumber <= page.totalPages.coerceAtMost(MAX_LIBRARY_SYNC_PAGES))
            }
        }
        flushLibraryOutbox()
    }

    private suspend fun flushLibraryOutbox() {
        val account = accountApi ?: return
        if (state.value.account !is AccountState.SignedIn) return
        library.pendingMutations().forEach { mutation ->
            val collection = if (mutation.collection == LibraryCollection.FAVORITES.wireValue) {
                LibraryCollection.FAVORITES
            } else LibraryCollection.WATCHLIST
            val mediaType = if (mutation.mediaType == MediaType.MOVIE.wireValue) MediaType.MOVIE else MediaType.TV
            runCatching {
                account.setLibrary(collection, mediaType, mutation.mediaId, mutation.enabled, mutation.id)
            }.onSuccess { library.confirmMutation(mutation.id) }
                .onFailure { library.failMutation(mutation.id, it.message.orEmpty()) }
        }
    }

    private fun includeAdult(snapshot: SmartMovieState): Boolean = snapshot.adultConfigured &&
        snapshot.adultUnlocked && snapshot.adultLockUntil <= systemTimeMillis()

    private fun normalizeRating(value: Double?): Double? = value?.let {
        ((it * 2).roundToInt() / 2.0).coerceIn(0.5, 10.0)
    }

    private fun resetAdultFailures() {
        store.putString(ADULT_FAILURES_KEY, "0")
        store.putString(ADULT_LOCK_UNTIL_KEY, "0")
        mutableState.update { it.copy(adultFailures = 0, adultLockUntil = 0) }
    }

    private companion object {
        const val INSTALLATION_ID_KEY = "smartmovie_installation_id"
        const val LOCALE_KEY = "smartmovie_locale"
        const val REGION_KEY = "smartmovie_region"
        const val ADULT_SALT_KEY = "smartmovie_adult_salt"
        const val ADULT_DIGEST_KEY = "smartmovie_adult_digest"
        const val ADULT_FAILURES_KEY = "smartmovie_adult_failures"
        const val ADULT_LOCK_UNTIL_KEY = "smartmovie_adult_lock_until"
        const val MAX_ADULT_FAILURES = 5
        const val ADULT_LOCK_MILLIS = 5 * 60 * 1_000L
        const val DEFAULT_AUTH_POLL_SECONDS = 5
        const val MAX_AUTH_POLLS = 120
        const val MAX_LIBRARY_SYNC_PAGES = 50
    }
}

private fun AccountMutationPayload.isListMutation(): Boolean = when (this) {
    is AccountMutationPayload.CreateList,
    is AccountMutationPayload.DeleteList,
    is AccountMutationPayload.MutateListItems,
    is AccountMutationPayload.UpdateList,
    -> true
    is AccountMutationPayload.EpisodeRating,
    is AccountMutationPayload.TitleRating,
    -> false
}

private fun TitleDetailV2.toLegacy(): TitleDetail = TitleDetail(
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
        credit.id?.let { personId -> CastMember(personId, credit.title.orEmpty(), credit.character, credit.profilePath) }
    },
    videos = videos,
    similar = similar,
)

private fun <T> Result<T>.propagateCancellation(): Result<T> = also { result ->
    if (result.exceptionOrNull() is CancellationException) throw result.exceptionOrNull()!!
}
