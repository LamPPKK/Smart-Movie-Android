package com.lamndt.smartmovie.multiplatform.platform

import java.awt.Desktop
import java.net.URI
import java.util.Locale
import java.util.prefs.Preferences
import java.io.File
import io.ktor.client.request.HttpRequestBuilder

private class DesktopKeyValueStore : KeyValueStore {
    private val preferences = Preferences.userRoot().node("com/lamndt/smartmovie")

    override fun getString(key: String): String? = preferences.get(key, null)

    override fun putString(key: String, value: String) {
        preferences.put(key, value)
        preferences.flush()
    }
}

actual fun createKeyValueStore(): KeyValueStore = DesktopKeyValueStore()

private class DesktopCredentialStore : SessionCredentialStore {
    private val os = System.getProperty("os.name").lowercase(Locale.US)
    private val windowsFile = File(System.getProperty("user.home"), ".smartmovie/session.dpapi")

    override fun load(): String? = when {
        "mac" in os -> command(listOf("security", "find-generic-password", "-a", ACCOUNT, "-s", SERVICE, "-w"))
        "win" in os -> {
            if (!windowsFile.isFile) null else command(
                listOf(
                    "powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "[Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes('${windowsFile.path.replace("'", "''")}'),\$null,[Security.Cryptography.DataProtectionScope]::CurrentUser))",
                ),
            )
        }
        else -> command(listOf("secret-tool", "lookup", "app", "smartmovie", "account", ACCOUNT))
    }?.trim()?.takeIf(String::isNotEmpty)

    override fun save(token: String) {
        when {
            "mac" in os -> command(
                listOf("security", "add-generic-password", "-U", "-a", ACCOUNT, "-s", SERVICE, "-w", token),
            )
            "win" in os -> {
                windowsFile.parentFile.mkdirs()
                command(
                    listOf(
                        "powershell", "-NoProfile", "-NonInteractive", "-Command",
                        "[IO.File]::WriteAllBytes('${windowsFile.path.replace("'", "''")}',[Security.Cryptography.ProtectedData]::Protect([Text.Encoding]::UTF8.GetBytes(\$env:SMARTMOVIE_SESSION),\$null,[Security.Cryptography.DataProtectionScope]::CurrentUser))",
                    ),
                    mapOf("SMARTMOVIE_SESSION" to token),
                )
            }
            else -> command(
                listOf("secret-tool", "store", "--label=SmartMovie", "app", "smartmovie", "account", ACCOUNT),
                input = token,
            )
        }
    }

    override fun clear() {
        when {
            "mac" in os -> command(listOf("security", "delete-generic-password", "-a", ACCOUNT, "-s", SERVICE))
            "win" in os -> windowsFile.delete()
            else -> command(listOf("secret-tool", "clear", "app", "smartmovie", "account", ACCOUNT))
        }
    }

    private fun command(
        arguments: List<String>,
        environment: Map<String, String> = emptyMap(),
        input: String? = null,
    ): String? = runCatching {
        val process = ProcessBuilder(arguments).redirectError(ProcessBuilder.Redirect.DISCARD).apply {
            environment().putAll(environment)
        }.start()
        if (input != null) process.outputStream.bufferedWriter().use { it.write(input) }
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() == 0) output else null
    }.getOrNull()

    private companion object {
        const val ACCOUNT = "tmdb-session"
        const val SERVICE = "app.smartmovie.session"
    }
}

actual fun createSessionCredentialStore(): SessionCredentialStore = DesktopCredentialStore()

actual fun openExternalUrl(url: String): Boolean = runCatching {
    if (!Desktop.isDesktopSupported()) return@runCatching false
    Desktop.getDesktop().browse(URI(url))
    true
}.getOrDefault(false)

actual fun authReturnUri(): String = "smartmovie://auth/callback"
actual fun authMode(): String = "browser"
actual fun HttpRequestBuilder.applySessionRequestOptions() = Unit

actual fun platformName(): String {
    val os = System.getProperty("os.name").lowercase(Locale.US)
    return when {
        "mac" in os -> "macOS"
        "win" in os -> "Windows"
        "linux" in os -> "Linux"
        else -> System.getProperty("os.name")
    }
}

actual fun systemRegion(): String = Locale.getDefault().country
    .uppercase(Locale.ROOT)
    .takeIf { it.length == 2 }
    ?: "US"

actual fun catalogBaseUrl(): String =
    System.getenv("SMARTMOVIE_CATALOG_BASE_URL")?.takeIf { it.startsWith("http") }
        ?: "https://catalog.smartmovie.app/"
