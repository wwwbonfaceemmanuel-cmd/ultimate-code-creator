package com.ultimate.codecreator.data

import com.ultimate.codecreator.model.SavedSnippet
import com.ultimate.codecreator.model.SupportedLanguage
import org.junit.Assert.*
import org.junit.Test

class SnippetPackCodecTest {
    @Test fun roundTripPreservesMetadata() {
        val snippet = SavedSnippet(title = "Example", language = SupportedLanguage.KOTLIN, code = "fun main() {}", tags = listOf("demo", "kotlin"), favorite = true)
        val decoded = SnippetPackCodec.decode(SnippetPackCodec.encode(listOf(snippet), "0.2.0"))
        assertEquals(1, decoded.size)
        assertEquals(snippet.id, decoded[0].id)
        assertEquals(snippet.title, decoded[0].title)
        assertEquals(snippet.language, decoded[0].language)
        assertEquals(snippet.code, decoded[0].code)
        assertEquals(snippet.tags, decoded[0].tags)
        assertTrue(decoded[0].favorite)
    }

    @Test fun invalidJsonIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { SnippetPackCodec.decode("not json") }
    }

    @Test fun unsupportedLanguageIsRejected() {
        val json = """{"formatVersion":1,"snippets":[{"title":"x","language":"COBOL","code":"x"}]}"""
        assertThrows(IllegalArgumentException::class.java) { SnippetPackCodec.decode(json) }
    }

    @Test fun oversizedSnippetIsRejected() {
        val snippet = SavedSnippet(title = "Large", language = SupportedLanguage.PYTHON, code = "x".repeat(SnippetPackCodec.MAX_CODE_CHARACTERS + 1))
        assertThrows(IllegalArgumentException::class.java) { SnippetPackCodec.encode(listOf(snippet), "test") }
    }

    @Test fun legacyArrayImports() {
        val raw = """[{"id":"1","title":"Legacy","language":"PYTHON","code":"print(1)","dateAdded":123}]"""
        val imported = LegacySnippetImporter.parse(raw)
        assertEquals(1, imported.size)
        assertEquals(SupportedLanguage.PYTHON, imported[0].language)
        assertEquals(123L, imported[0].dateAdded)
    }
}
