package com.lamndt.smartmovie.multiplatform.data

import com.lamndt.smartmovie.multiplatform.model.TitleSummary
import com.lamndt.smartmovie.multiplatform.platform.KeyValueStore
import com.lamndt.smartmovie.multiplatform.platform.systemTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class LibraryCollection { FAVORITES, WATCHLIST }

@Serializable
data class LibraryRecord(
    val title: TitleSummary,
    val isFavorite: Boolean = false,
    val isWatchlisted: Boolean = false,
    val favoritedAt: Long? = null,
    val watchlistedAt: Long? = null,
    val updatedAt: Long,
)

class PersistentLibrary(
    private val store: KeyValueStore,
    private val clock: () -> Long = ::systemTimeMillis,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) {
    private val mutableRecords = MutableStateFlow(readRecords())
    val records: StateFlow<List<LibraryRecord>> = mutableRecords.asStateFlow()

    fun toggle(title: TitleSummary, collection: LibraryCollection) {
        val now = clock()
        val current = mutableRecords.value.associateBy { it.title.libraryKey }.toMutableMap()
        val existing = current[title.libraryKey] ?: LibraryRecord(title = title, updatedAt = now)
        val changed = when (collection) {
            LibraryCollection.FAVORITES -> existing.copy(
                title = title,
                isFavorite = !existing.isFavorite,
                favoritedAt = if (existing.isFavorite) null else now,
                updatedAt = now,
            )
            LibraryCollection.WATCHLIST -> existing.copy(
                title = title,
                isWatchlisted = !existing.isWatchlisted,
                watchlistedAt = if (existing.isWatchlisted) null else now,
                updatedAt = now,
            )
        }
        if (changed.isFavorite || changed.isWatchlisted) current[title.libraryKey] = changed else current.remove(title.libraryKey)
        val next = current.values.sortedByDescending { it.updatedAt }
        mutableRecords.value = next
        store.putString(STORE_KEY, json.encodeToString(next))
    }

    fun membership(libraryKey: String): Pair<Boolean, Boolean> {
        val record = mutableRecords.value.firstOrNull { it.title.libraryKey == libraryKey }
        return (record?.isFavorite == true) to (record?.isWatchlisted == true)
    }

    private fun readRecords(): List<LibraryRecord> = store.getString(STORE_KEY)
        ?.let { runCatching { json.decodeFromString<List<LibraryRecord>>(it) }.getOrNull() }
        .orEmpty()

    companion object { private const val STORE_KEY = "smartmovie_library_v2" }
}
