package com.ultimate.codecreator.engine

import com.ultimate.codecreator.model.SupportedLanguage
import org.junit.Assert.*
import org.junit.Test

class ProfessionalEngineTest {
    @Test fun catalogHasUniqueIdsAndMetadata() {
        val catalog = OfflineCodeSynthesizer.catalog
        assertTrue(catalog.isNotEmpty())
        assertEquals(catalog.size, catalog.map { it.id }.distinct().size)
        val rest = catalog.first { it.title == "REST API example" }
        assertTrue(rest.dependencies.getValue(SupportedLanguage.PYTHON).contains("fastapi"))
        val lru = catalog.first { it.title == "LRU cache" }
        assertTrue(lru.parameters.any { it.key == "capacity" })
    }

    @Test fun lruCapacityParameterIsAppliedAndValidated() {
        val result = OfflineCodeSynthesizer.synthesize("LRU cache capacity=10", SupportedLanguage.PYTHON)
        assertTrue(result.code.contains("LRUCache(10)"))
        assertEquals("10", result.appliedParameters["capacity"])
        assertThrows(IllegalArgumentException::class.java) { OfflineCodeSynthesizer.synthesize("LRU cache capacity=0", SupportedLanguage.PYTHON) }
    }

    @Test fun highlighterProducesBoundedSpans() {
        val code = "# comment\ndef greet():\n    return \"hello\" + 42\n"
        val spans = SyntaxHighlighter.tokenize(code, SupportedLanguage.PYTHON)
        assertTrue(spans.any { it.type == TokenType.COMMENT })
        assertTrue(spans.any { it.type == TokenType.STRING })
        assertTrue(spans.any { it.type == TokenType.KEYWORD })
        assertTrue(spans.any { it.type == TokenType.NUMBER })
        spans.forEach { assertTrue(it.start >= 0 && it.end <= code.length && it.start <= it.end) }
    }

    @Test fun diffShowsAddedAndRemovedLines() {
        val diff = DiffEngine.diff("a\nb\n", "a\nc\n")
        assertTrue(diff.any { it.type == DiffType.REMOVED && it.text == "b" })
        assertTrue(diff.any { it.type == DiffType.ADDED && it.text == "c" })
    }

    @Test fun formatterSuggestsSafeNewlineIndent() {
        assertEquals("    ", CodeFormatter.nextLineIndent("def main():", SupportedLanguage.PYTHON))
        assertEquals("  ", CodeFormatter.nextLineIndent("int main() {", SupportedLanguage.CPP))
        assertEquals("line\n", CodeFormatter.cleanWhitespace("line   \n"))
    }
}
