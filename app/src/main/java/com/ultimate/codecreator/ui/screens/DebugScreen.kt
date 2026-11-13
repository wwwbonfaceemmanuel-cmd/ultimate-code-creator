package com.ultimate.codecreator.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ultimate.codecreator.R
import com.ultimate.codecreator.engine.CodeFormatter
import com.ultimate.codecreator.model.Severity
import com.ultimate.codecreator.model.SupportedLanguage
import com.ultimate.codecreator.ui.components.*
import com.ultimate.codecreator.ui.theme.AmberAccent
import com.ultimate.codecreator.ui.theme.GreenAccent
import com.ultimate.codecreator.ui.theme.RedAccent
import com.ultimate.codecreator.viewmodel.CodeCreatorViewModel

@Composable
fun DebugScreen(viewModel: CodeCreatorViewModel) {
    val context = LocalContext.current
    var selectedLanguage by rememberSaveable { mutableStateOf(SupportedLanguage.PYTHON) }
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue("def calculate_tax(amount)\n    if amount > 1000\n        return amount * 0.2\n    return amount * 0.1\n"))
    }
    val history = remember { EditorHistory() }
    LaunchedEffect(Unit) { history.record(input) }
    val result by viewModel.debugResult.collectAsState()
    val busy by viewModel.isDebugging.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val message by viewModel.message.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var currentMatch by rememberSaveable { mutableIntStateOf(-1) }
    var severityFilter by rememberSaveable { mutableStateOf("ALL") }
    var showRepairReview by rememberSaveable { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(input.text, selectedLanguage) { viewModel.clearDebugResult() }
    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.consumeMessage() }
    }

    val matches = remember(input.text, searchQuery) {
        if (searchQuery.isEmpty()) emptyList() else buildList {
            var index = input.text.indexOf(searchQuery, 0, ignoreCase = true)
            while (index >= 0) { add(index); index = input.text.indexOf(searchQuery, index + searchQuery.length, ignoreCase = true) }
        }
    }
    LaunchedEffect(matches) { if (currentMatch >= matches.size) currentMatch = -1 }

    fun selectMatch(index: Int) {
        if (index !in matches.indices) return
        currentMatch = index
        val start = matches[index]
        input = input.copy(selection = TextRange(start, start + searchQuery.length))
    }

    fun updateEditor(new: TextFieldValue) {
        var limited = if (new.text.length > CodeCreatorViewModel.MAX_EDITOR_LENGTH) {
            new.copy(text = new.text.take(CodeCreatorViewModel.MAX_EDITOR_LENGTH), selection = TextRange(CodeCreatorViewModel.MAX_EDITOR_LENGTH))
        } else new
        if (limited.text.length == input.text.length + 1 && limited.selection.start > 0 &&
            limited.text[limited.selection.start - 1] == '\n') {
            val lineStart = input.text.lastIndexOf('\n', input.selection.start - 1) + 1
            val currentLine = input.text.substring(lineStart.coerceAtLeast(0), input.selection.start.coerceAtLeast(0))
            val indent = CodeFormatter.nextLineIndent(currentLine, selectedLanguage)
            if (indent.isNotEmpty()) {
                val position = limited.selection.start
                val text = limited.text.substring(0, position) + indent + limited.text.substring(position)
                limited = TextFieldValue(text, TextRange(position + indent.length))
            }
        }
        if (limited.text != input.text) history.record(limited)
        input = limited
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = DocumentIo.readText(context, uri)
                input = TextFieldValue(text)
                history.record(input)
                val extension = uri.lastPathSegment?.substringAfterLast('.', "") ?: ""
                SupportedLanguage.fromExtension(extension)?.let { selectedLanguage = it }
            } catch (exception: Exception) {
                Toast.makeText(context, exception.message ?: "Could not open file.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = pendingSave
        pendingSave = null
        if (uri != null && text != null) {
            try { DocumentIo.writeText(context, uri, text) } catch (exception: Exception) {
                Toast.makeText(context, exception.message ?: "Could not save file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.linter_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.linter_helper), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        LanguageChips(selectedLanguage, onSelected = { selectedLanguage = it })
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { history.undo()?.let { input = it } }, enabled = history.canUndo) { Icon(Icons.Default.Undo, contentDescription = stringResource(R.string.undo)) }
            IconButton(onClick = { history.redo()?.let { input = it } }, enabled = history.canRedo) { Icon(Icons.Default.Redo, contentDescription = stringResource(R.string.redo)) }
            TextButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.open_file)) }
            TextButton(onClick = { pendingSave = input.text; saveLauncher.launch("snippet.${selectedLanguage.fileExtension}") }) { Text(stringResource(R.string.save_file)) }
            TextButton(onClick = { DocumentIo.shareText(context, "Code snippet", input.text) }) { Text(stringResource(R.string.share_code)) }
            TextButton(onClick = { updateEditor(TextFieldValue(CodeFormatter.cleanWhitespace(input.text))) }) { Text(stringResource(R.string.clean_whitespace)) }
        }
        CodeEditor(value = input, onValueChange = { updateEditor(it) }, language = selectedLanguage, fontSizeSp = settings.editorFontSizeSp, wordWrap = settings.editorWordWrap, modifier = Modifier.heightIn(min = 220.dp))
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text(stringResource(R.string.find)) }, singleLine = true, modifier = Modifier.weight(1f))
            IconButton(onClick = { selectMatch(if (matches.isEmpty()) -1 else (currentMatch + 1) % matches.size) }, enabled = matches.isNotEmpty()) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.next_match)) }
            IconButton(onClick = { selectMatch(if (matches.isEmpty()) -1 else (currentMatch - 1 + matches.size) % matches.size) }, enabled = matches.isNotEmpty()) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.previous_match)) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = replacement, onValueChange = { replacement = it }, label = { Text(stringResource(R.string.replace)) }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (searchQuery.isNotEmpty()) {
                    val replaced = input.text.replace(searchQuery, replacement, ignoreCase = true)
                    updateEditor(TextFieldValue(replaced))
                }
            }, enabled = searchQuery.isNotEmpty()) { Text(stringResource(R.string.replace_all)) }
        }
        if (searchQuery.isNotEmpty()) Text(stringResource(R.string.matches_count, matches.size), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { viewModel.debugCodeOffline(input.text, selectedLanguage) }, enabled = !busy && input.text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.BugReport, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (busy) stringResource(R.string.analyzing) else stringResource(R.string.analyze_button))
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
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = severityFilter == "ALL", onClick = { severityFilter = "ALL" }, label = { Text(stringResource(R.string.filter_all)) })
                FilterChip(selected = severityFilter == "ERROR", onClick = { severityFilter = "ERROR" }, label = { Text(stringResource(R.string.filter_errors)) })
                FilterChip(selected = severityFilter == "WARNING", onClick = { severityFilter = "WARNING" }, label = { Text(stringResource(R.string.filter_warnings)) })
            }
            val findings = analyzed.errors.filter { severityFilter == "ALL" || it.severity.name == severityFilter }
            if (findings.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.findings), style = MaterialTheme.typography.titleMedium)
                findings.forEach { error ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.line_label, error.line, error.severity.name, error.description), color = if (error.severity == Severity.ERROR) RedAccent else AmberAccent, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(error.fixSuggestion, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val changed = analyzed.fixedCode != input.text
            Text(if (changed) stringResource(R.string.repaired_code) else stringResource(R.string.reviewed_code), style = MaterialTheme.typography.titleMedium)
            if (changed) {
                TextButton(onClick = { showRepairReview = true }) { Text(stringResource(R.string.review_repairs)) }
            }
            Spacer(Modifier.height(6.dp))
            CodeViewer(code = analyzed.fixedCode, language = selectedLanguage)
            Spacer(Modifier.height(24.dp))
        }
    }

    val reviewed = result
    if (showRepairReview && reviewed != null) {
        AlertDialog(
            onDismissRequest = { showRepairReview = false },
            title = { Text(stringResource(R.string.repair_review_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.repair_review_helper), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    DiffView(original = input.text, repaired = reviewed.fixedCode)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    input = TextFieldValue(reviewed.fixedCode)
                    history.record(input)
                    showRepairReview = false
                    viewModel.clearDebugResult()
                }) { Text(stringResource(R.string.apply_repairs)) }
            },
            dismissButton = { TextButton(onClick = { showRepairReview = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
