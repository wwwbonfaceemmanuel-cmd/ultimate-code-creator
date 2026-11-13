package com.ultimate.codecreator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ultimate.codecreator.BuildConfig
import com.ultimate.codecreator.R
import com.ultimate.codecreator.model.ThemeMode
import com.ultimate.codecreator.viewmodel.CodeCreatorViewModel

@Composable
fun SettingsScreen(viewModel: CodeCreatorViewModel) {
    val settings by viewModel.settings.collectAsState()
    var confirmDeleteAll by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium)
        ThemeMode.entries.forEach { mode ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = settings.themeMode == mode, onClick = { viewModel.updateSettings { it.copy(themeMode = mode) } })
                Text(when (mode) {
                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                    ThemeMode.DARK -> stringResource(R.string.theme_dark)
                })
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.preview_js_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.preview_js_warning), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = settings.previewJavaScriptEnabled, onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(previewJavaScriptEnabled = enabled) } })
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.editor_font_size, settings.editorFontSizeSp), style = MaterialTheme.typography.titleMedium)
        Slider(
            value = settings.editorFontSizeSp.toFloat(),
            onValueChange = { size -> viewModel.updateSettings { it.copy(editorFontSizeSp = size.toInt()) } },
            valueRange = 10f..22f,
            steps = 11
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.word_wrap), modifier = Modifier.weight(1f))
            Switch(checked = settings.editorWordWrap, onCheckedChange = { wrap -> viewModel.updateSettings { it.copy(editorWordWrap = wrap) } })
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.data_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.data_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { confirmDeleteAll = true }) { Text(stringResource(R.string.delete_all)) }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelSmall)
    }
    if (confirmDeleteAll) {
        AlertDialog(onDismissRequest = { confirmDeleteAll = false }, title = { Text(stringResource(R.string.delete_all)) }, text = { Text(stringResource(R.string.delete_all_confirm)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteAllSnippets(); confirmDeleteAll = false }) { Text(stringResource(R.string.delete_all)) } },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.cancel)) } })
    }
}
