package com.ultimate.codecreator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultimate.codecreator.R
import com.ultimate.codecreator.model.SupportedLanguage

@Composable
fun CodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    language: SupportedLanguage,
    fontSizeSp: Int,
    wordWrap: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = rememberCodeColors()
    val placeholder = stringResource(R.string.editor_placeholder)
    val transformation = remember(language, colors) {
        VisualTransformation { text ->
            TransformedText(highlightedCode(text.toString(), language, colors), OffsetMapping.Identity)
        }
    }
    val scrollModifier = if (wordWrap) Modifier else Modifier.horizontalScroll(rememberScrollState())
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSizeSp.sp, lineHeight = (fontSizeSp + 5).sp, color = colors.text),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
        visualTransformation = transformation,
        softWrap = wordWrap,
        maxLines = Int.MAX_VALUE,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .then(scrollModifier)
            .padding(12.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.text.isEmpty()) {
                    Text(placeholder, color = colors.comment, fontFamily = FontFamily.Monospace, fontSize = fontSizeSp.sp)
                }
                innerTextField()
            }
        }
    )
}
