package com.ultimate.codecreator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ultimate.codecreator.R
import com.ultimate.codecreator.model.GenerationMode

@Composable
fun GenerationModeSelector(
    selected: GenerationMode,
    onSelected: (GenerationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GenerationMode.entries.forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                    label = {
                        Text(
                            when (mode) {
                                GenerationMode.AUTO -> stringResource(R.string.mode_auto)
                                GenerationMode.OFFLINE -> stringResource(R.string.mode_offline)
                                GenerationMode.CLOUD -> stringResource(R.string.mode_cloud)
                            }
                        )
                    }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = when (selected) {
                GenerationMode.AUTO -> stringResource(R.string.mode_auto_helper)
                GenerationMode.OFFLINE -> stringResource(R.string.mode_offline_helper)
                GenerationMode.CLOUD -> stringResource(R.string.mode_cloud_helper)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
