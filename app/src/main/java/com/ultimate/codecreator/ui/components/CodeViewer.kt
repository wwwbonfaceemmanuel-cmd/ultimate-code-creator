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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultimate.codecreator.ui.theme.CyanAccent
import com.ultimate.codecreator.ui.theme.DarkElevated
import com.ultimate.codecreator.ui.theme.TextMuted

@Composable
fun CodeViewer(code: String, language: String, modifier: Modifier = Modifier, onPreviewHtml: (() -> Unit)? = null) {
    val context = LocalContext.current
    val lineNumbers = remember(code) { code.lines().indices.joinToString("\n") { (it + 1).toString() } }
    Column(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF030712))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(DarkElevated).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(language, style = MaterialTheme.typography.labelMedium, color = CyanAccent, modifier = Modifier.weight(1f))
            Row {
                if (onPreviewHtml != null) {
                    IconButton(onClick = onPreviewHtml) {
                        Icon(Icons.Default.Visibility, contentDescription = "Preview HTML offline", tint = Color.White)
                    }
                }
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Generated code", code))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy code", tint = Color.White)
                }
            }
        }
        SelectionContainer {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp)) {
                Text(lineNumbers, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, color = TextMuted, modifier = Modifier.padding(end = 14.dp))
                Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, color = Color(0xFFE2E8F0), softWrap = false)
            }
        }
    }
}
