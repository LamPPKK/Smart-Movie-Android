package com.lamndt.smartmovie.multiplatform.platform

import io.ktor.client.request.HttpRequestBuilder
import kotlin.time.Clock

interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

interface SessionCredentialStore {
    fun load(): String?
    fun save(token: String)
    fun clear()
}

expect fun createKeyValueStore(): KeyValueStore
expect fun createSessionCredentialStore(): SessionCredentialStore
expect fun openExternalUrl(url: String): Boolean
expect fun authReturnUri(): String
expect fun authMode(): String
expect fun HttpRequestBuilder.applySessionRequestOptions()
expect fun platformName(): String
expect fun catalogBaseUrl(): String
fun systemTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
