package com.lamndt.smartmovie.multiplatform.platform

import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication

private class AppleKeyValueStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
}

actual fun createKeyValueStore(): KeyValueStore = AppleKeyValueStore()

@Suppress("DEPRECATION")
actual fun openExternalUrl(url: String): Boolean {
    val target = NSURL.URLWithString(url) ?: return false
    return UIApplication.sharedApplication.openURL(target)
}

actual fun platformName(): String = "iOS / iPadOS"

actual fun catalogBaseUrl(): String = "https://catalog.smartmovie.app/"
