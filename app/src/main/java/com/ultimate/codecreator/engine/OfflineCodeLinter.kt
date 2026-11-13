package com.ultimate.codecreator.engine

import com.ultimate.codecreator.model.CodeError
import com.ultimate.codecreator.model.DebugResult
import com.ultimate.codecreator.model.Severity
import com.ultimate.codecreator.model.SupportedLanguage
import java.util.regex.Pattern

/**
 * Lightweight, comment/string-aware structural checks, not a full AST parser or
 * language server. Repairs are intentionally conservative: missing Python header
 * colons, unambiguous trailing delimiters, and missing HTML end tags. Possible
 * semicolon issues are warnings and are never blindly inserted.
 */
object OfflineCodeLinter {
    const val MAX_CODE_LENGTH = 200_000

    private val openings = mapOf('(' to ')', '[' to ']', '{' to '}')
    private val closings = openings.entries.associate { (key, value) -> value to key }
    private val voidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
    private val pythonKeywords = setOf("def", "if", "elif", "else", "for", "while", "class", "try", "except", "finally", "with", "match", "case")
    private val tagRegex = Regex("""<!--[\s\S]*?-->|<![^>]*>|<(/?)([a-zA-Z][\w:-]*)(?:"[^"]*"|'[^']*'|[^'">])*>""")

    fun analyzeAndFix(code: String, language: SupportedLanguage): DebugResult {
        require(code.length <= MAX_CODE_LENGTH) { "Keep code under $MAX_CODE_LENGTH characters for mobile-safe analysis." }
        if (code.isBlank()) return DebugResult(false, emptyList(), code, "Enter code to analyze.")
        val result = if (language == SupportedLanguage.HTML_CSS) analyzeHtml(code) else analyzeFragment(code, language)
        val errorCount = result.errors.count { it.severity == Severity.ERROR }
        val warningCount = result.errors.count { it.severity == Severity.WARNING }
        val summary = when {
            result.errors.isEmpty() -> "No structural issues found by the offline heuristic checks."
            result.fixedCode != code -> "$errorCount error(s), $warningCount warning(s). Conservative repairs prepared; review the result."
            else -> "$errorCount error(s), $warningCount warning(s). Review the listed issues; no blind automatic repair was applied."
        }
        return DebugResult(errorCount > 0, result.errors.sortedWith(compareBy({ it.line }, { it.severity })), result.fixedCode, summary)
    }

    private data class FragmentResult(val errors: List<CodeError>, val fixedCode: String)
    private data class MaskResult(val text: String, val errors: List<CodeError>)
    private data class DelimiterResult(val errors: List<CodeError>, val missingClosers: String, val mismatch: Boolean)
    private data class PythonResult(val errors: List<CodeError>, val colonLines: Set<Int>)
    private data class HtmlTag(val name: String, val line: Int)
    private data class RawFragment(val start: Int, val end: Int, val content: String, val lineOffset: Int, val isCss: Boolean)
    private data class TagResult(val errors: List<CodeError>, val missingTags: List<String>, val mismatch: Boolean, val fragments: List<RawFragment>)

    private fun analyzeFragment(code: String, language: SupportedLanguage, lineOffset: Int = 0, isCss: Boolean = false): FragmentResult {
        val maskLanguage = if (isCss) SupportedLanguage.CPP else language
        val masked = maskCommentsAndStrings(code, maskLanguage, lineOffset)
        val delimiters = checkDelimiters(masked.text, lineOffset)
        val python = if (language == SupportedLanguage.PYTHON && !isCss) checkPythonHeaders(masked.text, lineOffset) else PythonResult(emptyList(), emptySet())
        val errors = masked.errors + delimiters.errors + python.errors +
            if (!isCss && language in setOf(SupportedLanguage.JAVA, SupportedLanguage.CPP, SupportedLanguage.C_SHARP)) possibleSemicolons(masked.text, lineOffset) else emptyList()
        val lines = code.lines().toMutableList()
        val maskedLines = masked.text.lines()
        for (localLine in python.colonLines) {
            val index = localLine - 1
            if (index !in lines.indices) continue
            val insertion = maskedLines[index].trimEnd().length.coerceAtMost(lines[index].length)
            lines[index] = lines[index].substring(0, insertion) + ":" + lines[index].substring(insertion)
        }
        var fixed = lines.joinToString("\n")
        if (masked.errors.isEmpty() && !delimiters.mismatch && delimiters.missingClosers.isNotEmpty()) {
            fixed = insertBeforeTrailingWhitespace(fixed, delimiters.missingClosers)
        }
        return FragmentResult(errors, fixed)
    }

    private fun maskCommentsAndStrings(code: String, language: SupportedLanguage, lineOffset: Int): MaskResult {
        val output = CharArray(code.length) { ' ' }
        val errors = mutableListOf<CodeError>()
        val cLike = language in setOf(SupportedLanguage.JAVA, SupportedLanguage.KOTLIN, SupportedLanguage.JAVASCRIPT, SupportedLanguage.TYPESCRIPT, SupportedLanguage.CPP, SupportedLanguage.GO, SupportedLanguage.RUST, SupportedLanguage.C_SHARP, SupportedLanguage.SQL)
        val lineToken = when (language) {
            SupportedLanguage.PYTHON -> "#"
            SupportedLanguage.SQL -> "--"
            else -> if (cLike) "//" else null
        }
        val tripleQuotes = language == SupportedLanguage.PYTHON || language == SupportedLanguage.KOTLIN
        var index = 0
        var line = 1 + lineOffset

        fun blankUntil(requestedEnd: Int) {
            val end = requestedEnd.coerceAtMost(code.length)
            while (index < end) {
                if (code[index] == '\n') {
                    output[index] = '\n'
                    line++
                }
                index++
            }
        }
        fun unclosed(kind: String, openingLine: Int) {
            errors.add(CodeError(openingLine, Severity.ERROR, "Unclosed $kind", "Close the $kind before continuing."))
        }

        while (index < code.length) {
            val ch = code[index]
            if (tripleQuotes && code.startsWith("\"\"\"", index)) {
                val openingLine = line
                blankUntil(index + 3)
                var closed = false
                while (index < code.length) {
                    if (code.startsWith("\"\"\"", index)) {
                        blankUntil(index + 3)
                        closed = true
                        break
                    }
                    blankUntil(index + if (language == SupportedLanguage.PYTHON && code[index] == '\\') 2 else 1)
                }
                if (!closed) unclosed("triple-quoted string", openingLine)
                continue
            }
            if (lineToken != null && code.startsWith(lineToken, index)) {
                val end = code.indexOf('\n', index).let { if (it < 0) code.length else it }
                blankUntil(end)
                continue
            }
            if (cLike && code.startsWith("/*", index)) {
                val openingLine = line
                val end = code.indexOf("*/", index + 2).let { if (it < 0) code.length else it + 2 }
                val closed = end <= code.length && code.startsWith("*/", end - 2)
                blankUntil(end)
                if (!closed) unclosed("block comment", openingLine)
                continue
            }
            // Rust lifetimes such as 'a are not character literals.
            if (language == SupportedLanguage.RUST && ch == '\'' &&
                !(index + 1 < code.length && code[index + 1] == '\\') &&
                !(index + 2 < code.length && code[index + 2] == '\'')) {
                blankUntil(index + 1)
                continue
            }
            val quoteChars = when (language) {
                SupportedLanguage.JAVASCRIPT, SupportedLanguage.TYPESCRIPT, SupportedLanguage.GO -> setOf('\'', '"', '`')
                SupportedLanguage.PYTHON, SupportedLanguage.SQL -> setOf('\'', '"')
                else -> setOf('\'', '"')
            }
            if (ch in quoteChars) {
                val openingLine = line
                val multiline = ch == '`' || language == SupportedLanguage.SQL
                blankUntil(index + 1)
                var closed = false
                var newlineReported = false
                while (index < code.length) {
                    if (code[index] == '\n' && !multiline) {
                        unclosed("string", openingLine)
                        newlineReported = true
                        break
                    }
                    if (code[index] == '\\') {
                        blankUntil((index + 2).coerceAtMost(code.length))
                    } else if (code[index] == ch) {
                        blankUntil(index + 1)
                        closed = true
                        break
                    } else {
                        blankUntil(index + 1)
                    }
                }
                if (!closed && !newlineReported) unclosed("string", openingLine)
                continue
            }
            output[index] = ch
            if (ch == '\n') line++
            index++
        }
        return MaskResult(output.concatToString(), errors)
    }

    private fun checkDelimiters(masked: String, lineOffset: Int): DelimiterResult {
        val errors = mutableListOf<CodeError>()
        val stack = mutableListOf<Pair<Char, Int>>()
        var line = 1 + lineOffset
        var mismatch = false
        for (ch in masked) {
            when {
                ch == '\n' -> line++
                ch in openings -> stack.add(ch to line)
                ch in closings -> {
                    val expectedOpening = closings.getValue(ch)
                    if (stack.isEmpty() || stack.last().first != expectedOpening) {
                        mismatch = true
                        val expected = stack.lastOrNull()?.let { openings.getValue(it.first) }
                        errors.add(CodeError(line, Severity.ERROR,
                            if (expected == null) "Unmatched closing '$ch'" else "Mismatched delimiter: expected '$expected' before '$ch'",
                            "Review the opening and closing delimiters; mismatched blocks are not auto-rewritten."))
                    }
                    if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                }
            }
        }
        if (!mismatch) stack.forEach { (ch, openingLine) ->
            errors.add(CodeError(openingLine, Severity.ERROR, "Unclosed delimiter '$ch'", "Add '${openings.getValue(ch)}' to close this delimiter."))
        }
        val missing = if (mismatch) "" else stack.asReversed().joinToString("") { openings.getValue(it.first).toString() }
        return DelimiterResult(errors, missing, mismatch)
    }

    private fun checkPythonHeaders(masked: String, lineOffset: Int): PythonResult {
        val errors = mutableListOf<CodeError>()
        val colonLines = mutableSetOf<Int>()
        var depth = 0
        var inHeader = false
        var colonFound = false
        var continuation = false
        masked.lines().forEachIndexed { index, text ->
            val line = index + 1 + lineOffset
            val trimmed = text.trimStart()
            if (depth == 0 && !continuation && !inHeader && trimmed.isNotEmpty()) {
                val match = Regex("""^(?:(async)\s+)?([a-z]+)\b""").find(trimmed)
                val keyword = match?.groupValues?.get(2)
                val asyncPrefix = match?.groupValues?.get(1)?.isNotEmpty() == true
                val softKeywordAssignment = Regex("""^(match|case)\s*[=(]""").containsMatchIn(trimmed)
                if (keyword in pythonKeywords && !softKeywordAssignment && (!asyncPrefix || keyword in setOf("def", "for", "with"))) {
                    inHeader = true
                    colonFound = false
                }
            }
            for (ch in text) {
                when (ch) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> depth = (depth - 1).coerceAtLeast(0)
                    ':' -> if (depth == 0 && inHeader) colonFound = true
                }
            }
            continuation = text.trimEnd().endsWith("\\")
            if (inHeader && depth == 0 && !continuation) {
                if (!colonFound) {
                    errors.add(CodeError(line, Severity.ERROR, "Missing colon at the end of a Python block header", "Append ':' before any trailing comment."))
                    colonLines.add(line - lineOffset)
                }
                inHeader = false
            }
        }
        return PythonResult(errors, colonLines)
    }

    private fun possibleSemicolons(masked: String, lineOffset: Int): List<CodeError> {
        val errors = mutableListOf<CodeError>()
        var depth = 0
        val declarationOrControl = Regex("""^(if|for|while|switch|catch|else|do|try|class|interface|enum|record|public|private|protected|namespace|template|case|default)\b""")
        val completeStatement = Regex("""^(?:return\b.*|break|continue|throw\b.*|(?:[A-Za-z_$][\w$]*(?:\s*\[\s*\])?\s+)?[A-Za-z_$][\w$]*\s*(?:=|\+=|-=|\*=|/=)[^=].*|[A-Za-z_$][\w$]*\s*\([^;{}]*\))$""")
        masked.lines().forEachIndexed { index, text ->
            val startDepth = depth
            depth += text.count { it in openings } - text.count { it in closings }
            val trimmed = text.trim()
            if (startDepth != 0 || depth != 0 || trimmed.isEmpty()) return@forEachIndexed
            if (declarationOrControl.containsMatchIn(trimmed)) return@forEachIndexed
            if (trimmed.last() in ";{}:,\\+-*/&|=.?([") return@forEachIndexed
            if (completeStatement.matches(trimmed)) {
                errors.add(CodeError(index + 1 + lineOffset, Severity.WARNING, "Possible missing semicolon",
                    "Add ';' if this statement is complete. Left unchanged because a continuation may be valid."))
            }
        }
        return errors
    }

    private fun analyzeHtml(code: String): FragmentResult {
        val tags = checkHtmlTags(code)
        val errors = tags.errors.toMutableList()
        var fixed = code
        for (fragment in tags.fragments.sortedByDescending { it.start }) {
            val result = analyzeFragment(fragment.content,
                if (fragment.isCss) SupportedLanguage.CPP else SupportedLanguage.JAVASCRIPT,
                fragment.lineOffset, fragment.isCss)
            errors.addAll(result.errors)
            if (result.fixedCode != fragment.content) {
                fixed = fixed.substring(0, fragment.start) + result.fixedCode + fixed.substring(fragment.end)
            }
        }
        if (!tags.mismatch && tags.missingTags.isNotEmpty()) {
            fixed = insertBeforeTrailingWhitespace(fixed, tags.missingTags.joinToString("") { "</$it>" })
        }
        return FragmentResult(errors, fixed)
    }

    private fun checkHtmlTags(code: String): TagResult {
        val errors = mutableListOf<CodeError>()
        val stack = mutableListOf<HtmlTag>()
        val fragments = mutableListOf<RawFragment>()
        val resolveLine = lineResolver(code)
        var mismatch = false
        var searchFrom = 0

        fun closeTag(name: String, line: Int) {
            if (stack.lastOrNull()?.name == name) {
                stack.removeAt(stack.lastIndex)
            } else {
                mismatch = true
                errors.add(CodeError(line, Severity.ERROR, "Unexpected or mismatched closing tag '</$name>'",
                    "Align this with the most recent open tag; ambiguous markup is not auto-rewritten."))
                if (stack.any { it.name == name }) {
                    while (stack.isNotEmpty() && stack.last().name != name) stack.removeAt(stack.lastIndex)
                    if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                }
            }
        }

        while (searchFrom <= code.length) {
            val match = tagRegex.find(code, searchFrom) ?: break
            searchFrom = match.range.last + 1
            if (match.value.startsWith("<!")) continue
            val name = match.groupValues[2].lowercase()
            val line = resolveLine(match.range.first)
            if (match.groupValues[1] == "/") {
                closeTag(name, line)
                continue
            }
            if (name in voidTags || match.value.trimEnd().endsWith("/>")) continue
            stack.add(HtmlTag(name, line))
            if (name == "script" || name == "style") {
                val closing = Regex("</\\s*${Pattern.quote(name)}\\s*>", RegexOption.IGNORE_CASE).find(code, searchFrom)
                val contentEnd = closing?.range?.first ?: code.length
                val dataScript = name == "script" && Regex("""type\s*=\s*["']?(application/json|application/ld\+json|text/template)""", RegexOption.IGNORE_CASE).containsMatchIn(match.value)
                if (!dataScript && contentEnd > searchFrom) {
                    fragments.add(RawFragment(searchFrom, contentEnd, code.substring(searchFrom, contentEnd), resolveLine(searchFrom) - 1, name == "style"))
                }
                if (closing != null) {
                    closeTag(name, resolveLine(closing.range.first))
                    searchFrom = closing.range.last + 1
                } else {
                    searchFrom = code.length + 1
                }
            }
        }
        stack.forEach { tag ->
            errors.add(CodeError(tag.line, Severity.ERROR, "Unclosed HTML tag '<${tag.name}>'", "Add '</${tag.name}>' in the correct nesting order."))
        }
        return TagResult(errors, stack.asReversed().map { it.name }, mismatch, fragments)
    }

    private fun lineResolver(code: String): (Int) -> Int {
        val starts = mutableListOf(0)
        code.forEachIndexed { index, ch -> if (ch == '\n') starts.add(index + 1) }
        return { offset ->
            val index = starts.binarySearch(offset)
            if (index >= 0) index + 1 else -index - 1
        }
    }

    private fun insertBeforeTrailingWhitespace(code: String, suffix: String): String {
        val lastContent = code.indexOfLast { !it.isWhitespace() } + 1
        return code.substring(0, lastContent) + suffix + code.substring(lastContent)
    }
}
