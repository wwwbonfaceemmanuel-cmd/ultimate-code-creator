package com.ultimate.codecreator.engine

import com.ultimate.codecreator.data.CloudSettingsStore
import com.ultimate.codecreator.model.CloudGenerationException
import com.ultimate.codecreator.model.GenerationMode
import com.ultimate.codecreator.model.GenerationResult
import com.ultimate.codecreator.model.SupportedLanguage
import kotlinx.coroutines.CancellationException

/**
 * Decides where code comes from based on the user's [GenerationMode]:
 *
 * - OFFLINE: always the on-device recipe engine.
 * - CLOUD: always the configured provider; errors surface instead of silently
 *   substituting a recipe.
 * - AUTO: confident bundled recipes stay offline; novel/low-confidence requests
 *   use cloud AI when a provider is configured and the device is online, and fall
 *   back to the offline result on any cloud failure.
 *
 * Offline is always available, so the app keeps working with no network/key.
 */
class GenerationCoordinator(
    private val offline: CodeGenerator,
    private val cloud: CloudGenerator,
    private val cloudSettings: CloudSettingsStore,
    private val network: NetworkMonitor
) {
    suspend fun generate(prompt: String, language: SupportedLanguage, mode: GenerationMode): GenerationResult {
        return when (mode) {
            GenerationMode.OFFLINE -> offline.generate(prompt, language)
            GenerationMode.CLOUD -> {
                val endpoint = cloudSettings.endpoint()
                    ?: throw CloudGenerationException("Cloud is not configured. Add a provider and API key in Settings, or switch to Offline/Auto.")
                if (!network.isOnline()) throw CloudGenerationException("No network connection. Connect to the internet or switch to Offline/Auto.")
                cloud.generate(prompt, language, endpoint)
            }
            GenerationMode.AUTO -> auto(prompt, language)
        }
    }

    /** Sends a tiny request to verify the configured provider/key without generating user code. */
    suspend fun testConnection(): String {
        val endpoint = cloudSettings.endpoint()
            ?: throw CloudGenerationException("Configure a provider and API key first.")
        if (!network.isOnline()) throw CloudGenerationException("No network connection. Connect to the internet and try again.")
        val result = cloud.generate("Return a one-line hello world program.", SupportedLanguage.PYTHON, endpoint)
        return "Connected to ${endpoint.model}. Received a ${result.code.length}-character sample response."
    }

    private suspend fun auto(prompt: String, language: SupportedLanguage): GenerationResult {
        val local = offline.generate(prompt, language)
        val confidentRecipe = !local.isTemplateFallback && local.confidence >= CONFIDENCE_THRESHOLD
        if (confidentRecipe) return local

        val endpoint = cloudSettings.endpoint() ?: return local
        if (!network.isOnline()) return local
        return try {
            cloud.generate(prompt, language, endpoint)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: CloudGenerationException) {
            local.copy(explanation = "${local.explanation} Cloud AI was unavailable (${exception.message}) — showing the offline result.")
        } catch (exception: Exception) {
            local.copy(explanation = "${local.explanation} Cloud AI was unavailable — showing the offline result.")
        }
    }

    companion object {
        const val CONFIDENCE_THRESHOLD = 0.9f
    }
}
