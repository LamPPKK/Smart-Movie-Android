package com.lamndt.smartmovie.multiplatform.data

import com.lamndt.smartmovie.multiplatform.model.MediaType
import com.lamndt.smartmovie.multiplatform.model.TitleSummary
import com.lamndt.smartmovie.multiplatform.platform.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistentLibraryTest {
    @Test
    fun favoriteAndWatchlistRemainIndependentAndPersist() {
        val store = MemoryStore()
        var now = 100L
        val library = PersistentLibrary(store, clock = { now++ })

        library.toggle(title, LibraryCollection.FAVORITES)
        assertEquals(true to false, library.membership(title.libraryKey))

        library.toggle(title, LibraryCollection.WATCHLIST)
        assertEquals(true to true, library.membership(title.libraryKey))

        library.toggle(title, LibraryCollection.FAVORITES)
        assertEquals(false to true, library.membership(title.libraryKey))

        val restored = PersistentLibrary(store, clock = { now++ })
        val record = restored.records.value.single()
        assertFalse(record.isFavorite)
        assertTrue(record.isWatchlisted)
        assertEquals(null, record.favoritedAt)
        assertEquals(101L, record.watchlistedAt)
    }

    @Test
    fun removingLastCollectionDeletesTheSnapshot() {
        val library = PersistentLibrary(MemoryStore(), clock = { 42L })
        library.toggle(title, LibraryCollection.WATCHLIST)
        library.toggle(title, LibraryCollection.WATCHLIST)
        assertTrue(library.records.value.isEmpty())
    }

    private val title = TitleSummary(
        id = 550,
        mediaType = MediaType.MOVIE,
        title = "Fight Club",
        originalTitle = "Fight Club",
        overview = "An insomniac encounters a soap maker.",
    )
}

internal class MemoryStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) { values[key] = value }
}
