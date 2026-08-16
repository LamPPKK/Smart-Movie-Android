package com.lamndt.smartmovie.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.smartMovieDataStore by preferencesDataStore(name = "smartmovie_installation")

class InstallationIdStore(private val context: Context) {
    private val key = stringPreferencesKey("installation_id")

    suspend fun get(): String {
        val preferences = context.smartMovieDataStore.data.first()
        preferences[key]?.takeIf(::isUuid)?.let { return it }
        val created = UUID.randomUUID().toString().lowercase()
        context.smartMovieDataStore.edit { it[key] = created }
        return created
    }

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess
}
