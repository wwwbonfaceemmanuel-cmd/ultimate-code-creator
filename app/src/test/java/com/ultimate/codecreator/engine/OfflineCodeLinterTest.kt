package com.ultimate.codecreator.engine

import com.ultimate.codecreator.model.Severity
import com.ultimate.codecreator.model.SupportedLanguage
import org.junit.Assert.*
import org.junit.Test

class OfflineCodeLinterTest {
    private fun errors(code: String, language: SupportedLanguage) = OfflineCodeLinter.analyzeAndFix(code, language).errors.filter { it.severity == Severity.ERROR }

    @Test
    fun pythonDocstringsAndStringsDoNotCreateSyntaxErrors() {
        val code = "def greet(name):\n    \"\"\"Example { [ ( and def missing_colon\"\"\"\n    return \"hello \" + name\n"
        assertTrue(errors(code, SupportedLanguage.PYTHON).isEmpty())
    }

    @Test
    fun pythonMissingColonsHavePreciseLinesAndSafeFixes() {
        val code = "def calculate_tax(amount)\n    if amount > 1000\n        return amount * 0.2\n    return amount * 0.1\n"
        val result = OfflineCodeLinter.analyzeAndFix(code, SupportedLanguage.PYTHON)
        assertEquals(listOf(1, 2), result.errors.map { it.line })
        assertEquals(code.replace("amount)\n", "amount):\n").replace("1000\n", "1000:\n"), result.fixedCode)
        assertTrue(errors(result.fixedCode, SupportedLanguage.PYTHON).isEmpty())
    }

    @Test
    fun pythonColonRepairPreservesTrailingComments() {
        val result = OfflineCodeLinter.analyzeAndFix("def main()   # entry point\n    pass\n", SupportedLanguage.PYTHON)
        assertEquals("def main():   # entry point\n    pass\n", result.fixedCode)
    }

    @Test
    fun multilinePythonHeadersAreCheckedAtTheirLogicalEnd() {
        val valid = "async def fetch(\n    value\n) -> int:\n    return value\n"
        assertTrue(errors(valid, SupportedLanguage.PYTHON).isEmpty())
        val invalid = "def fetch(\n    value\n)\n"
        val result = OfflineCodeLinter.analyzeAndFix(invalid, SupportedLanguage.PYTHON)
        assertEquals(listOf(3), result.errors.map { it.line })
        assertEquals("def fetch(\n    value\n):\n", result.fixedCode)
    }

    @Test
    fun pythonSoftKeywordsAreNotMistakenForAssignments() {
        assertTrue(errors("match = {'x': 1}\ncase = 2\n", SupportedLanguage.PYTHON).isEmpty())
        assertTrue(errors("match value:\n    case _:\n        pass\n", SupportedLanguage.PYTHON).isEmpty())
    }

    @Test
    fun javaCommentsAndStringsHideDelimiters() {
        val code = "public class Main {\n    // { [ (\n    String value = \")]}'\";\n}\n"
        assertTrue(errors(code, SupportedLanguage.JAVA).isEmpty())
    }

    @Test
    fun possibleJavaSemicolonIsAWarningAndIsNotBlindlyInserted() {
        val code = "public class Main {\n    int result = 1\n}\n"
        val result = OfflineCodeLinter.analyzeAndFix(code, SupportedLanguage.JAVA)
        assertFalse(result.hasErrors)
        assertEquals(Severity.WARNING, result.errors.single().severity)
        assertEquals(2, result.errors.single().line)
        assertEquals(code, result.fixedCode)
    }

    @Test
    fun validJavaControlFlowAndAnnotationsAreNotSemicolonWarnings() {
        val code = "public class Main {\n    @Override\n    public String toString() {\n        if (true) return \"ok\";\n        for (int i = 0; i < 2; i++) {\n            System.out.println(i);\n        }\n        return \"done\";\n    }\n}\n"
        assertTrue(OfflineCodeLinter.analyzeAndFix(code, SupportedLanguage.JAVA).errors.isEmpty())
    }

    @Test
    fun trailingUnclosedBraceIsRepaired() {
        val code = "fun main() {\n    println(\"ok\")\n"
        val result = OfflineCodeLinter.analyzeAndFix(code, SupportedLanguage.KOTLIN)
        assertTrue(result.fixedCode.contains("println(\"ok\")}"))
        assertTrue(errors(result.fixedCode, SupportedLanguage.KOTLIN).isEmpty())
    }

    @Test
    fun mismatchedDelimitersAreNotGuessRepaired() {
        val code = "([)]"
        val result = OfflineCodeLinter.analyzeAndFix(code, SupportedLanguage.JAVASCRIPT)
        assertTrue(result.hasErrors)
        assertEquals(code, result.fixedCode)
    }

    @Test
    fun unclosedQuoteIsReportedWithoutInventingARepair() {
        val code = "x = \"hello\n"
        val result = OfflineCodeLinter.analyzeAndFix(code, SupportedLanguage.PYTHON)
        assertTrue(result.hasErrors)
        assertEquals(code, result.fixedCode)
    }

    @Test
    fun htmlVoidTagsDoNotNeedClosingTags() {
        assertTrue(errors("<img src=\"x\"><br><input type=\"text\">", SupportedLanguage.HTML_CSS).isEmpty())
    }

    @Test
    fun htmlLikeTextInsideScriptsIsNotAnHtmlTag() {
        assertTrue(errors("<script>const x = \"<div>\";</script>", SupportedLanguage.HTML_CSS).isEmpty())
    }

    @Test
    fun missingHtmlEndTagIsAppended() {
        val code = "<div>\n<p>Hello</p>\n"
        val result = OfflineCodeLinter.analyzeAndFix(code, SupportedLanguage.HTML_CSS)
        assertEquals("<div>\n<p>Hello</p></div>\n", result.fixedCode)
        assertTrue(errors(result.fixedCode, SupportedLanguage.HTML_CSS).isEmpty())
    }

    @Test
    fun mismatchedHtmlTagsAreNotGuessRepaired() {
        val code = "<div><span></div></span>"
        val result = OfflineCodeLinter.analyzeAndFix(code, SupportedLanguage.HTML_CSS)
        assertTrue(result.hasErrors)
        assertEquals(code, result.fixedCode)
    }

    @Test
    fun embeddedCssBlockErrorsKeepOriginalLineNumbers() {
        val code = "<style>\nbody {\n    color: red;\n</style>"
        val result = OfflineCodeLinter.analyzeAndFix(code, SupportedLanguage.HTML_CSS)
        assertTrue(result.errors.any { it.line == 2 && it.description.contains("Unclosed delimiter") })
        assertTrue(errors(result.fixedCode, SupportedLanguage.HTML_CSS).isEmpty())
    }

    @Test
    fun sqlCommentsAndStringsHideDelimiters() {
        assertTrue(errors("-- { [\nSELECT ')}' AS value;", SupportedLanguage.SQL).isEmpty())
    }

    @Test
    fun rustLifetimesAndCharacterLiteralsAreValid() {
        val code = "fn pick<'a>(value: &'a str) -> &'a str { value }\nfn main() { let c = '\\n'; println!(\"{}\", pick(\"ok\")); }"
        assertTrue(errors(code, SupportedLanguage.RUST).isEmpty())
    }

    @Test
    fun javascriptTemplateLiteralsMayContainDelimitersAndNewlines() {
        assertTrue(errors("const text = `hello { [ (\nworld`;", SupportedLanguage.JAVASCRIPT).isEmpty())
    }

    @Test
    fun goRawStringsHideDelimiters() {
        assertTrue(errors("package main\nvar text = `{ [ (`\n", SupportedLanguage.GO).isEmpty())
    }

    @Test
    fun emptyAndOversizedInputsAreHandled() {
        assertFalse(OfflineCodeLinter.analyzeAndFix("", SupportedLanguage.PYTHON).hasErrors)
        assertThrows(IllegalArgumentException::class.java) { OfflineCodeLinter.analyzeAndFix("x".repeat(OfflineCodeLinter.MAX_CODE_LENGTH + 1), SupportedLanguage.PYTHON) }
    }
}
