package com.ultimate.codecreator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultimate.codecreator.ui.screens.DebugScreen
import com.ultimate.codecreator.ui.screens.GenerateScreen
import com.ultimate.codecreator.ui.screens.SettingsScreen
import com.ultimate.codecreator.ui.screens.SnippetsScreen
import com.ultimate.codecreator.ui.theme.UltimateTheme
import com.ultimate.codecreator.viewmodel.CodeCreatorViewModel

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: CodeCreatorViewModel = viewModel(factory = CodeCreatorViewModel.factory(this))
            val settings by viewModel.settings.collectAsState()
            UltimateTheme(themeMode = settings.themeMode) {
                var currentTab by rememberSaveable { mutableIntStateOf(0) }
                val tabs = listOf(
                    Tab(stringResource(R.string.nav_generate), Icons.Default.AutoFixHigh),
                    Tab(stringResource(R.string.nav_linter), Icons.Default.BugReport),
                    Tab(stringResource(R.string.nav_saved), Icons.Default.FolderSpecial),
                    Tab(stringResource(R.string.nav_settings), Icons.Default.Settings)
                )
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BoxWithConstraints {
                        val wide = maxWidth >= 840.dp
                        if (wide) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                NavigationRail {
                                    tabs.forEachIndexed { index, tab ->
                                        NavigationRailItem(selected = currentTab == index, onClick = { currentTab = index },
                                            icon = { Icon(tab.icon, contentDescription = tab.label) }, label = { Text(tab.label) })
                                    }
                                }
                                Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
                                    Content(currentTab, viewModel, Modifier.padding(padding))
                                }
                            }
                        } else {
                            Scaffold(
                                topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
                                bottomBar = {
                                    NavigationBar {
                                        tabs.forEachIndexed { index, tab ->
                                            NavigationBarItem(selected = currentTab == index, onClick = { currentTab = index },
                                                icon = { Icon(tab.icon, contentDescription = null) }, label = { Text(tab.label) })
                                        }
                                    }
                                }
                            ) { padding ->
                                Content(currentTab, viewModel, Modifier.padding(padding))
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

@Composable
private fun Content(currentTab: Int, viewModel: CodeCreatorViewModel, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (currentTab) {
            0 -> GenerateScreen(viewModel)
            1 -> DebugScreen(viewModel)
            2 -> SnippetsScreen(viewModel)
            3 -> SettingsScreen(viewModel)
        }
    }
}
