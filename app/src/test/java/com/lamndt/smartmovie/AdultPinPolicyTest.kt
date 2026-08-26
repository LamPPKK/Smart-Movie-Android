package com.lamndt.smartmovie

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdultPinPolicyTest {
    @Test
    fun configurationRequiresExplicitAgeConfirmation() {
        assertThat(AdultPinPolicy.canConfigure("123456", "123456", ageConfirmed = false)).isFalse()
        assertThat(AdultPinPolicy.canConfigure("123456", "123456", ageConfirmed = true)).isTrue()
    }

    @Test
    fun configurationRequiresMatchingSixDigitNumericPin() {
        assertThat(AdultPinPolicy.canConfigure("12345", "12345", ageConfirmed = true)).isFalse()
        assertThat(AdultPinPolicy.canConfigure("123456", "654321", ageConfirmed = true)).isFalse()
        assertThat(AdultPinPolicy.canConfigure("abcdef", "abcdef", ageConfirmed = true)).isFalse()
    }

    @Test
    fun fifthFailureLocksForFiveMinutesUsingRebootStableWallClock() {
        val now = 1_800_000_000_000L
        var state = AdultFailureState(0, 0)

        repeat(4) { attempt ->
            state = AdultPinPolicy.recordFailure(state.failedAttempts, now)
            assertThat(state.failedAttempts).isEqualTo(attempt + 1)
            assertThat(state.lockUntil).isEqualTo(0)
        }
        state = AdultPinPolicy.recordFailure(state.failedAttempts, now)
        assertThat(state.failedAttempts).isEqualTo(0)
        assertThat(state.lockUntil).isEqualTo(now + 5 * 60_000L)
        assertThat(state.lockUntil - now).isEqualTo(5 * 60_000L)
    }
}
