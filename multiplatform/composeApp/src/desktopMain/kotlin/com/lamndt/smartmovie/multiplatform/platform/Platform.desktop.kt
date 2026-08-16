package com.lamndt.smartmovie.multiplatform.platform

import java.awt.Desktop
import java.net.URI
import java.util.Locale
import java.util.prefs.Preferences

private class DesktopKeyValueStore : KeyValueStore {
    private val preferences = Preferences.userRoot().node("com/lamndt/smartmovie")

    override fun getString(key: String): String? = preferences.get(key, null)

    override fun putString(key: String, value: String) {
        preferences.put(key, value)
        preferences.flush()
    }
}

actual fun createKeyValueStore(): KeyValueStore = DesktopKeyValueStore()

actual fun openExternalUrl(url: String): Boolean = runCatching {
    if (!Desktop.isDesktopSupported()) return@runCatching false
    Desktop.getDesktop().browse(URI(url))
    true
}.getOrDefault(false)

actual fun platformName(): String {
    val os = System.getProperty("os.name").lowercase(Locale.US)
    return when {
        "mac" in os -> "macOS"
        "win" in os -> "Windows"
        "linux" in os -> "Linux"
        else -> System.getProperty("os.name")
    }
}

actual fun catalogBaseUrl(): String =
    System.getenv("SMARTMOVIE_CATALOG_BASE_URL")?.takeIf { it.startsWith("http") }
        ?: "https://catalog.smartmovie.app/"
