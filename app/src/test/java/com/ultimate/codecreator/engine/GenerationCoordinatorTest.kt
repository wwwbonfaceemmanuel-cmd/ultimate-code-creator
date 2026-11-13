package com.ultimate.codecreator.engine

import com.ultimate.codecreator.model.CloudGenerationException
import com.ultimate.codecreator.model.GenerationMode
import com.ultimate.codecreator.model.GenerationSource
import com.ultimate.codecreator.model.SupportedLanguage
import com.ultimate.codecreator.viewmodel.FakeCloudGenerator
import com.ultimate.codecreator.viewmodel.FakeCloudSettingsStore
import com.ultimate.codecreator.viewmodel.FakeNetworkMonitor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GenerationCoordinatorTest {
    private val novel = "quantum flux capacitor parser"

    private fun coordinator(
        cloud: FakeCloudGenerator = FakeCloudGenerator(),
        cloudSettings: FakeCloudSettingsStore = FakeCloudSettingsStore.configured(),
        network: FakeNetworkMonitor = FakeNetworkMonitor(true)
    ) = GenerationCoordinator(OfflineCodeGenerator(), cloud, cloudSettings, network)

    private suspend fun expectCloudError(block: suspend () -> Unit): CloudGenerationException {
        try {
            block()
        } catch (exception: CloudGenerationException) {
            return exception
        }
        throw AssertionError("Expected a CloudGenerationException")
    }

    @Test fun offlineModeReturnsRecipeWithoutCloud() = runTest {
        val cloud = FakeCloudGenerator()
        val result = coordinator(cloud = cloud).generate("Binary Search", SupportedLanguage.PYTHON, GenerationMode.OFFLINE)
        assertEquals(GenerationSource.OFFLINE_RECIPE, result.source)
        assertEquals(0, cloud.calls)
    }

    @Test fun autoKeepsConfidentRecipeOffline() = runTest {
        val cloud = FakeCloudGenerator()
        val result = coordinator(cloud = cloud).generate("Binary Search", SupportedLanguage.PYTHON, GenerationMode.AUTO)
        assertEquals(GenerationSource.OFFLINE_RECIPE, result.source)
        assertEquals(0, cloud.calls)
    }

    @Test fun autoUsesCloudForNovelRequest() = runTest {
        val cloud = FakeCloudGenerator()
        val result = coordinator(cloud = cloud).generate(novel, SupportedLanguage.PYTHON, GenerationMode.AUTO)
        assertEquals(GenerationSource.CLOUD_AI, result.source)
        assertEquals(1, cloud.calls)
    }

    @Test fun autoFallsBackToOfflineOnCloudError() = runTest {
        val cloud = FakeCloudGenerator(error = CloudGenerationException("Provider down"))
        val result = coordinator(cloud = cloud).generate(novel, SupportedLanguage.PYTHON, GenerationMode.AUTO)
        assertEquals(GenerationSource.OFFLINE_FALLBACK, result.source)
        assertTrue(result.explanation.contains("unavailable"))
        assertEquals(1, cloud.calls)
    }

    @Test fun autoStaysOfflineWithoutNetwork() = runTest {
        val cloud = FakeCloudGenerator()
        val result = coordinator(cloud = cloud, network = FakeNetworkMonitor(false)).generate(novel, SupportedLanguage.PYTHON, GenerationMode.AUTO)
        assertEquals(GenerationSource.OFFLINE_FALLBACK, result.source)
        assertEquals(0, cloud.calls)
    }

    @Test fun autoStaysOfflineWhenUnconfigured() = runTest {
        val cloud = FakeCloudGenerator()
        val result = coordinator(cloud = cloud, cloudSettings = FakeCloudSettingsStore()).generate(novel, SupportedLanguage.PYTHON, GenerationMode.AUTO)
        assertEquals(GenerationSource.OFFLINE_FALLBACK, result.source)
        assertEquals(0, cloud.calls)
    }

    @Test fun cloudModeUsesCloud() = runTest {
        val cloud = FakeCloudGenerator()
        val result = coordinator(cloud = cloud).generate("Binary Search", SupportedLanguage.PYTHON, GenerationMode.CLOUD)
        assertEquals(GenerationSource.CLOUD_AI, result.source)
        assertEquals(1, cloud.calls)
    }

    @Test fun cloudModeThrowsWhenUnconfigured() = runTest {
        val coordinator = coordinator(cloudSettings = FakeCloudSettingsStore())
        val error = expectCloudError { coordinator.generate("Binary Search", SupportedLanguage.PYTHON, GenerationMode.CLOUD) }
        assertTrue(error.message!!.contains("not configured"))
    }

    @Test fun cloudModeThrowsWithoutNetwork() = runTest {
        val coordinator = coordinator(network = FakeNetworkMonitor(false))
        val error = expectCloudError { coordinator.generate("Binary Search", SupportedLanguage.PYTHON, GenerationMode.CLOUD) }
        assertTrue(error.message!!.contains("network"))
    }

    @Test fun testConnectionSucceedsWhenConfigured() = runTest {
        val message = coordinator().testConnection()
        assertTrue(message.contains("Connected"))
    }

    @Test fun testConnectionThrowsWhenUnconfigured() = runTest {
        val coordinator = coordinator(cloudSettings = FakeCloudSettingsStore())
        expectCloudError { coordinator.testConnection() }
        Unit
    }
}
