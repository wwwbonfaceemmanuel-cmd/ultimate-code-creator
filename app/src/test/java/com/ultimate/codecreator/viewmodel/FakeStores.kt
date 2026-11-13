package com.ultimate.codecreator.viewmodel

import com.ultimate.codecreator.data.SettingsStore
import com.ultimate.codecreator.data.SnippetStore
import com.ultimate.codecreator.model.AppSettings
import com.ultimate.codecreator.model.SavedSnippet
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
