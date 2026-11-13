package com.ultimate.codecreator.ui.components

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ultimate.codecreator.R
import com.ultimate.codecreator.model.CloudProviderPresets
import com.ultimate.codecreator.ui.theme.GreenAccent
import com.ultimate.codecreator.viewmodel.CodeCreatorViewModel

@Composable
fun CloudProviderSettings(viewModel: CodeCreatorViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val config by viewModel.cloudConfig.collectAsState()
    val testing by viewModel.isTestingCloud.collectAsState()
    val message by viewModel.message.collectAsState()

    var provider by remember { mutableStateOf(config.providerLabel) }
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var model by remember { mutableStateOf(config.model) }
    var apiKeyInput by remember { mutableStateOf("") }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(stringResource(R.string.cloud_section_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.cloud_section_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (config.isConfigured) stringResource(R.string.cloud_configured_status, config.providerLabel, config.model)
            else stringResource(R.string.cloud_not_configured_status),
            style = MaterialTheme.typography.labelMedium,
            color = if (config.isConfigured) GreenAccent else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (config.hasApiKey) stringResource(R.string.cloud_api_key_saved) else stringResource(R.string.cloud_api_key_missing),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.cloud_provider_label), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudProviderPresets.all.forEach { preset ->
                FilterChip(
                    selected = provider == preset.label,
                    onClick = {
                        provider = preset.label
                        if (preset.baseUrl.isNotEmpty()) {
                            baseUrl = preset.baseUrl
                            model = preset.defaultModel
                        }
                    },
                    label = { Text(preset.label) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it.take(200) },
            label = { Text(stringResource(R.string.cloud_base_url_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = model,
            onValueChange = { model = it.take(120) },
            label = { Text(stringResource(R.string.cloud_model_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { viewModel.updateCloudEndpoint(provider, baseUrl, model) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cloud_save_endpoint))
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it.take(400) },
            label = { Text(stringResource(R.string.cloud_api_key_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.setApiKey(apiKeyInput); apiKeyInput = "" }, enabled = apiKeyInput.isNotBlank()) {
                Text(stringResource(R.string.cloud_save_key))
            }
            OutlinedButton(onClick = { viewModel.clearApiKey() }, enabled = config.hasApiKey) {
                Text(stringResource(R.string.cloud_clear_key))
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { viewModel.testCloudConnection() }, enabled = config.isConfigured && !testing, modifier = Modifier.fillMaxWidth()) {
            Text(if (testing) stringResource(R.string.cloud_testing) else stringResource(R.string.cloud_test_connection))
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.cloud_privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
