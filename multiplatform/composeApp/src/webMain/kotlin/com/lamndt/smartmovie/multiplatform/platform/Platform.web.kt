package com.lamndt.smartmovie.multiplatform.platform

import kotlinx.browser.window

private class BrowserKeyValueStore : KeyValueStore {
    override fun getString(key: String): String? = runCatching { window.localStorage.getItem(key) }.getOrNull()

    override fun putString(key: String, value: String) {
        runCatching { window.localStorage.setItem(key, value) }
    }
}

actual fun createKeyValueStore(): KeyValueStore = BrowserKeyValueStore()

actual fun openExternalUrl(url: String): Boolean = window.open(url, "_blank", "noopener,noreferrer") != null

actual fun platformName(): String = "Web"

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
