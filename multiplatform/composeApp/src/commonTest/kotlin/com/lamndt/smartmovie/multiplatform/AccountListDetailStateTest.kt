package com.lamndt.smartmovie.multiplatform

import com.lamndt.smartmovie.multiplatform.model.MediaType
import com.lamndt.smartmovie.multiplatform.model.PagedResult
import com.lamndt.smartmovie.multiplatform.model.TitleSummary
import com.lamndt.smartmovie.multiplatform.model.UserList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountListDetailStateTest {
    @Test
    fun mixedPagesFilterAdultAndDeduplicateByLibraryKey() {
        val movie = title(1, MediaType.MOVIE)
        val adult = title(2, MediaType.MOVIE, adult = true)
        val series = title(3, MediaType.TV)
        val first = mergeAccountListPage(
            existing = null,
            page = UserList(7, "Mixed", page = 1, totalPages = 2, results = listOf(movie, adult)),
            includeAdult = false,
        )

        val result = mergeAccountListPage(
            existing = first,
            page = UserList(7, "Mixed", page = 2, totalPages = 2, results = listOf(movie, series)),
            includeAdult = false,
        )

        assertEquals(listOf("movie:1", "tv:3"), result.results.map(TitleSummary::libraryKey))
        assertEquals(2, result.page)
        assertEquals(2, result.totalPages)
    }

    @Test
    fun searchResultsExcludeExistingAdultAndDuplicates() {
        val existing = title(1, MediaType.MOVIE)
        val series = title(3, MediaType.TV)

        val result = filterAccountListSearchResults(
            candidates = listOf(existing, title(2, MediaType.MOVIE, adult = true), series, series),
            existing = listOf(existing),
            includeAdult = false,
        )

        assertEquals(listOf("tv:3"), result.map(TitleSummary::libraryKey))
    }

    @Test
    fun allAccountListPagesAreLoadedAndDeduplicated() = runTest {
        val requested = mutableListOf<Int>()

        val result = loadAllAccountLists { page ->
            requested += page
            PagedResult(page, 3, listOf(UserList(page.coerceAtMost(2), "List $page")))
        }

        assertEquals(listOf(1, 2, 3), requested)
        assertEquals(listOf(1, 2), result.map(UserList::id))
    }

    private fun title(id: Int, type: MediaType, adult: Boolean = false) = TitleSummary(
        id = id,
        mediaType = type,
        title = "Title $id",
        originalTitle = "Title $id",
        overview = "",
        adult = adult,
    )
}
