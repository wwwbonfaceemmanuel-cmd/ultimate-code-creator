package com.ultimate.codecreator.data

import com.ultimate.codecreator.model.SavedSnippet
import kotlinx.coroutines.flow.Flow

interface SnippetStore {
    fun observeSnippets(query: String = ""): Flow<List<SavedSnippet>>
    suspend fun getAll(): List<SavedSnippet>
    suspend fun findById(id: String): SavedSnippet?
    suspend fun save(snippet: SavedSnippet)
    suspend fun delete(id: String)
    suspend fun deleteAll()
    suspend fun replaceAll(snippets: List<SavedSnippet>)
    suspend fun count(): Int
    suspend fun migrateLegacyIfNeeded(): Int
}
