package com.ultimate.codecreator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultimate.codecreator.engine.DiffEngine
import com.ultimate.codecreator.engine.DiffType

@Composable
fun DiffView(original: String, repaired: String, modifier: Modifier = Modifier) {
    val lines = remember(original, repaired) { DiffEngine.diff(original, repaired) }
    val added = Color(0xFF14532D).copy(alpha = 0.35f)
    val removed = Color(0xFF7F1D1D).copy(alpha = 0.35f)
    Column(modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        lines.forEach { line ->
            val background = when (line.type) {
                DiffType.ADDED -> added
                DiffType.REMOVED -> removed
                DiffType.CONTEXT -> Color.Transparent
            }
            val prefix = when (line.type) {
                DiffType.ADDED -> "+ "
                DiffType.REMOVED -> "- "
                DiffType.CONTEXT -> "  "
            }
            Row(modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = 8.dp, vertical = 1.dp)) {
                Text(prefix + line.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
