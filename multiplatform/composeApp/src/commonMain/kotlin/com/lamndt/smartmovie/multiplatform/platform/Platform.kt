package com.lamndt.smartmovie.multiplatform.platform

import kotlin.time.Clock

interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

expect fun createKeyValueStore(): KeyValueStore
expect fun openExternalUrl(url: String): Boolean
expect fun platformName(): String
expect fun catalogBaseUrl(): String
fun systemTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
