package com.ultimate.codecreator.data

import com.ultimate.codecreator.model.SavedSnippet
import com.ultimate.codecreator.model.SupportedLanguage
import org.json.JSONArray
import java.util.UUID

object LegacySnippetImporter {
    fun parse(raw: String): List<SavedSnippet> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val language = SupportedLanguage.fromName(item.optString("language")) ?: continue
                val code = item.optString("code")
                if (code.isBlank()) continue
                add(
                    SavedSnippet(
                        id = item.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                        title = item.optString("title", "Untitled snippet").take(80),
                        language = language,
                        code = code,
                        dateAdded = item.optLong("dateAdded", System.currentTimeMillis())
                    )
                )
            }
        }
    }
}
