package com.ultimate.codecreator.data

import android.content.SharedPreferences
import com.ultimate.codecreator.model.CloudConfig
import com.ultimate.codecreator.model.CloudEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the non-secret cloud configuration in a [StateFlow] and the API key in a
 * [SecretStore]. The key is read on demand and never exposed through [config].
 */
interface CloudSettingsStore {
    val config: StateFlow<CloudConfig>
    suspend fun updateEndpoint(providerLabel: String, baseUrl: String, model: String)
    suspend fun setApiKey(apiKey: String)
    suspend fun clearApiKey()
    suspend fun apiKey(): String?
    suspend fun endpoint(): CloudEndpoint?
}

class PrefsCloudSettingsStore(
    private val prefs: SharedPreferences,
    private val secretStore: SecretStore
) : CloudSettingsStore {

    private val _config = MutableStateFlow(read())
    override val config: StateFlow<CloudConfig> = _config.asStateFlow()

    override suspend fun updateEndpoint(providerLabel: String, baseUrl: String, model: String) {
        val normalized = CloudConfig.normalizeBaseUrl(baseUrl)
        require(normalized.isEmpty() || CloudConfig.isValidBaseUrl(normalized)) {
            "Cloud base URL must be a valid https:// address."
        }
        val label = providerLabel.trim().ifEmpty { "Custom" }.take(40)
        val safeModel = model.trim().take(120)
        prefs.edit()
            .putString(KEY_PROVIDER, label)
            .putString(KEY_BASE_URL, normalized)
            .putString(KEY_MODEL, safeModel)
            .apply()
        _config.value = read()
    }

    override suspend fun setApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty()) { "Enter an API key." }
        require(trimmed.length <= MAX_KEY_LENGTH) { "API key is too long." }
        secretStore.put(KEY_API_KEY, trimmed)
        _config.value = read()
    }

    override suspend fun clearApiKey() {
        secretStore.clear(KEY_API_KEY)
        _config.value = read()
    }

    override suspend fun apiKey(): String? = secretStore.get(KEY_API_KEY)?.takeIf { it.isNotBlank() }

    override suspend fun endpoint(): CloudEndpoint? {
        val current = _config.value
        val key = apiKey() ?: return null
        if (!current.isConfigured) return null
        return CloudEndpoint(current.baseUrl, current.model, key)
    }

    private fun read(): CloudConfig = CloudConfig(
        providerLabel = prefs.getString(KEY_PROVIDER, "Custom") ?: "Custom",
        baseUrl = prefs.getString(KEY_BASE_URL, "") ?: "",
        model = prefs.getString(KEY_MODEL, "") ?: "",
        hasApiKey = !secretStore.get(KEY_API_KEY).isNullOrBlank()
    )

    companion object {
        private const val KEY_PROVIDER = "cloud_provider"
        private const val KEY_BASE_URL = "cloud_base_url"
        private const val KEY_MODEL = "cloud_model"
        private const val KEY_API_KEY = "api_key"
        private const val MAX_KEY_LENGTH = 400
    }
}
