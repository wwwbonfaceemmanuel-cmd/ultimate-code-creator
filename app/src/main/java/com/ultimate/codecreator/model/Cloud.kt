package com.ultimate.codecreator.model

/**
 * Non-secret cloud configuration surfaced to the UI. The API key itself is held
 * separately in encrypted storage; only [hasApiKey] is exposed here.
 */
data class CloudConfig(
    val providerLabel: String = "Custom",
    val baseUrl: String = "",
    val model: String = "",
    val hasApiKey: Boolean = false
) {
    val isConfigured: Boolean
        get() = hasApiKey && isValidBaseUrl(baseUrl) && model.isNotBlank()

    companion object {
        /** Cloud endpoints must use HTTPS. Cleartext is never allowed for keys/prompts. */
        fun isValidBaseUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (!trimmed.startsWith("https://", ignoreCase = true)) return false
            return runCatching {
                val uri = java.net.URI(trimmed)
                !uri.host.isNullOrBlank()
            }.getOrDefault(false)
        }

        /** Normalize to a scheme://host/path root without a trailing slash. */
        fun normalizeBaseUrl(url: String): String = url.trim().trimEnd('/')
    }
}

/** A fully-resolved endpoint used for a single cloud request (includes the secret). */
data class CloudEndpoint(
    val baseUrl: String,
    val model: String,
    val apiKey: String
)

/** A selectable provider preset that pre-fills the endpoint fields. */
data class CloudProviderPreset(
    val label: String,
    val baseUrl: String,
    val defaultModel: String
)

object CloudProviderPresets {
    val all: List<CloudProviderPreset> = listOf(
        CloudProviderPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        CloudProviderPreset("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
        CloudProviderPreset("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        CloudProviderPreset("Custom", "", "")
    )
}

/** User-facing cloud failure with a safe, actionable message (never contains the key). */
class CloudGenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)
