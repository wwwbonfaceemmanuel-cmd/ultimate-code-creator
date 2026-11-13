package com.ultimate.codecreator.engine.parser

import com.ultimate.codecreator.engine.OfflineCodeLinter
import com.ultimate.codecreator.model.CodeError
import com.ultimate.codecreator.model.SupportedLanguage

/**
 * Parser abstraction for future native backends. The current backend is the
 * heuristic structural checker; a Tree-sitter backend can implement the same
 * contract without changing screens or the ViewModel.
 */
interface SyntaxParser {
    val isFullAst: Boolean
    fun parse(code: String, language: SupportedLanguage): ParseResult
}

data class ParseResult(val errors: List<CodeError>, val isFullAst: Boolean)

object HeuristicSyntaxParser : SyntaxParser {
    override val isFullAst: Boolean = false
    override fun parse(code: String, language: SupportedLanguage): ParseResult {
        val result = OfflineCodeLinter.analyzeAndFix(code, language)
        return ParseResult(result.errors, isFullAst = false)
    }
}
