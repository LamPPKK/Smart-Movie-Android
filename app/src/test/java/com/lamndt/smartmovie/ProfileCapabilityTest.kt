package com.lamndt.smartmovie

import com.google.common.truth.Truth.assertThat
import com.lamndt.smartmovie.model.ConfigurationCountry
import com.lamndt.smartmovie.model.DiscoverConfiguration
import com.lamndt.smartmovie.model.supportsAccountAuthentication
import com.lamndt.smartmovie.testing.FakeCatalogRepository
import com.lamndt.smartmovie.testing.FakeCatalogV2Repository
import com.lamndt.smartmovie.testing.capabilities
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProfileCapabilityTest {
    @Test
    fun providerRegionsRequireAdvancedDiscoverCapability() = runTest {
        val unavailableCatalog = FakeCatalogRepository()
        val disabledCatalog = FakeCatalogV2Repository().apply {
            capabilitiesResult = { capabilities(advancedDiscover = false) }
        }
        val enabledCatalog = FakeCatalogV2Repository().apply {
            capabilitiesResult = { capabilities(advancedDiscover = true) }
            configurationResult = { _, region ->
                DiscoverConfiguration(
                    watchProviderRegions = listOf(ConfigurationCountry("VN", "Vietnam", "Việt Nam")),
                    region = region,
                )
            }
        }

        val unavailable = loadProfileProviderRegions(unavailableCatalog, null, "vi-VN", "VN")
        val disabledCapabilities = capabilities(advancedDiscover = false)
        val enabledCapabilities = capabilities(advancedDiscover = true)
        val disabled = loadProfileProviderRegions(disabledCatalog, disabledCapabilities, "vi-VN", "VN")
        val enabled = loadProfileProviderRegions(enabledCatalog, enabledCapabilities, "vi-VN", "VN")

        assertThat(unavailable).isEmpty()
        assertThat(disabled).isEmpty()
        assertThat(disabledCatalog.configurationCalls).isEmpty()
        assertThat(enabled.map(ConfigurationCountry::code)).containsExactly("VN")
        assertThat(enabledCatalog.configurationCalls).containsExactly("vi-VN" to "VN")
    }

    @Test
    fun accountAuthenticationFailsClosedAndUsesPlatformCapability() {
        val disabled = capabilities(advancedDiscover = true)
        val browser = capabilities(advancedDiscover = true, browserAuth = true, tvQrAuth = false)
        val television = capabilities(advancedDiscover = true, browserAuth = false, tvQrAuth = true)

        assertThat(null.supportsAccountAuthentication(isTv = false)).isFalse()
        assertThat(disabled.supportsAccountAuthentication(isTv = false)).isFalse()
        assertThat(browser.supportsAccountAuthentication(isTv = false)).isTrue()
        assertThat(browser.supportsAccountAuthentication(isTv = true)).isFalse()
        assertThat(television.supportsAccountAuthentication(isTv = true)).isTrue()
    }
}
