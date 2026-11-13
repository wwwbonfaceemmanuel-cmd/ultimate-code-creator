package com.ultimate.codecreator.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultimate.codecreator.R
import com.ultimate.codecreator.model.SupportedLanguage

@Composable
fun CodeViewer(
    code: String,
    language: SupportedLanguage,
    modifier: Modifier = Modifier,
    onPreviewHtml: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = rememberCodeColors()
    val annotated = remember(code, language, colors) { highlightedCode(code, language, colors) }
    val lineNumbers = remember(code) { code.lines().indices.joinToString("\n") { (it + 1).toString() } }
    Column(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(language.displayName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Row {
                if (onPreviewHtml != null) {
                    IconButton(onClick = onPreviewHtml) {
                        Icon(Icons.Default.Visibility, contentDescription = stringResource(R.string.preview_html), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Generated code", code))
                    Toast.makeText(context, context.getString(R.string.copy_code), Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_code), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        SelectionContainer {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(
                    lineNumbers,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = colors.comment,
                    modifier = Modifier.padding(end = 14.dp)
                )
                Box(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    Text(annotated, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, softWrap = false)
                }
            }
        }
    }
}
