package com.ultimate.codecreator.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ultimate.codecreator.R
import com.ultimate.codecreator.engine.OfflineCodeSynthesizer
import com.ultimate.codecreator.model.SupportedLanguage
import com.ultimate.codecreator.ui.components.CodeViewer
import com.ultimate.codecreator.ui.components.HtmlPreviewDialog
import com.ultimate.codecreator.ui.components.LanguageChips
import com.ultimate.codecreator.ui.components.RecipeCatalogDialog
import com.ultimate.codecreator.ui.components.SnippetMetadataDialog
import com.ultimate.codecreator.ui.theme.AmberAccent
import com.ultimate.codecreator.ui.theme.GreenAccent
import com.ultimate.codecreator.viewmodel.CodeCreatorViewModel

@Composable
fun GenerateScreen(viewModel: CodeCreatorViewModel) {
    val context = LocalContext.current
    var selectedLanguage by rememberSaveable { mutableStateOf(SupportedLanguage.PYTHON) }
    var prompt by rememberSaveable { mutableStateOf("Binary Search") }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    val result by viewModel.generationResult.collectAsState()
    val busy by viewModel.isGenerating.collectAsState()
    val message by viewModel.message.collectAsState()
    val settings by viewModel.settings.collectAsState()

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.OfflineBolt, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.offline_badge), color = GreenAccent, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.generate_helper), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        LanguageChips(selectedLanguage, onSelected = { selectedLanguage = it })
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tap_to_add_keyword), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { showCatalog = true }) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.recipe_catalog))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            selectedLanguage.quickWords.forEach { word ->
                SuggestionChip(onClick = {
                    prompt = if (prompt.isBlank()) word else (prompt.trim() + " " + word).take(OfflineCodeSynthesizer.MAX_PROMPT_LENGTH)
                }, label = { Text(word) })
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it.take(OfflineCodeSynthesizer.MAX_PROMPT_LENGTH) },
            label = { Text(stringResource(R.string.instruction_label)) },
            placeholder = { Text(stringResource(R.string.instruction_placeholder)) },
            supportingText = { Text("${prompt.length}/${OfflineCodeSynthesizer.MAX_PROMPT_LENGTH}") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { viewModel.generateCodeOffline(prompt, selectedLanguage) }, enabled = !busy && prompt.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (busy) stringResource(R.string.generating) else stringResource(R.string.generate_button, selectedLanguage.displayName))
        }
        val generated = result
        if (generated != null) {
            Spacer(Modifier.height(22.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).semantics { heading() }) {
                    Text(generated.intentDetected, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (generated.isTemplateFallback) stringResource(R.string.starter_label) else stringResource(R.string.recipe_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (generated.isTemplateFallback) AmberAccent else GreenAccent
                    )
                    Text(stringResource(R.string.confidence_label, (generated.confidence * 100).toInt()), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { showSaveDialog = true }) {
                    Icon(Icons.Default.Bookmark, contentDescription = stringResource(R.string.save_snippet))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(generated.explanation, style = MaterialTheme.typography.bodyMedium)
            if (generated.appliedParameters.isNotEmpty()) {
                Text(stringResource(R.string.applied_parameters_label, generated.appliedParameters.entries.joinToString { "${it.key}=${it.value}" }), style = MaterialTheme.typography.labelSmall)
            }
            if (generated.alternatives.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.alternatives_label), style = MaterialTheme.typography.labelSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    generated.alternatives.forEach { alternative ->
                        AssistChip(onClick = { prompt = alternative }, label = { Text(alternative) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            CodeViewer(
                code = generated.code,
                language = generated.language,
                onPreviewHtml = if (generated.language == SupportedLanguage.HTML_CSS) { { showPreview = true } } else null
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    val preview = result
    if (showPreview && preview?.language == SupportedLanguage.HTML_CSS) {
        HtmlPreviewDialog(htmlContent = preview.code, javaScriptEnabled = settings.previewJavaScriptEnabled, onDismiss = { showPreview = false })
    }
    if (showCatalog) {
        RecipeCatalogDialog(onDismiss = { showCatalog = false }, onSelectRecipe = { keyword -> prompt = keyword; showCatalog = false })
    }
    val saveTarget = result
    if (showSaveDialog && saveTarget != null) {
        SnippetMetadataDialog(
            initialTitle = prompt.take(80),
            initialTags = emptyList(),
            onDismiss = { showSaveDialog = false },
            onSave = { title, tags ->
                viewModel.saveSnippet(title, saveTarget.code, saveTarget.language, tags)
                showSaveDialog = false
            }
        )
    }
}
