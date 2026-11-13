package com.ultimate.codecreator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultimate.codecreator.model.SavedSnippet
import com.ultimate.codecreator.model.SupportedLanguage
import com.ultimate.codecreator.ui.components.CodeViewer
import com.ultimate.codecreator.ui.components.HtmlPreviewDialog
import com.ultimate.codecreator.ui.theme.DarkElevated
import com.ultimate.codecreator.viewmodel.CodeCreatorViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun SnippetsScreen(viewModel: CodeCreatorViewModel) {
    val snippets by viewModel.snippets.collectAsState()
    var preview by remember { mutableStateOf<SavedSnippet?>(null) }
    if (snippets.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No saved snippets yet. Generate code and tap the bookmark to keep it on this device.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(snippets, key = { it.id }) { item ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkElevated)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.dateAdded)), style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { viewModel.deleteSnippet(item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ${item.title}")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        CodeViewer(code = item.code, language = item.language.displayName,
                            onPreviewHtml = if (item.language == SupportedLanguage.HTML_CSS) { { preview = item } } else null)
                    }
                }
            }
        }
    }
    preview?.let { HtmlPreviewDialog(htmlContent = it.code, onDismiss = { preview = null }) }
}
