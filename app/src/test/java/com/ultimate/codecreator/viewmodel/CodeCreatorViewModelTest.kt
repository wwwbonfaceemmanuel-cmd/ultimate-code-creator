package com.ultimate.codecreator.viewmodel

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

    @Test fun generateAndSaveSnippet() = runTest(dispatcher) {
        val store = FakeSnippetStore()
        val viewModel = CodeCreatorViewModel(store, FakeSettingsStore(), dispatcher, dispatcher)
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
        val viewModel = CodeCreatorViewModel(store, FakeSettingsStore(), dispatcher, dispatcher)
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
        val viewModel = CodeCreatorViewModel(FakeSnippetStore(), settings, dispatcher, dispatcher)
        viewModel.updateSettings { it.copy(themeMode = ThemeMode.DARK, previewJavaScriptEnabled = true) }
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, viewModel.settings.value.themeMode)
        assertTrue(viewModel.settings.value.previewJavaScriptEnabled)
    }
}
