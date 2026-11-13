package com.ultimate.codecreator.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.ultimate.codecreator.engine.HighlightSpan
import com.ultimate.codecreator.engine.SyntaxHighlighter
import com.ultimate.codecreator.engine.TokenType
import com.ultimate.codecreator.model.SupportedLanguage
import com.ultimate.codecreator.ui.theme.*

data class CodeColors(
    val background: Color,
    val text: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val annotation: Color,
    val tag: Color
)

@Composable
fun rememberCodeColors(): CodeColors {
    val dark = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
    return if (dark) CodeColors(
        background = Color(0xFF030712), text = Color(0xFFE2E8F0),
        keyword = CodeKeywordDark, string = CodeStringDark, comment = CodeCommentDark,
        number = CodeNumberDark, annotation = CodeAnnotationDark, tag = CodeTagDark
    ) else CodeColors(
        background = Color(0xFFF1F5F9), text = Color(0xFF0F172A),
        keyword = CodeKeywordLight, string = CodeStringLight, comment = CodeCommentLight,
        number = CodeNumberLight, annotation = CodeAnnotationLight, tag = CodeTagLight
    )
}

fun highlightedCode(code: String, language: SupportedLanguage, colors: CodeColors): AnnotatedString {
    val spans = SyntaxHighlighter.tokenize(code, language)
    return buildAnnotatedString {
        append(code)
        spans.forEach { span ->
            val color = when (span.type) {
                TokenType.COMMENT -> colors.comment
                TokenType.STRING -> colors.string
                TokenType.KEYWORD -> colors.keyword
                TokenType.NUMBER -> colors.number
                TokenType.ANNOTATION -> colors.annotation
                TokenType.TAG -> colors.tag
            }
            addStyle(SpanStyle(color = color), span.start.coerceIn(0, code.length), span.end.coerceIn(0, code.length))
        }
    }
}
