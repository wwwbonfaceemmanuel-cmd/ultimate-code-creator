package com.ultimate.codecreator.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ultimate.codecreator.BuildConfig
import com.ultimate.codecreator.R
import com.ultimate.codecreator.model.SavedSnippet
import com.ultimate.codecreator.model.SupportedLanguage
import com.ultimate.codecreator.ui.components.CodeViewer
import com.ultimate.codecreator.ui.components.DocumentIo
import com.ultimate.codecreator.ui.components.HtmlPreviewDialog
import com.ultimate.codecreator.ui.components.SnippetMetadataDialog
import com.ultimate.codecreator.viewmodel.CodeCreatorViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SnippetsScreen(viewModel: CodeCreatorViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snippets by viewModel.snippets.collectAsState()
    val query by viewModel.snippetQuery.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var preview by remember { mutableStateOf<SavedSnippet?>(null) }
    var editTarget by remember { mutableStateOf<SavedSnippet?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedSnippet?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExport
        pendingExport = null
        if (uri != null && json != null) {
            try { DocumentIo.writeText(context, uri, json) } catch (exception: Exception) {
                Toast.makeText(context, exception.message ?: "Export failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { pendingImport = DocumentIo.readText(context, uri, 5_000_000) } catch (exception: Exception) {
                Toast.makeText(context, exception.message ?: "Import failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = query, onValueChange = viewModel::setSnippetQuery, label = { Text(stringResource(R.string.search_snippets)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text(stringResource(R.string.import_pack)) }
            OutlinedButton(onClick = {
                scope.launch {
                    try {
                        pendingExport = viewModel.buildExportPack(BuildConfig.VERSION_NAME)
                        exportLauncher.launch("ultimate-code-snippets.json")
                    } catch (exception: Exception) {
                        Toast.makeText(context, exception.message ?: "Export failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            }) { Text(stringResource(R.string.export_pack)) }
        }
        Spacer(Modifier.height(12.dp))
        if (snippets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (query.isBlank()) stringResource(R.string.empty_snippets) else stringResource(R.string.no_snippet_results))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(snippets, key = { it.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.updatedAt)), style = MaterialTheme.typography.labelSmall)
                                    if (item.tags.isNotEmpty()) Text(item.tags.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { viewModel.toggleFavorite(item.id) }) {
                                    Icon(if (item.favorite) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = stringResource(if (item.favorite) R.string.unfavorite else R.string.favorite))
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { editTarget = item }) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_snippet)) }
                                IconButton(onClick = { viewModel.duplicateSnippet(item.id) }) { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.duplicate)) }
                                IconButton(onClick = { DocumentIo.shareSnippetFile(context, item) }) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share)) }
                                IconButton(onClick = { deleteTarget = item }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete)) }
                            }
                            Spacer(Modifier.height(8.dp))
                            CodeViewer(code = item.code, language = item.language,
                                onPreviewHtml = if (item.language == SupportedLanguage.HTML_CSS) { { preview = item } } else null)
                        }
                    }
                }
            }
        }
    }

    preview?.let { HtmlPreviewDialog(htmlContent = it.code, javaScriptEnabled = settings.previewJavaScriptEnabled, onDismiss = { preview = null }) }
    editTarget?.let { target ->
        SnippetMetadataDialog(initialTitle = target.title, initialTags = target.tags, onDismiss = { editTarget = null }, onSave = { title, tags ->
            viewModel.renameSnippet(target.id, title, tags)
            editTarget = null
        })
    }
    deleteTarget?.let { target ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text(stringResource(R.string.delete)) }, text = { Text(stringResource(R.string.delete_snippet_confirm)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteSnippet(target.id); deleteTarget = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } })
    }
    pendingImport?.let { json ->
        AlertDialog(onDismissRequest = { pendingImport = null }, title = { Text(stringResource(R.string.import_replace_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.import_replace_message))
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.importPack(json, replaceExisting = true); pendingImport = null }) { Text(stringResource(R.string.replace_existing)) }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.importPack(json, replaceExisting = false); pendingImport = null }) { Text(stringResource(R.string.merge)) } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } })
    }
}
