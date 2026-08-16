package com.lamndt.smartmovie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lamndt.smartmovie.data.ImageUrlFactory
import com.lamndt.smartmovie.designsystem.SmartMovieTheme
import com.lamndt.smartmovie.model.Genre
import com.lamndt.smartmovie.model.HomeFeed
import com.lamndt.smartmovie.model.HomeSection
import com.lamndt.smartmovie.model.ImageConfiguration
import com.lamndt.smartmovie.model.MediaType
import com.lamndt.smartmovie.model.TitleDetail
import com.lamndt.smartmovie.model.TitleSummary
import com.lamndt.smartmovie.testing.FakeCatalogRepository
import com.lamndt.smartmovie.testing.FakeLibraryRepository
import org.junit.Rule
import org.junit.Test

class NavigationAndStateTest {
    @get:Rule val compose = createComposeRule()

    private val dune = TitleSummary(
        id = 42,
        mediaType = MediaType.MOVIE,
        title = "Dune",
        originalTitle = "Dune",
        overview = "A deterministic overview.",
        voteAverage = 8.4,
    )
    private val catalog = FakeCatalogRepository().apply {
        homeResult = { HomeFeed(it, dune, listOf(HomeSection("popular", "Popular", listOf(dune)))) }
        genresResult = { listOf(Genre(18, "Drama")) }
        detailResult = { type, id ->
            TitleDetail(id, type, "Dune", "Dune", "A deterministic overview.", voteAverage = 8.4)
        }
    }
    private val library = FakeLibraryRepository()
    private val images = ImageUrlFactory { ImageConfiguration.Fallback }

    @Test
    fun fourTabsFilterSheetAndHeadingSemanticsAreReachable() {
        setAppRoot()

        compose.onNodeWithText("Explore").performClick()
        compose.onNode(hasText("Explore") and isHeading()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Filters").performClick()
        compose.onNodeWithText("Reset").assertIsDisplayed()
        compose.onNodeWithText("Apply").performClick()

        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Find your next story").assertIsDisplayed()

        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("No favorites yet").assertIsDisplayed()
    }

    @Test
    fun selectedTabRestoresAfterSavedInstanceState() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            SmartMovieTheme {
                AppRoot(catalog, library, images, "2.0.0-test", "en-US", {})
            }
        }
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Find your next story").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Find your next story").assertIsDisplayed()
    }

    @Test
    fun heroNavigatesToDetailAndBackActionIsAccessible() {
        compose.setContent {
            SmartMovieTheme {
                SmartMovieContent(catalog, library, images, "2.0.0-test")
            }
        }
        compose.onNodeWithText("View details").performClick()

        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNode(hasText("Dune") and isHeading()).assertIsDisplayed()
    }

    private fun setAppRoot() {
        compose.setContent {
            SmartMovieTheme {
                AppRoot(catalog, library, images, "2.0.0-test", "en-US", {})
            }
        }
    }
}
