package com.ultimate.codecreator.viewmodel

import com.ultimate.codecreator.engine.GenerationCoordinator
import com.ultimate.codecreator.engine.OfflineCodeGenerator
import com.ultimate.codecreator.model.GenerationMode
import com.ultimate.codecreator.model.GenerationSource
import com.ultimate.codecreator.model.SupportedLanguage
import com.ultimate.codecreator.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CodeCreatorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(
        store: FakeSnippetStore = FakeSnippetStore(),
        settings: FakeSettingsStore = FakeSettingsStore(),
        cloudSettings: FakeCloudSettingsStore = FakeCloudSettingsStore(),
        cloud: FakeCloudGenerator = FakeCloudGenerator(),
        network: FakeNetworkMonitor = FakeNetworkMonitor()
    ): CodeCreatorViewModel {
        val coordinator = GenerationCoordinator(OfflineCodeGenerator(), cloud, cloudSettings, network)
        return CodeCreatorViewModel(store, settings, cloudSettings, coordinator, dispatcher, dispatcher)
    }

    @Test fun generateAndSaveSnippet() = runTest(dispatcher) {
        val store = FakeSnippetStore()
        val viewModel = viewModel(store = store)
        viewModel.generateCodeOffline("Binary Search", SupportedLanguage.PYTHON)
        advanceUntilIdle()
        val result = viewModel.generationResult.value
        assertNotNull(result)
        viewModel.saveSnippet("Binary search", result!!.code, result.language, listOf("algorithm"))
        advanceUntilIdle()
        assertEquals(1, store.getAll().size)
        assertEquals(listOf("algorithm"), store.getAll().first().tags)
    }

    @Test fun exportImportRoundTrip() = runTest(dispatcher) {
        val store = FakeSnippetStore()
        val viewModel = viewModel(store = store)
        viewModel.saveSnippet("One", "print('one')", SupportedLanguage.PYTHON)
        advanceUntilIdle()
        val pack = viewModel.buildExportPack("test")
        viewModel.deleteAllSnippets()
        advanceUntilIdle()
        assertEquals(0, store.count())
        viewModel.importPack(pack, replaceExisting = false)
        advanceUntilIdle()
        assertEquals(1, store.count())
        assertEquals("One", store.getAll().first().title)
    }

    @Test fun settingsUpdate() = runTest(dispatcher) {
        val settings = FakeSettingsStore()
        val viewModel = viewModel(settings = settings)
        viewModel.updateSettings { it.copy(themeMode = ThemeMode.DARK, previewJavaScriptEnabled = true) }
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, viewModel.settings.value.themeMode)
        assertTrue(viewModel.settings.value.previewJavaScriptEnabled)
    }

    @Test fun autoModeStaysOfflineWhenCloudUnconfigured() = runTest(dispatcher) {
        val cloud = FakeCloudGenerator()
        val viewModel = viewModel(cloud = cloud)
        viewModel.generateCode("Binary Search", SupportedLanguage.PYTHON)
        advanceUntilIdle()
        assertEquals(GenerationSource.OFFLINE_RECIPE, viewModel.generationResult.value?.source)
        assertEquals(0, cloud.calls)
    }

    @Test fun autoModeUsesCloudForNovelRequest() = runTest(dispatcher) {
        val cloud = FakeCloudGenerator()
        val viewModel = viewModel(cloudSettings = FakeCloudSettingsStore.configured(), cloud = cloud, network = FakeNetworkMonitor(true))
        viewModel.generateCode("quantum flux capacitor parser", SupportedLanguage.PYTHON)
        advanceUntilIdle()
        assertEquals(GenerationSource.CLOUD_AI, viewModel.generationResult.value?.source)
        assertEquals(1, cloud.calls)
    }

    @Test fun offlineModeNeverUsesCloud() = runTest(dispatcher) {
        val cloud = FakeCloudGenerator()
        val viewModel = viewModel(cloudSettings = FakeCloudSettingsStore.configured(), cloud = cloud)
        viewModel.setGenerationMode(GenerationMode.OFFLINE)
        advanceUntilIdle()
        viewModel.generateCode("quantum flux capacitor parser", SupportedLanguage.PYTHON)
        advanceUntilIdle()
        assertEquals(0, cloud.calls)
        assertEquals(GenerationSource.OFFLINE_FALLBACK, viewModel.generationResult.value?.source)
    }

    @Test fun cloudModeUnconfiguredSurfacesMessage() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setGenerationMode(GenerationMode.CLOUD)
        advanceUntilIdle()
        viewModel.generateCode("Binary Search", SupportedLanguage.PYTHON)
        advanceUntilIdle()
        assertNull(viewModel.generationResult.value)
        assertNotNull(viewModel.message.value)
        assertFalse(viewModel.isGenerating.value)
    }
}
