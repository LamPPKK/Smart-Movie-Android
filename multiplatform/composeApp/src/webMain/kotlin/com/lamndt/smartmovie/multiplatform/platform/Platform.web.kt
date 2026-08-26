@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.lamndt.smartmovie.multiplatform.platform

import kotlinx.browser.window
import io.ktor.client.fetchOptions
import io.ktor.client.request.HttpRequestBuilder
import kotlin.js.JsAny

private fun includeCredentials(): JsAny = js("'include'")

private class BrowserKeyValueStore : KeyValueStore {
    override fun getString(key: String): String? = runCatching { window.localStorage.getItem(key) }.getOrNull()

    override fun putString(key: String, value: String) {
        runCatching { window.localStorage.setItem(key, value) }
    }
}

actual fun createKeyValueStore(): KeyValueStore = BrowserKeyValueStore()

private object BrowserCookieCredentialStore : SessionCredentialStore {
    // The Worker owns the Secure HttpOnly cookie. JavaScript intentionally cannot
    // read or persist the opaque session token.
    override fun load(): String? = null
    override fun save(token: String) = Unit
    override fun clear() = Unit
}

actual fun createSessionCredentialStore(): SessionCredentialStore = BrowserCookieCredentialStore

actual fun openExternalUrl(url: String): Boolean = window.open(url, "_blank", "noopener,noreferrer") != null
actual fun authReturnUri(): String = "${window.location.origin}/auth/callback"
actual fun authMode(): String = "web"
actual fun HttpRequestBuilder.applySessionRequestOptions() {
    fetchOptions { credentials = includeCredentials() }
}

actual fun platformName(): String = "Web"

actual fun systemRegion(): String = regionFromLanguageTag(window.navigator.language) ?: "US"

actual fun catalogBaseUrl(): String {
    val parameters = window.location.search
        .removePrefix("?")
        .split('&')
    if (parameters.any { it == "preview=1" }) return window.location.origin
    val override = parameters
        .firstOrNull { it.substringBefore('=') == "api" }
        ?.substringAfter('=', "")
        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    return override ?: "https://catalog.smartmovie.app/"
}
