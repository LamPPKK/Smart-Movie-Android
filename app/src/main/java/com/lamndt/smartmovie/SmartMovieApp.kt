package com.lamndt.smartmovie

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.lamndt.smartmovie.designsystem.CinemaBackground
import com.lamndt.smartmovie.designsystem.CinemaColors
import com.lamndt.smartmovie.designsystem.R
import com.lamndt.smartmovie.designsystem.isWindowWidthAtLeast
import com.lamndt.smartmovie.feature.about.AboutScreen
import com.lamndt.smartmovie.feature.detail.DetailRoute
import com.lamndt.smartmovie.feature.detail.DetailRemoteState
import com.lamndt.smartmovie.feature.explore.ExploreRoute
import com.lamndt.smartmovie.feature.home.HomeRoute
import com.lamndt.smartmovie.feature.library.LibraryRoute
import com.lamndt.smartmovie.feature.search.SearchRoute
import com.lamndt.smartmovie.data.ImageUrlFactory
import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.CatalogEntity
import com.lamndt.smartmovie.model.Credit
import com.lamndt.smartmovie.model.CatalogLocale
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.TitleSummary
import kotlinx.serialization.Serializable

private enum class AppTab(val label: Int, val icon: ImageVector) {
    HOME(R.string.home, Icons.Default.Home),
    EXPLORE(R.string.explore, Icons.Default.Explore),
    SEARCH(R.string.search, Icons.Default.Search),
    LIBRARY(R.string.library, Icons.Default.CollectionsBookmark),
    PROFILE(R.string.profile, Icons.Default.Person),
}

@Serializable
private data object RootKey : NavKey

@Serializable
internal data class DetailKey(
    val id: Int,
    val type: String,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val rating: Double,
    val adult: Boolean = true,
) : NavKey {
    fun summary() = TitleSummary(
        id = id,
        mediaType = if (type == "tv") MediaType.TV else MediaType.MOVIE,
        title = title,
        originalTitle = originalTitle,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate,
        voteAverage = rating,
        adult = adult,
    )
}

@Serializable
internal data class EntityKey(
    val kind: String,
    val id: Int,
    val name: String,
    val seriesId: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val series: TitleSummary? = null,
) : NavKey

@Serializable
private data class CreditKey(val id: String, val label: String) : NavKey

@Composable
fun SmartMovieApp(container: AppContainer) {
    SmartMovieContent(
        catalog = container.catalog,
        library = container.library,
        images = container.images,
        versionName = BuildConfig.VERSION_NAME,
        watchRemote = container.watchRemote,
        appContainer = container,
    )
}

@Composable
internal fun SmartMovieContent(
    catalog: CatalogRepository,
    library: LibraryRepository,
    images: ImageUrlFactory,
    versionName: String,
    watchRemote: PhoneWatchRemoteController? = null,
    appContainer: AppContainer? = null,
) {
    val backStack = rememberNavBackStack(RootKey)
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val language = CatalogLocale.from(locale.language, locale.country)
    val providerRegion = appContainer?.preferences?.region?.collectAsState()
    val adultUnlocked = appContainer?.preferences?.adultUnlocked?.collectAsState()?.value == true
    val effectiveRegion = providerRegion?.value
        ?: locale.country.takeIf { it.length == 2 }
        ?: "US"
    val includeAdult = appContainer?.preferences?.let { adultUnlocked && it.includeAdult } == true
    androidx.compose.runtime.LaunchedEffect(watchRemote) {
        watchRemote?.actions?.collect { action ->
            when (action) {
                is PhoneRemoteAction.OpenDetails -> {
                    val key = action.title.toDetailKey()
                    val current = backStack.lastOrNull() as? DetailKey
                    if (current?.id != key.id || current.type != key.type) backStack.add(key)
                }
                is PhoneRemoteAction.OpenEpisode -> {
                    val key = action.episode.toEntityKey(action.series)
                    val current = backStack.lastOrNull() as? EntityKey
                    if (current != key) backStack.add(key)
                }
                is PhoneRemoteAction.PlayTrailer -> launchYoutube(context, action.youtubeKey)
            }
        }
    }
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<RootKey> {
                AppRoot(
                    catalog, library, images, versionName, language,
                    onTitleClick = { title -> backStack.add(title.toDetailKey()) },
                    onEntityClick = { entity, series ->
                        if (entity is CatalogEntity.Title) backStack.add(entity.value.toDetailKey())
                        else backStack.add(entity.toEntityKey(series))
                    },
                    onCreditClick = { credit -> credit.creditId?.let { backStack.add(CreditKey(it, credit.title.orEmpty())) } },
                    watchRemote = watchRemote,
                    appContainer = appContainer,
                )
            }
            entry<DetailKey> { key ->
                CinemaBackground {
                    val title = key.summary()
                    val accountRating = rememberTitleAccountRating(appContainer, title)
                    DetailRoute(
                        title = title, catalog = catalog, library = library,
                        images = images, language = language,
                        onBack = { backStack.removeLastOrNull() },
                        onTitleClick = { backStack.add(it.toDetailKey()) },
                        onRemoteStateChange = { watchRemote?.publish(it, images) },
                        onRemoteClosed = { watchRemote?.clear(it) },
                        region = effectiveRegion,
                        includeAdult = includeAdult,
                        onEntityClick = { entity -> backStack.add(entity.toEntityKey(title)) },
                        onCreditClick = { credit -> credit.creditId?.let { backStack.add(CreditKey(it, credit.title.orEmpty())) } },
                        accountRating = accountRating.value,
                        accountRatingEnabled = accountRating.signedIn,
                        accountRatingPending = accountRating.pending,
                        accountRatingError = accountRating.error,
                        onAccountRatingChange = accountRating.onChange,
                    )
                }
            }
            entry<EntityKey> { key ->
                CinemaBackground {
                    EntityDetailScreen(
                        key = key,
                        catalog = catalog as com.lamndt.smartmovie.model.CatalogV2Repository,
                        images = images,
                        language = language,
                        onBack = { backStack.removeLastOrNull() },
                        onTitle = { backStack.add(it.toDetailKey()) },
                        onEntity = { backStack.add(it.toEntityKey(key.series)) },
                        onCredit = { credit -> credit.creditId?.let { backStack.add(CreditKey(it, credit.title.orEmpty())) } },
                        appContainer = appContainer,
                        watchRemote = watchRemote,
                    )
                }
            }
            entry<CreditKey> { key ->
                CinemaBackground {
                    CreditDetailScreen(
                        creditId = key.id,
                        label = key.label,
                        catalog = catalog as com.lamndt.smartmovie.model.CatalogV2Repository,
                        images = images,
                        language = language,
                        onBack = { backStack.removeLastOrNull() },
                        onPerson = { backStack.add(CatalogEntity.Person(it).toEntityKey()) },
                        onTitle = { backStack.add(it.toDetailKey()) },
                    )
                }
            }
        },
    )
}

@Composable
internal fun AppRoot(
    catalog: CatalogRepository,
    library: LibraryRepository,
    images: ImageUrlFactory,
    versionName: String,
    language: String,
    onTitleClick: (TitleSummary) -> Unit,
    onEntityClick: (CatalogEntity, TitleSummary?) -> Unit = { _, _ -> },
    onCreditClick: (Credit) -> Unit = {},
    watchRemote: PhoneWatchRemoteController? = null,
    appContainer: AppContainer? = null,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var paneTitle by remember { mutableStateOf<TitleSummary?>(null) }
    val focusRequester = remember { FocusRequester() }
    val expanded = isWindowWidthAtLeast(600)
    val providerRegion = appContainer?.preferences?.region?.collectAsState()
    val adultUnlocked = appContainer?.preferences?.adultUnlocked?.collectAsState()?.value == true
    val deviceRegion = LocalConfiguration.current.locales[0].country
    val effectiveRegion = providerRegion?.value
        ?: deviceRegion.takeIf { it.length == 2 }
        ?: "US"
    val includeAdult = appContainer?.preferences?.let { adultUnlocked && it.includeAdult } == true
    androidx.compose.runtime.LaunchedEffect(Unit) { focusRequester.requestFocus() }
    CinemaBackground(
        Modifier
            .onPreviewKeyEvent { event ->
                val shortcut = event.shortcutTab()
                if (shortcut != null) {
                    tab = shortcut
                    paneTitle = null
                    showAbout = false
                    true
                } else {
                    false
                }
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        if (showAbout) {
            Box(Modifier.fillMaxSize()) {
                AboutScreen(versionName)
                IconButton(onClick = { showAbout = false }, Modifier.padding(top = 44.dp, end = 12.dp).align(androidx.compose.ui.Alignment.TopEnd)) {
                    Icon(Icons.Default.Home, stringResource(R.string.home))
                }
            }
        } else if (expanded) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = CinemaColors.Elevated, header = {
                    IconButton(onClick = { showAbout = true }) { Icon(Icons.Default.Info, stringResource(R.string.about)) }
                }) {
                    AppTab.entries.forEach { item ->
                        NavigationRailItem(
                            selected = tab == item, onClick = { tab = item; paneTitle = null },
                            icon = { Icon(item.icon, stringResource(item.label)) }, label = { Text(stringResource(item.label)) },
                        )
                    }
                }
                TabContent(
                    tab, catalog, library, images, language, { paneTitle = it },
                    { entity -> onEntityClick(entity, null) },
                    Modifier.weight(if (paneTitle == null) 1f else .42f), appContainer,
                    effectiveRegion, includeAdult,
                )
                paneTitle?.let { title ->
                    val accountRating = rememberTitleAccountRating(appContainer, title)
                    DetailRoute(
                        title = title,
                        catalog = catalog,
                        library = library,
                        images = images,
                        language = language,
                        onBack = { paneTitle = null },
                        onTitleClick = { paneTitle = it },
                        modifier = Modifier.weight(.58f),
                        onRemoteStateChange = { watchRemote?.publish(it, images) },
                        onRemoteClosed = { watchRemote?.clear(it) },
                        region = effectiveRegion,
                        includeAdult = includeAdult,
                        onEntityClick = { entity -> onEntityClick(entity, title) },
                        onCreditClick = onCreditClick,
                        accountRating = accountRating.value,
                        accountRatingEnabled = accountRating.signedIn,
                        accountRatingPending = accountRating.pending,
                        accountRatingError = accountRating.error,
                        onAccountRatingChange = accountRating.onChange,
                    )
                }
            }
        } else {
            Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                bottomBar = {
                    NavigationBar(containerColor = CinemaColors.Elevated) {
                        AppTab.entries.forEach { item ->
                            NavigationBarItem(
                            selected = tab == item, onClick = { tab = item },
                                icon = { Icon(item.icon, stringResource(item.label)) }, label = { Text(stringResource(item.label)) },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                    TabContent(
                        tab, catalog, library, images, language, onTitleClick,
                        { entity -> onEntityClick(entity, null) },
                        appContainer = appContainer, region = effectiveRegion, includeAdult = includeAdult,
                    )
                    IconButton(onClick = { showAbout = true }, Modifier.padding(top = 42.dp, end = 8.dp).align(androidx.compose.ui.Alignment.TopEnd)) {
                        Icon(Icons.Default.Info, stringResource(R.string.about), tint = CinemaColors.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    tab: AppTab,
    catalog: CatalogRepository,
    library: LibraryRepository,
    images: ImageUrlFactory,
    language: String,
    onTitleClick: (TitleSummary) -> Unit,
    onEntityClick: (CatalogEntity) -> Unit = {},
    modifier: Modifier = Modifier,
    appContainer: AppContainer? = null,
    region: String = "US",
    includeAdult: Boolean = false,
) {
    when (tab) {
        AppTab.HOME -> HomeRoute(catalog, images, language, onTitleClick, modifier)
        AppTab.EXPLORE -> ExploreRoute(
            catalog, images, language, onTitleClick, modifier,
            region = region, includeAdult = includeAdult,
        )
        AppTab.SEARCH -> SearchRoute(
            catalog, images, language, onTitleClick, modifier, onEntityClick,
            includeAdult = includeAdult,
        )
        AppTab.LIBRARY -> LibraryRoute(library, images, onTitleClick, modifier)
        AppTab.PROFILE -> appContainer?.let {
            ProfileScreen(it, language, isTv = false, modifier = modifier, onTitleClick = onTitleClick)
        }
            ?: AboutScreen(versionName = "", modifier = modifier)
    }
}

private fun TitleSummary.toDetailKey() = DetailKey(
    id = id,
    type = mediaType.wireValue,
    title = title,
    originalTitle = originalTitle,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    rating = voteAverage,
    adult = adult,
)

private fun CatalogEntity.toEntityKey(series: TitleSummary? = null): EntityKey = when (this) {
    is CatalogEntity.Person -> EntityKey("person", value.id, value.name)
    is CatalogEntity.Collection -> EntityKey("collection", value.id, value.name)
    is CatalogEntity.Organization -> EntityKey(value.entityKind.wireValue, value.id, value.name)
    is CatalogEntity.Keyword -> EntityKey("keyword", value.id, value.name)
    is CatalogEntity.Season -> EntityKey(
        "season", value.id, value.name, value.seriesId, value.seasonNumber, series = series,
    )
    is CatalogEntity.Episode -> EntityKey(
        "episode", value.id, value.name, value.seriesId, value.seasonNumber, value.episodeNumber, series,
    )
    is CatalogEntity.Title -> error("Title entities use DetailKey")
}

private fun com.lamndt.smartmovie.model.EpisodeDetail.toEntityKey(series: TitleSummary) = EntityKey(
    kind = "episode",
    id = id,
    name = name,
    seriesId = seriesId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    series = series,
)

private fun KeyEvent.shortcutTab(): AppTab? {
    if (type != KeyEventType.KeyDown || (!isCtrlPressed && !isMetaPressed)) return null
    return when (key) {
        Key.One -> AppTab.HOME
        Key.Two -> AppTab.EXPLORE
        Key.Three, Key.F -> AppTab.SEARCH
        Key.Four -> AppTab.LIBRARY
        Key.Five -> AppTab.PROFILE
        else -> null
    }
}

private fun PhoneWatchRemoteController.publish(state: DetailRemoteState, images: ImageUrlFactory) {
    val artwork = images.url(state.title.backdropPath, com.lamndt.smartmovie.model.ImageKind.BACKDROP)
        ?: images.url(state.title.posterPath, com.lamndt.smartmovie.model.ImageKind.POSTER)
    publish(state.title, state.membership, state.trailerKey, artwork)
}

private fun launchYoutube(context: Context, youtubeKey: String) {
    val appIntent = Intent(Intent.ACTION_VIEW, "vnd.youtube:$youtubeKey".toUri())
    val webIntent = Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=$youtubeKey".toUri())
    context.startActivity(if (appIntent.resolveActivity(context.packageManager) != null) appIntent else webIntent)
}
