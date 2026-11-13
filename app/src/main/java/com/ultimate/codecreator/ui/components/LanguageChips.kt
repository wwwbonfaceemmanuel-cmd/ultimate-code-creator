package com.ultimate.codecreator.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultimate.codecreator.model.SupportedLanguage

@Composable
fun LanguageChips(selected: SupportedLanguage, onSelected: (SupportedLanguage) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SupportedLanguage.entries.forEach { language ->
            FilterChip(selected = selected == language, onClick = { onSelected(language) }, label = { Text(language.displayName) })
        }
    }
}
