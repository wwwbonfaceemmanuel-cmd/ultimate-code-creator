package com.ultimate.codecreator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ultimate.codecreator.model.Severity
import com.ultimate.codecreator.model.SupportedLanguage
import com.ultimate.codecreator.ui.components.CodeViewer
import com.ultimate.codecreator.ui.components.LanguageChips
import com.ultimate.codecreator.ui.theme.AmberAccent
import com.ultimate.codecreator.ui.theme.DarkElevated
import com.ultimate.codecreator.ui.theme.GreenAccent
import com.ultimate.codecreator.ui.theme.RedAccent
import com.ultimate.codecreator.viewmodel.CodeCreatorViewModel

@Composable
fun DebugScreen(viewModel: CodeCreatorViewModel) {
    var selectedLanguage by rememberSaveable { mutableStateOf(SupportedLanguage.PYTHON) }
    var inputCode by rememberSaveable {
        mutableStateOf("def calculate_tax(amount)\n    if amount > 1000\n        return amount * 0.2\n    return amount * 0.1\n")
    }
    val result by viewModel.debugResult.collectAsState()
    val busy by viewModel.isDebugging.collectAsState()
    LaunchedEffect(inputCode, selectedLanguage) { viewModel.clearDebugResult() }

    Column(modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Offline structural checks", style = MaterialTheme.typography.titleMedium)
        Text("Comments and strings are ignored. This is a heuristic linter, not a compiler or a full AST parser.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        LanguageChips(selectedLanguage) { selectedLanguage = it }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = inputCode,
            onValueChange = { inputCode = it.take(CodeCreatorViewModel.MAX_EDITOR_LENGTH) },
            label = { Text("Code to analyze") },
            supportingText = { Text("${inputCode.length}/${CodeCreatorViewModel.MAX_EDITOR_LENGTH}") },
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            minLines = 7,
            maxLines = 14,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { viewModel.debugCodeOffline(inputCode, selectedLanguage) }, enabled = !busy && inputCode.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.BugReport, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "Analyzing…" else "Analyze & repair safely")
        }
        val analyzed = result
        if (analyzed != null) {
            val accent = if (analyzed.errors.isEmpty()) GreenAccent else if (analyzed.hasErrors) RedAccent else AmberAccent
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.12f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (analyzed.errors.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, tint = accent)
                Spacer(Modifier.width(8.dp))
                Text(analyzed.analysisSummary, style = MaterialTheme.typography.bodyMedium)
            }
            if (analyzed.errors.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Findings", style = MaterialTheme.typography.titleMedium)
                analyzed.errors.forEach { error ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = DarkElevated)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Line ${error.line} · ${error.severity.name}: ${error.description}", color = if (error.severity == Severity.ERROR) RedAccent else AmberAccent, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(error.fixSuggestion, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(if (analyzed.fixedCode == inputCode) "Reviewed code · no automatic changes" else "Repaired code · review before using", style = MaterialTheme.typography.titleMedium)
            if (analyzed.fixedCode != inputCode) {
                TextButton(onClick = { inputCode = analyzed.fixedCode }) { Text("Use repaired code in editor") }
            }
            Spacer(Modifier.height(6.dp))
            CodeViewer(code = analyzed.fixedCode, language = selectedLanguage.displayName)
            Spacer(Modifier.height(24.dp))
        }
    }
}
