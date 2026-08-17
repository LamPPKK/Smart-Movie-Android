package com.lamndt.smartmovie.network

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

class ContractErrorConformanceTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun canonicalErrorFixtureDecodesWithProductionWireModel() {
        val workingDirectory = File(checkNotNull(System.getProperty("user.dir")))
        val fixture = generateSequence(workingDirectory) { it.parentFile }
            .map { File(it, "catalog-contract/v1/fixtures/error.json") }
            .firstOrNull(File::isFile)
        val envelope = json.decodeFromString<ErrorEnvelope>(checkNotNull(fixture).readText())

        assertThat(envelope.error.code).isEqualTo("rate_limited")
        assertThat(envelope.error.retryAfter).isEqualTo(60)
        assertThat(envelope.error.requestId).isNotEmpty()
    }
}
