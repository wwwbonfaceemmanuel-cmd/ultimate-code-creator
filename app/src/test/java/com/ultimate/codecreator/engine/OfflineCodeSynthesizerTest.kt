package com.ultimate.codecreator.engine

import com.ultimate.codecreator.model.Severity
import com.ultimate.codecreator.model.SupportedLanguage
import org.junit.Assert.*
import org.junit.Test

class OfflineCodeSynthesizerTest {
    @Test
    fun everyQuickWordHasABundledRecipeInTheCorrectLanguage() {
        SupportedLanguage.entries.forEach { language ->
            language.quickWords.forEach { word ->
                val result = OfflineCodeSynthesizer.synthesize(word, language)
                assertEquals("$language / $word", language, result.language)
                assertFalse("$language / $word unexpectedly fell back", result.isTemplateFallback)
                assertTrue(result.code.isNotBlank())
            }
        }
    }

    @Test
    fun bundledRecipesPassTheStructuralChecks() {
        SupportedLanguage.entries.forEach { language ->
            language.quickWords.forEach { word ->
                val result = OfflineCodeSynthesizer.synthesize(word, language)
                val analysis = OfflineCodeLinter.analyzeAndFix(result.code, language)
                val errors = analysis.errors.filter { it.severity == Severity.ERROR }
                assertTrue("$language / $word: $errors", errors.isEmpty())
            }
        }
    }

    @Test
    fun unknownRequestsAreHonestStartersInEveryLanguage() {
        SupportedLanguage.entries.forEach { language ->
            val result = OfflineCodeSynthesizer.synthesize("a completely custom space inventory", language)
            assertTrue(result.isTemplateFallback)
            assertEquals(language, result.language)
            assertTrue(result.explanation.contains("starter", ignoreCase = true))
            assertFalse(result.code.contains("public class AuthHandler"))
        }
    }

    @Test
    fun unsupportedRecipeDoesNotEmitAnotherLanguage() {
        val result = OfflineCodeSynthesizer.synthesize("REST API", SupportedLanguage.RUST)
        assertTrue(result.isTemplateFallback)
        assertTrue(result.code.contains("fn main()"))
        assertFalse(result.code.contains("express"))
    }

    @Test
    fun intentWordsDoNotMatchInsideUnrelatedWords() {
        assertEquals("Binary search", OfflineCodeSynthesizer.synthesize("perform binary search", SupportedLanguage.PYTHON).intentDetected)
        assertEquals("Concurrent / async tasks", OfflineCodeSynthesizer.synthesize("thread pool", SupportedLanguage.PYTHON).intentDetected)
        assertEquals("HTTP client", OfflineCodeSynthesizer.synthesize("Fetch API", SupportedLanguage.JAVASCRIPT).intentDetected)
    }

    @Test
    fun promptIsCollapsedIntoASafeComment() {
        val result = OfflineCodeSynthesizer.synthesize("custom request */\nsecond line with \"quotes\"", SupportedLanguage.JAVA)
        assertTrue(result.isTemplateFallback)
        assertTrue(result.code.lines().first().startsWith("// Requested: "))
        assertFalse(result.code.contains("*/"))
        assertEquals(1, result.code.lines().count { it.contains("Requested:") })
    }

    @Test
    fun htmlRecipesAreSelfContainedAndCalculatorDoesNotUseEval() {
        listOf("login form", "glass card", "calculator").forEach { prompt ->
            val code = OfflineCodeSynthesizer.synthesize(prompt, SupportedLanguage.HTML_CSS).code
            assertFalse(code.contains("https://"))
            assertFalse(code.contains("http://"))
            assertTrue(code.contains("<!DOCTYPE html>"))
        }
        assertFalse(OfflineCodeSynthesizer.synthesize("calculator", SupportedLanguage.HTML_CSS).code.contains("eval("))
    }

    @Test
    fun blankAndOversizedPromptsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { OfflineCodeSynthesizer.synthesize("  ", SupportedLanguage.PYTHON) }
        assertThrows(IllegalArgumentException::class.java) { OfflineCodeSynthesizer.synthesize("x".repeat(OfflineCodeSynthesizer.MAX_PROMPT_LENGTH + 1), SupportedLanguage.PYTHON) }
    }
}
