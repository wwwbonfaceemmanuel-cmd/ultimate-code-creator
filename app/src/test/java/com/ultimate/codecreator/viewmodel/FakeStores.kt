package com.ultimate.codecreator.viewmodel

import com.ultimate.codecreator.data.CloudSettingsStore
import com.ultimate.codecreator.data.SettingsStore
import com.ultimate.codecreator.data.SnippetStore
import com.ultimate.codecreator.engine.CloudGenerator
import com.ultimate.codecreator.engine.NetworkMonitor
import com.ultimate.codecreator.model.AppSettings
import com.ultimate.codecreator.model.CloudConfig
import com.ultimate.codecreator.model.CloudEndpoint
import com.ultimate.codecreator.model.GenerationResult
import com.ultimate.codecreator.model.GenerationSource
import com.ultimate.codecreator.model.SavedSnippet
import com.ultimate.codecreator.model.SupportedLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeSnippetStore : SnippetStore {
    private val items = MutableStateFlow<List<SavedSnippet>>(emptyList())

    override fun observeSnippets(query: String): Flow<List<SavedSnippet>> = items.map { list ->
        if (query.isBlank()) list else list.filter {
            it.title.contains(query, true) || it.code.contains(query, true) || it.tags.any { tag -> tag.contains(query, true) }
        }
    }

    override suspend fun getAll(): List<SavedSnippet> = items.value
    override suspend fun findById(id: String): SavedSnippet? = items.value.firstOrNull { it.id == id }
    override suspend fun save(snippet: SavedSnippet) {
        items.value = (items.value.filterNot { it.id == snippet.id } + snippet).sortedByDescending { it.updatedAt }
    }
    override suspend fun delete(id: String) { items.value = items.value.filterNot { it.id == id } }
    override suspend fun deleteAll() { items.value = emptyList() }
    override suspend fun replaceAll(snippets: List<SavedSnippet>) { items.value = snippets }
    override suspend fun count(): Int = items.value.size
    override suspend fun migrateLegacyIfNeeded(): Int = 0
}

class FakeSettingsStore : SettingsStore {
    private val state = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = state.asStateFlow()
    override suspend fun update(transform: (AppSettings) -> AppSettings) { state.value = transform(state.value) }
}

class FakeCloudSettingsStore(
    initial: CloudConfig = CloudConfig(),
    private var key: String? = null
) : CloudSettingsStore {
    private val state = MutableStateFlow(initial.copy(hasApiKey = key != null))
    override val config: StateFlow<CloudConfig> = state.asStateFlow()
    override suspend fun updateEndpoint(providerLabel: String, baseUrl: String, model: String) {
        state.value = state.value.copy(providerLabel = providerLabel, baseUrl = CloudConfig.normalizeBaseUrl(baseUrl), model = model)
    }
    override suspend fun setApiKey(apiKey: String) { key = apiKey; state.value = state.value.copy(hasApiKey = true) }
    override suspend fun clearApiKey() { key = null; state.value = state.value.copy(hasApiKey = false) }
    override suspend fun apiKey(): String? = key
    override suspend fun endpoint(): CloudEndpoint? {
        val current = state.value
        val secret = key ?: return null
        if (!current.isConfigured) return null
        return CloudEndpoint(current.baseUrl, current.model, secret)
    }

    companion object {
        fun configured(): FakeCloudSettingsStore =
            FakeCloudSettingsStore(CloudConfig("OpenAI", "https://api.test.invalid/v1", "test-model"), key = "sk-test")
    }
}

class FakeNetworkMonitor(var online: Boolean = true) : NetworkMonitor {
    override fun isOnline(): Boolean = online
}

class FakeCloudGenerator(
    var result: GenerationResult? = null,
    var error: Exception? = null
) : CloudGenerator {
    var calls = 0
    override suspend fun generate(prompt: String, language: SupportedLanguage, endpoint: CloudEndpoint): GenerationResult {
        calls++
        error?.let { throw it }
        return result ?: GenerationResult(
            code = "// cloud: $prompt",
            intentDetected = "AI result",
            explanation = "cloud",
            language = language,
            confidence = 0.6f,
            source = GenerationSource.CLOUD_AI,
            model = endpoint.model
        )
    }
}
