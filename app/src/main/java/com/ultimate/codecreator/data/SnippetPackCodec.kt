package com.ultimate.codecreator.data

import com.ultimate.codecreator.model.SavedSnippet
import com.ultimate.codecreator.model.SupportedLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Versioned, validated local backup format. No network or cloud dependency. */
object SnippetPackCodec {
    const val FORMAT_VERSION = 1
    const val MAX_PACK_CHARACTERS = 5_000_000
    const val MAX_SNIPPETS = 1_000
    const val MAX_CODE_CHARACTERS = 200_000

    fun encode(snippets: List<SavedSnippet>, appVersion: String): String {
        require(snippets.size <= MAX_SNIPPETS) { "Too many snippets to export in one pack." }
        val array = JSONArray()
        snippets.forEach { snippet ->
            require(snippet.code.length <= MAX_CODE_CHARACTERS) { "Snippet '${snippet.title}' is too large to export." }
            array.put(JSONObject().apply {
                put("id", snippet.id)
                put("title", snippet.title)
                put("language", snippet.language.name)
                put("code", snippet.code)
                put("dateAdded", snippet.dateAdded)
                put("updatedAt", snippet.updatedAt)
                put("favorite", snippet.favorite)
                put("tags", JSONArray(snippet.tags))
            })
        }
        return JSONObject().apply {
            put("formatVersion", FORMAT_VERSION)
            put("appVersion", appVersion)
            put("exportedAt", System.currentTimeMillis())
            put("snippets", array)
        }.toString(2)
    }

    fun decode(json: String): List<SavedSnippet> {
        require(json.length <= MAX_PACK_CHARACTERS) { "Snippet pack is too large." }
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            throw IllegalArgumentException("This file is not a valid snippet pack.")
        }
        require(root.optInt("formatVersion", -1) == FORMAT_VERSION) { "Unsupported snippet pack version." }
        val array = root.optJSONArray("snippets") ?: throw IllegalArgumentException("Snippet pack has no snippets array.")
        require(array.length() <= MAX_SNIPPETS) { "Snippet pack contains too many snippets." }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val language = SupportedLanguage.fromName(item.optString("language"))
                    ?: throw IllegalArgumentException("Snippet pack contains an unsupported language at entry ${index + 1}.")
                val code = item.optString("code")
                require(code.length <= MAX_CODE_CHARACTERS) { "Snippet at entry ${index + 1} is too large." }
                val tags = item.optJSONArray("tags")?.let { tagArray ->
                    buildList {
                        for (tagIndex in 0 until tagArray.length()) {
                            val tag = tagArray.optString(tagIndex).trim()
                            if (tag.isNotBlank()) add(tag.take(30))
                        }
                    }
                } ?: emptyList()
                add(
                    SavedSnippet(
                        id = item.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                        title = item.optString("title", "Untitled snippet").trim().ifEmpty { "Untitled snippet" }.take(80),
                        language = language,
                        code = code,
                        dateAdded = item.optLong("dateAdded", System.currentTimeMillis()),
                        updatedAt = item.optLong("updatedAt", item.optLong("dateAdded", System.currentTimeMillis())),
                        tags = tags.take(10),
                        favorite = item.optBoolean("favorite", false)
                    )
                )
            }
        }
    }
}
