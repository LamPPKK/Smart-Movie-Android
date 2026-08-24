package com.lamndt.smartmovie

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

class CatalogPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("catalog_preferences", Context.MODE_PRIVATE)
    private val mutableRegion = MutableStateFlow(preferences.getString(REGION, null))
    val region: StateFlow<String?> = mutableRegion.asStateFlow()
    private val mutableAdultUnlocked = MutableStateFlow(false)
    val adultUnlocked: StateFlow<Boolean> = mutableAdultUnlocked.asStateFlow()

    val adultConfigured: Boolean get() = preferences.contains(PIN_DIGEST)
    val includeAdult: Boolean get() = adultConfigured && mutableAdultUnlocked.value && !isLocked
    val failedAttempts: Int get() = preferences.getInt(FAILURES, 0)
    val isLocked: Boolean get() = preferences.getLong(LOCK_UNTIL, 0L) > SystemClock.elapsedRealtime()

    fun setRegion(value: String?) {
        val normalized = value?.uppercase(Locale.ROOT)?.takeIf { it.matches(Regex("[A-Z]{2}")) }
        preferences.edit().putString(REGION, normalized).apply()
        mutableRegion.value = normalized
    }

    fun configureAdult(pin: String, confirmation: String): Boolean {
        if (pin != confirmation || !pin.matches(Regex("[0-9]{6}"))) return false
        val salt = ByteArray(24).also(SecureRandom()::nextBytes).toHex()
        preferences.edit()
            .putString(PIN_SALT, salt)
            .putString(PIN_DIGEST, digest(salt, pin))
            .putInt(FAILURES, 0)
            .remove(LOCK_UNTIL)
            .apply()
        mutableAdultUnlocked.value = true
        return true
    }

    fun unlockAdult(pin: String): Boolean {
        if (isLocked) return false
        val salt = preferences.getString(PIN_SALT, null)
        val expected = preferences.getString(PIN_DIGEST, null)
        if (pin.matches(Regex("[0-9]{6}")) && salt != null && digest(salt, pin) == expected) {
            preferences.edit().putInt(FAILURES, 0).apply()
            mutableAdultUnlocked.value = true
            return true
        }
        val failures = failedAttempts + 1
        if (failures >= 5) {
            preferences.edit().putInt(FAILURES, 0).putLong(LOCK_UNTIL, SystemClock.elapsedRealtime() + 5 * 60_000L).apply()
        } else preferences.edit().putInt(FAILURES, failures).apply()
        return false
    }

    fun lockAdult() { mutableAdultUnlocked.value = false }

    fun disableAdult() {
        preferences.edit().remove(PIN_SALT).remove(PIN_DIGEST).remove(FAILURES).remove(LOCK_UNTIL).apply()
        mutableAdultUnlocked.value = false
    }

    private fun digest(salt: String, pin: String) = MessageDigest.getInstance("SHA-256")
        .digest("$salt:$pin".toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private companion object {
        const val REGION = "region_override"
        const val PIN_SALT = "adult_pin_salt"
        const val PIN_DIGEST = "adult_pin_digest"
        const val FAILURES = "adult_pin_failures"
        const val LOCK_UNTIL = "adult_pin_lock_until_elapsed"
    }
}
