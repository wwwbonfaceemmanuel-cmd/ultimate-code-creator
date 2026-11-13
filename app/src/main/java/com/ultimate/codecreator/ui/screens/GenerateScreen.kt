package com.ultimate.codecreator.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ultimate.codecreator.engine.OfflineCodeSynthesizer
import com.ultimate.codecreator.model.SupportedLanguage
import com.ultimate.codecreator.ui.components.CodeViewer
import com.ultimate.codecreator.ui.components.HtmlPreviewDialog
import com.ultimate.codecreator.ui.components.LanguageChips
import com.ultimate.codecreator.ui.theme.AmberAccent
import com.ultimate.codecreator.ui.theme.CyanAccent
import com.ultimate.codecreator.ui.theme.GreenAccent
import com.ultimate.codecreator.viewmodel.CodeCreatorViewModel

@Composable
fun GenerateScreen(viewModel: CodeCreatorViewModel) {
    val context = LocalContext.current
    var selectedLanguage by rememberSaveable { mutableStateOf(SupportedLanguage.PYTHON) }
    var prompt by rememberSaveable { mutableStateOf("Binary Search") }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    val result by viewModel.generationResult.collectAsState()
    val busy by viewModel.isGenerating.collectAsState()
    val message by viewModel.message.collectAsState()

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
            Text("Offline templates · No API keys", color = GreenAccent, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text("Choose a bundled recipe, or get a clearly labelled starter in your language.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        LanguageChips(selectedLanguage, onSelected = { selectedLanguage = it })
        Spacer(Modifier.height(14.dp))
        Text("Tap to add a keyword", style = MaterialTheme.typography.labelMedium)
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
            label = { Text("Instruction or concept") },
            placeholder = { Text("e.g. binary search, login form, LRU cache") },
            supportingText = { Text("${prompt.length}/${OfflineCodeSynthesizer.MAX_PROMPT_LENGTH}") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { viewModel.generateCodeOffline(prompt, selectedLanguage) }, enabled = !busy && prompt.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (busy) "Generating…" else "Generate ${selectedLanguage.displayName} code")
        }
        val generated = result
        if (generated != null) {
            Spacer(Modifier.height(22.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(generated.intentDetected, style = MaterialTheme.typography.titleMedium, color = CyanAccent)
                    Text(if (generated.isTemplateFallback) "Starter template · not a custom solution" else "Bundled recipe · review before use",
                        style = MaterialTheme.typography.labelSmall, color = if (generated.isTemplateFallback) AmberAccent else GreenAccent)
                }
                IconButton(onClick = { viewModel.saveSnippet(prompt, generated.code, generated.language) }) {
                    Icon(Icons.Default.Bookmark, contentDescription = "Save snippet on this device")
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(generated.explanation, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            CodeViewer(code = generated.code, language = generated.language.displayName,
                onPreviewHtml = if (generated.language == SupportedLanguage.HTML_CSS) { { showPreview = true } } else null)
            Spacer(Modifier.height(24.dp))
        }
    }
    val preview = result
    if (showPreview && preview?.language == SupportedLanguage.HTML_CSS) {
        HtmlPreviewDialog(htmlContent = preview.code, onDismiss = { showPreview = false })
    }
}
