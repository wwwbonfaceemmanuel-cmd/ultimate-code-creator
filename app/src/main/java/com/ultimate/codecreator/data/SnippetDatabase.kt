package com.ultimate.codecreator.data

import android.content.Context
import com.ultimate.codecreator.model.SavedSnippet
import com.ultimate.codecreator.model.SupportedLanguage
import org.json.JSONArray
import org.json.JSONObject

class SnippetRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("offline_snippet_store", Context.MODE_PRIVATE)
    private val key = "snippets"

    init {
        // Seed once. Persist an empty array after deletion so defaults do not reappear.
        if (!prefs.contains(key)) persist(defaultList())
    }

    fun getAll(): List<SavedSnippet> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val language = SupportedLanguage.entries.firstOrNull { it.name == item.optString("language") } ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    add(SavedSnapshot(item, id, language))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun SavedSnapshot(item: JSONObject, id: String, language: SupportedLanguage) = SavedSnippet(
        id = id,
        title = item.optString("title", "Untitled snippet"),
        language = language,
        code = item.optString("code"),
        dateAdded = item.optLong("dateAdded", System.currentTimeMillis())
    )

    fun save(snippet: SavedSnippet) {
        val updated = getAll().filterNot { it.id == snippet.id }.toMutableList()
        updated.add(0, snippet)
        persist(updated)
    }

    fun delete(id: String) {
        persist(getAll().filterNot { it.id == id })
    }

    private fun persist(snippets: List<SavedSnippet>) {
        val array = JSONArray()
        snippets.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("language", item.language.name)
                put("code", item.code)
                put("dateAdded", item.dateAdded)
            })
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun defaultList() = listOf(
        SavedSnippet(
            title = "Offline HTML starter",
            language = SupportedLanguage.HTML_CSS,
            code = """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Saved Snippet</title></head>
                <body style="background:#0f172a;color:#f8fafc;font-family:system-ui,sans-serif;padding:24px">
                  <h1>Ultimate Code Creator</h1>
                  <p>This saved snippet works without a network connection.</p>
                </body>
                </html>
            """.trimIndent()
        )
    )
}
