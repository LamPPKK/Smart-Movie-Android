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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
}

@Serializable
private data object RootKey : NavKey

@Serializable
private data class DetailKey(
    val id: Int,
    val type: String,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val rating: Double,
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
    )
}

@Composable
fun SmartMovieApp(container: AppContainer) {
    SmartMovieContent(
        catalog = container.catalog,
        library = container.library,
        images = container.images,
        versionName = BuildConfig.VERSION_NAME,
        watchRemote = container.watchRemote,
    )
}

@Composable
internal fun SmartMovieContent(
    catalog: CatalogRepository,
    library: LibraryRepository,
    images: ImageUrlFactory,
    versionName: String,
    watchRemote: PhoneWatchRemoteController? = null,
) {
    val backStack = rememberNavBackStack(RootKey)
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val language = CatalogLocale.from(locale.language, locale.country)
    androidx.compose.runtime.LaunchedEffect(watchRemote) {
        watchRemote?.actions?.collect { action ->
            when (action) {
                is PhoneRemoteAction.OpenDetails -> {
                    val key = action.title.toDetailKey()
                    val current = backStack.lastOrNull() as? DetailKey
                    if (current?.id != key.id || current.type != key.type) backStack.add(key)
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
                    watchRemote = watchRemote,
                )
            }
            entry<DetailKey> { key ->
                CinemaBackground {
                    DetailRoute(
                        title = key.summary(), catalog = catalog, library = library,
                        images = images, language = language,
                        onBack = { backStack.removeLastOrNull() },
                        onTitleClick = { backStack.add(it.toDetailKey()) },
                        onRemoteStateChange = { watchRemote?.publish(it, images) },
                        onRemoteClosed = { watchRemote?.clear(it) },
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
    watchRemote: PhoneWatchRemoteController? = null,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var paneTitle by remember { mutableStateOf<TitleSummary?>(null) }
    val focusRequester = remember { FocusRequester() }
    val expanded = isWindowWidthAtLeast(600)
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
                    Modifier.weight(if (paneTitle == null) 1f else .42f),
                )
                paneTitle?.let { title ->
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
                    TabContent(tab, catalog, library, images, language, onTitleClick)
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
    modifier: Modifier = Modifier,
) {
    when (tab) {
        AppTab.HOME -> HomeRoute(catalog, images, language, onTitleClick, modifier)
        AppTab.EXPLORE -> ExploreRoute(catalog, images, language, onTitleClick, modifier)
        AppTab.SEARCH -> SearchRoute(catalog, images, language, onTitleClick, modifier)
        AppTab.LIBRARY -> LibraryRoute(library, images, onTitleClick, modifier)
    }
}

private fun TitleSummary.toDetailKey() = DetailKey(
    id, mediaType.wireValue, title, originalTitle, overview, posterPath, backdropPath, releaseDate, voteAverage,
)

private fun KeyEvent.shortcutTab(): AppTab? {
    if (type != KeyEventType.KeyDown || (!isCtrlPressed && !isMetaPressed)) return null
    return when (key) {
        Key.One -> AppTab.HOME
        Key.Two -> AppTab.EXPLORE
        Key.Three, Key.F -> AppTab.SEARCH
        Key.Four -> AppTab.LIBRARY
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
