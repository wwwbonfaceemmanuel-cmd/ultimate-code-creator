package com.ultimate.codecreator.engine

import com.ultimate.codecreator.model.SupportedLanguage

/** Conservative whitespace helpers. Full semantic formatting is intentionally not guessed. */
object CodeFormatter {
    fun cleanWhitespace(code: String): String {
        val cleaned = code.lines().joinToString("\n") { it.trimEnd() }
        return if (cleaned.endsWith("\n")) cleaned else cleaned + "\n"
    }

    fun nextLineIndent(currentLine: String, language: SupportedLanguage): String {
        val base = currentLine.takeWhile { it == ' ' || it == '\t' }
        val trimmed = currentLine.trim()
        val unit = if (language == SupportedLanguage.PYTHON) "    " else "  "
        return when {
            language == SupportedLanguage.PYTHON && trimmed.endsWith(":") -> base + unit
            language != SupportedLanguage.PYTHON && trimmed.endsWith("{") -> base + unit
            trimmed.startsWith("}") || trimmed.startsWith(")") || trimmed.startsWith("]") ->
                if (base.length >= unit.length) base.dropLast(unit.length) else base
            else -> base
        }
    }
}
