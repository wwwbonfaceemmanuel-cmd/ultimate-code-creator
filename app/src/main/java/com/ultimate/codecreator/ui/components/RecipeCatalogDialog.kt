package com.ultimate.codecreator.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ultimate.codecreator.R
import com.ultimate.codecreator.engine.OfflineCodeSynthesizer
import com.ultimate.codecreator.model.RecipeInfo

@Composable
fun RecipeCatalogDialog(onDismiss: () -> Unit, onSelectRecipe: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val recipes = remember(query) {
        val q = query.trim().lowercase()
        OfflineCodeSynthesizer.catalog.filter { recipe ->
            q.isBlank() || recipe.title.lowercase().contains(q) ||
                recipe.keywords.any { it.lowercase().contains(q) } ||
                recipe.languages.any { it.displayName.lowercase().contains(q) }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.recipe_catalog), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close)) }
                }
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.catalog_search)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                if (recipes.isEmpty()) {
                    Text(stringResource(R.string.catalog_empty))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recipes, key = { it.id }) { recipe -> RecipeCard(recipe, onSelectRecipe) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: RecipeInfo, onSelectRecipe: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(recipe.title, style = MaterialTheme.typography.titleMedium)
            Text(recipe.explanation, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text("${stringResource(R.string.catalog_languages)}: ${recipe.languages.joinToString { it.displayName }}", style = MaterialTheme.typography.labelMedium)
            if (recipe.keywords.isNotEmpty()) {
                Text(recipe.keywords.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (recipe.dependencies.isNotEmpty()) {
                Text("${stringResource(R.string.catalog_dependencies)}: " + recipe.dependencies.entries.joinToString { (lang, deps) -> "${lang.displayName}: ${deps.joinToString()}" }, style = MaterialTheme.typography.labelSmall)
            }
            recipe.safetyNotes.forEach { note ->
                Text("${stringResource(R.string.catalog_safety)}: $note", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            recipe.parameters.forEach { parameter ->
                Text("${stringResource(R.string.catalog_parameters)}: ${parameter.label} — ${parameter.helpText}", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onSelectRecipe(recipe.keywords.firstOrNull() ?: recipe.title) }) {
                Text(stringResource(R.string.catalog_use_recipe))
            }
        }
    }
}
