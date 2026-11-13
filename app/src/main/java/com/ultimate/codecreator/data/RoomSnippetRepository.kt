package com.ultimate.codecreator.data

import android.content.SharedPreferences
import com.ultimate.codecreator.data.local.SnippetDao
import com.ultimate.codecreator.data.local.SnippetEntity
import com.ultimate.codecreator.model.SavedSnippet
import com.ultimate.codecreator.model.SupportedLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomSnippetRepository(
    private val dao: SnippetDao,
    private val legacyPrefs: SharedPreferences? = null
) : SnippetStore {

    override fun observeSnippets(query: String): Flow<List<SavedSnippet>> {
        val source = if (query.isBlank()) dao.observeAll() else dao.observeSearch(query.trim())
        return source.map { entities -> entities.mapNotNull { it.toModel() } }
    }

    override suspend fun getAll(): List<SavedSnippet> = dao.observeAll().first().mapNotNull { it.toModel() }

    override suspend fun findById(id: String): SavedSnippet? = dao.findById(id)?.toModel()

    override suspend fun save(snippet: SavedSnippet) {
        dao.upsert(snippet.toEntity())
    }

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun deleteAll() = dao.deleteAll()

    override suspend fun replaceAll(snippets: List<SavedSnippet>) {
        dao.replaceAll(snippets.map { it.toEntity() })
    }

    override suspend fun count(): Int = dao.count()

    override suspend fun migrateLegacyIfNeeded(): Int {
        val prefs = legacyPrefs ?: return 0
        val raw = prefs.getString(LEGACY_KEY, null) ?: return 0
        val legacy = try {
            LegacySnippetImporter.parse(raw)
        } catch (_: Exception) {
            return 0
        }
        var imported = 0
        legacy.forEach { snippet ->
            if (dao.findById(snippet.id) == null) {
                dao.upsert(snippet.toEntity())
                imported++
            }
        }
        prefs.edit().remove(LEGACY_KEY).apply()
        return imported
    }

    private fun SnippetEntity.toModel(): SavedSnippet? {
        val language = SupportedLanguage.fromName(language) ?: return null
        return SavedSnippet(
            id = id,
            title = title,
            language = language,
            code = code,
            dateAdded = createdAt,
            updatedAt = updatedAt,
            tags = tags.split(TAG_SEPARATOR).filter { it.isNotBlank() },
            favorite = favorite
        )
    }

    private fun SavedSnippet.toEntity() = SnippetEntity(
        id = id,
        title = title.take(80),
        language = language.name,
        code = code,
        tags = tags.take(10).joinToString(TAG_SEPARATOR),
        favorite = favorite,
        createdAt = dateAdded,
        updatedAt = updatedAt
    )

    companion object {
        private const val LEGACY_KEY = "snippets"
        private const val TAG_SEPARATOR = "\u001F"
    }
}
