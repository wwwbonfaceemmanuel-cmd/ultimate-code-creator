package com.ultimate.codecreator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultimate.codecreator.ui.screens.DebugScreen
import com.ultimate.codecreator.ui.screens.GenerateScreen
import com.ultimate.codecreator.ui.screens.SnippetsScreen
import com.ultimate.codecreator.ui.theme.DarkBg
import com.ultimate.codecreator.ui.theme.DarkSurface
import com.ultimate.codecreator.ui.theme.UltimateTheme
import com.ultimate.codecreator.viewmodel.CodeCreatorViewModel

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltimateTheme {
                val viewModel: CodeCreatorViewModel = viewModel()
                var currentTab by rememberSaveable { mutableIntStateOf(0) }
                Scaffold(
                    containerColor = DarkBg,
                    topBar = {
                        TopAppBar(title = { Text("Ultimate Code Creator") },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface, titleContentColor = MaterialTheme.colorScheme.onSurface))
                    },
                    bottomBar = {
                        NavigationBar(containerColor = DarkSurface) {
                            NavigationBarItem(selected = currentTab == 0, onClick = { currentTab = 0 },
                                icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) }, label = { Text("Generate") })
                            NavigationBarItem(selected = currentTab == 1, onClick = { currentTab = 1 },
                                icon = { Icon(Icons.Default.BugReport, contentDescription = null) }, label = { Text("Linter") })
                            NavigationBarItem(selected = currentTab == 2, onClick = { currentTab = 2 },
                                icon = { Icon(Icons.Default.FolderSpecial, contentDescription = null) }, label = { Text("Saved") })
                        }
                    }
                ) { innerPadding ->
                    Surface(modifier = Modifier.fillMaxSize().padding(innerPadding), color = DarkBg) {
                        when (currentTab) {
                            0 -> GenerateScreen(viewModel)
                            1 -> DebugScreen(viewModel)
                            2 -> SnippetsScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}
