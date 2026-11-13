package com.ultimate.codecreator.engine

import com.ultimate.codecreator.model.SupportedLanguage

enum class TokenType { COMMENT, STRING, KEYWORD, NUMBER, ANNOTATION, TAG }

data class HighlightSpan(val start: Int, val end: Int, val type: TokenType)

/**
 * Lightweight tokenizer for editor colouring. It is intentionally separate from
 * the linter and does not claim to be a full parser. A Tree-sitter backend can
 * replace this tokenizer later without changing the editor UI contract.
 */
object SyntaxHighlighter {
    fun tokenize(code: String, language: SupportedLanguage): List<HighlightSpan> {
        if (code.isEmpty()) return emptyList()
        val spans = mutableListOf<HighlightSpan>()
        val masked = CharArray(code.length) { ' ' }
        var index = 0
        val cLike = language in setOf(
            SupportedLanguage.JAVA, SupportedLanguage.KOTLIN, SupportedLanguage.JAVASCRIPT,
            SupportedLanguage.TYPESCRIPT, SupportedLanguage.CPP, SupportedLanguage.GO,
            SupportedLanguage.RUST, SupportedLanguage.C_SHARP, SupportedLanguage.SQL
        )
        val lineToken = when (language) {
            SupportedLanguage.PYTHON -> "#"
            SupportedLanguage.SQL -> "--"
            SupportedLanguage.HTML_CSS -> null
            else -> if (cLike) "//" else null
        }
        val tripleQuotes = language == SupportedLanguage.PYTHON || language == SupportedLanguage.KOTLIN

        fun blank(start: Int, end: Int) {
            for (i in start until end.coerceAtMost(code.length)) masked[i] = ' '
        }

        while (index < code.length) {
            val ch = code[index]
            if (language == SupportedLanguage.HTML_CSS && code.startsWith("<!--", index)) {
                val end = code.indexOf("-->", index + 4).let { if (it < 0) code.length else it + 3 }
                spans.add(HighlightSpan(index, end, TokenType.COMMENT))
                blank(index, end)
                index = end
                continue
            }
            if (tripleQuotes && code.startsWith("\"\"\"", index)) {
                val end = code.indexOf("\"\"\"", index + 3).let { if (it < 0) code.length else it + 3 }
                spans.add(HighlightSpan(index, end, TokenType.STRING))
                blank(index, end)
                index = end
                continue
            }
            if (lineToken != null && code.startsWith(lineToken, index)) {
                val end = code.indexOf('\n', index).let { if (it < 0) code.length else it }
                spans.add(HighlightSpan(index, end, TokenType.COMMENT))
                blank(index, end)
                index = end
                continue
            }
            if (cLike && code.startsWith("/*", index)) {
                val end = code.indexOf("*/", index + 2).let { if (it < 0) code.length else it + 2 }
                spans.add(HighlightSpan(index, end, TokenType.COMMENT))
                blank(index, end)
                index = end
                continue
            }
            if (language == SupportedLanguage.RUST && ch == '\'' &&
                !(index + 1 < code.length && code[index + 1] == '\\') &&
                !(index + 2 < code.length && code[index + 2] == '\'')) {
                masked[index] = ' '
                index++
                continue
            }
            val quotes = when (language) {
                SupportedLanguage.JAVASCRIPT, SupportedLanguage.TYPESCRIPT, SupportedLanguage.GO -> setOf('\'', '"', '`')
                else -> setOf('\'', '"')
            }
            if (ch in quotes) {
                var end = index + 1
                while (end < code.length) {
                    if (code[end] == '\\') { end += 2; continue }
                    if (code[end] == ch) { end++; break }
                    if (code[end] == '\n' && ch != '`' && language != SupportedLanguage.SQL) break
                    end++
                }
                end = end.coerceAtMost(code.length)
                spans.add(HighlightSpan(index, end, TokenType.STRING))
                blank(index, end)
                index = end
                continue
            }
            masked[index] = ch
            index++
        }

        val codeOnly = masked.concatToString()
        val keywords = keywords(language)
        if (keywords.isNotEmpty()) {
            Regex("\\b(${keywords.joinToString("|")})\\b").findAll(codeOnly).forEach {
                spans.add(HighlightSpan(it.range.first, it.range.last + 1, TokenType.KEYWORD))
            }
        }
        Regex("(?<![A-Za-z0-9_])\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?").findAll(codeOnly).forEach {
            spans.add(HighlightSpan(it.range.first, it.range.last + 1, TokenType.NUMBER))
        }
        Regex("(?m)^\\s*@[A-Za-z_][A-Za-z0-9_]*").findAll(codeOnly).forEach {
            spans.add(HighlightSpan(it.range.first, it.range.last + 1, TokenType.ANNOTATION))
        }
        if (language == SupportedLanguage.CPP) {
            Regex("(?m)^\\s*#\\s*[A-Za-z_]+").findAll(codeOnly).forEach {
                spans.add(HighlightSpan(it.range.first, it.range.last + 1, TokenType.ANNOTATION))
            }
        }
        if (language == SupportedLanguage.HTML_CSS) {
            Regex("</?[A-Za-z][^>]*>").findAll(codeOnly).forEach {
                spans.add(HighlightSpan(it.range.first, it.range.last + 1, TokenType.TAG))
            }
        }
        return spans.sortedBy { it.start }
    }

    private fun keywords(language: SupportedLanguage): List<String> = when (language) {
        SupportedLanguage.PYTHON -> listOf("def", "class", "if", "elif", "else", "for", "while", "return", "import", "from", "as", "with", "try", "except", "finally", "raise", "lambda", "yield", "async", "await", "pass", "break", "continue", "True", "False", "None", "and", "or", "not", "in", "is")
        SupportedLanguage.JAVA -> listOf("public", "private", "protected", "class", "interface", "enum", "record", "static", "final", "void", "int", "long", "double", "float", "boolean", "char", "new", "return", "if", "else", "for", "while", "do", "switch", "case", "break", "continue", "try", "catch", "finally", "throw", "throws", "import", "package", "extends", "implements", "this", "super", "null", "true", "false")
        SupportedLanguage.KOTLIN -> listOf("fun", "val", "var", "class", "object", "interface", "data", "sealed", "enum", "when", "if", "else", "for", "while", "return", "import", "package", "suspend", "override", "private", "public", "internal", "protected", "companion", "init", "constructor", "null", "true", "false", "is", "in", "as", "by", "try", "catch", "finally", "throw")
        SupportedLanguage.JAVASCRIPT, SupportedLanguage.TYPESCRIPT -> listOf("function", "const", "let", "var", "class", "extends", "new", "return", "if", "else", "for", "while", "do", "switch", "case", "break", "continue", "try", "catch", "finally", "throw", "async", "await", "import", "export", "from", "default", "typeof", "instanceof", "null", "undefined", "true", "false", "interface", "type", "readonly", "implements", "private", "public", "protected")
        SupportedLanguage.CPP -> listOf("include", "define", "int", "long", "double", "float", "bool", "char", "auto", "const", "constexpr", "static", "class", "struct", "enum", "template", "typename", "public", "private", "protected", "virtual", "override", "new", "delete", "return", "if", "else", "for", "while", "do", "switch", "case", "break", "continue", "try", "catch", "throw", "namespace", "using", "nullptr", "true", "false")
        SupportedLanguage.SQL -> listOf("SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "INDEX", "PRIMARY", "KEY", "NOT", "NULL", "UNIQUE", "DEFAULT", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER", "ON", "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET", "AS", "AND", "OR", "WITH", "OVER", "PARTITION", "BEGIN", "COMMIT", "ROLLBACK", "BOOLEAN", "INTEGER", "VARCHAR", "TIMESTAMP", "DECIMAL", "DATE")
        SupportedLanguage.GO -> listOf("package", "import", "func", "var", "const", "type", "struct", "interface", "map", "chan", "go", "defer", "select", "case", "switch", "if", "else", "for", "range", "return", "break", "continue", "true", "false", "nil", "string", "int", "int64", "uint64", "byte", "error", "bool")
        SupportedLanguage.RUST -> listOf("fn", "let", "mut", "const", "static", "struct", "enum", "trait", "impl", "pub", "mod", "use", "crate", "self", "super", "where", "for", "while", "loop", "if", "else", "match", "return", "break", "continue", "move", "ref", "as", "in", "true", "false", "Some", "None", "Ok", "Err", "usize", "u32", "u64", "i32", "i64", "bool", "str", "String", "Vec")
        SupportedLanguage.C_SHARP -> listOf("using", "namespace", "class", "struct", "interface", "enum", "record", "public", "private", "protected", "internal", "static", "readonly", "const", "var", "new", "return", "if", "else", "for", "foreach", "while", "do", "switch", "case", "break", "continue", "try", "catch", "finally", "throw", "async", "await", "task", "void", "int", "long", "double", "float", "bool", "string", "null", "true", "false", "out", "ref", "in", "is", "as")
        SupportedLanguage.HTML_CSS -> listOf("html", "head", "body", "script", "style", "div", "span", "form", "input", "button", "label", "main", "section", "article", "header", "footer", "nav", "title", "meta", "link", "img", "table", "tr", "td", "th", "ul", "ol", "li", "p", "h1", "h2", "h3", "a")
    }.let { words ->
        if (language == SupportedLanguage.SQL) words else words.map { it.lowercase() }.distinct()
    }
}
