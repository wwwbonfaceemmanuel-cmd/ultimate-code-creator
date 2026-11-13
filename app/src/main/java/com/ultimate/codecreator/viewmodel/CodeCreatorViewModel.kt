package com.ultimate.codecreator.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ultimate.codecreator.data.CloudSettingsStore
import com.ultimate.codecreator.data.KeystoreSecretStore
import com.ultimate.codecreator.data.PreferencesSettingsStore
import com.ultimate.codecreator.data.PrefsCloudSettingsStore
import com.ultimate.codecreator.data.RoomSnippetRepository
import com.ultimate.codecreator.data.SettingsStore
import com.ultimate.codecreator.data.SnippetPackCodec
import com.ultimate.codecreator.data.SnippetStore
import com.ultimate.codecreator.data.local.AppDatabase
import com.ultimate.codecreator.engine.AndroidNetworkMonitor
import com.ultimate.codecreator.engine.GenerationCoordinator
import com.ultimate.codecreator.engine.OfflineCodeGenerator
import com.ultimate.codecreator.engine.OfflineCodeLinter
import com.ultimate.codecreator.engine.OpenAiCloudGenerator
import com.ultimate.codecreator.model.AppSettings
import com.ultimate.codecreator.model.CloudConfig
import com.ultimate.codecreator.model.CloudGenerationException
import com.ultimate.codecreator.model.DebugResult
import com.ultimate.codecreator.model.GenerationMode
import com.ultimate.codecreator.model.GenerationResult
import com.ultimate.codecreator.model.SavedSnippet
import com.ultimate.codecreator.model.SupportedLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class CodeCreatorViewModel(
    private val store: SnippetStore,
    private val settingsStore: SettingsStore,
    private val cloudSettings: CloudSettingsStore,
    private val coordinator: GenerationCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    companion object {
        const val MAX_EDITOR_LENGTH = 200_000

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val database = AppDatabase.get(appContext)
                val legacyPrefs = appContext.getSharedPreferences("offline_snippet_store", Context.MODE_PRIVATE)
                val settingsPrefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val cloudPrefs = appContext.getSharedPreferences("cloud_settings", Context.MODE_PRIVATE)
                val cloudSettings = PrefsCloudSettingsStore(cloudPrefs, KeystoreSecretStore(appContext))
                val httpClient = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .callTimeout(90, TimeUnit.SECONDS)
                    .build()
                val coordinator = GenerationCoordinator(
                    offline = OfflineCodeGenerator(),
                    cloud = OpenAiCloudGenerator(httpClient),
                    cloudSettings = cloudSettings,
                    network = AndroidNetworkMonitor(appContext)
                )
                CodeCreatorViewModel(
                    store = RoomSnippetRepository(database.snippetDao(), legacyPrefs),
                    settingsStore = PreferencesSettingsStore(settingsPrefs),
                    cloudSettings = cloudSettings,
                    coordinator = coordinator
                )
            }
        }
    }

    private val _generationResult = MutableStateFlow<GenerationResult?>(null)
    val generationResult: StateFlow<GenerationResult?> = _generationResult.asStateFlow()
    private val _debugResult = MutableStateFlow<DebugResult?>(null)
    val debugResult: StateFlow<DebugResult?> = _debugResult.asStateFlow()
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    private val _isDebugging = MutableStateFlow(false)
    val isDebugging: StateFlow<Boolean> = _isDebugging.asStateFlow()
    private val _isTestingCloud = MutableStateFlow(false)
    val isTestingCloud: StateFlow<Boolean> = _isTestingCloud.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _snippetQuery = MutableStateFlow("")
    val snippetQuery: StateFlow<String> = _snippetQuery.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val cloudConfig: StateFlow<CloudConfig> = cloudSettings.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, CloudConfig())

    val snippets: StateFlow<List<SavedSnippet>> = _snippetQuery
        .flatMapLatest { store.observeSnippets(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(ioDispatcher) {
            try {
                val imported = store.migrateLegacyIfNeeded()
                if (imported > 0) _message.value = "Migrated $imported legacy snippet(s) to the new database."
            } catch (_: Exception) {
                _message.value = "Legacy snippets could not be migrated. They were left untouched."
            }
        }
    }

    fun setSnippetQuery(query: String) { _snippetQuery.value = query.take(120) }
    fun consumeMessage() { _message.value = null }
    fun clearDebugResult() { _debugResult.value = null }

    /** Mode-aware generation: the coordinator chooses offline vs cloud per settings. */
    fun generateCode(prompt: String, language: SupportedLanguage) {
        runGeneration(prompt, language, settings.value.generationMode, ioDispatcher)
    }

    /** Forces the on-device engine regardless of the selected mode. */
    fun generateCodeOffline(prompt: String, language: SupportedLanguage) {
        runGeneration(prompt, language, GenerationMode.OFFLINE, defaultDispatcher)
    }

    private fun runGeneration(prompt: String, language: SupportedLanguage, mode: GenerationMode, dispatcher: CoroutineDispatcher) {
        if (prompt.isBlank()) {
            _message.value = "Enter an instruction or tap a keyword first."
            return
        }
        if (_isGenerating.value) return
        _isGenerating.value = true
        viewModelScope.launch(dispatcher) {
            try {
                _generationResult.value = coordinator.generate(prompt, language, mode)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: CloudGenerationException) {
                _message.value = exception.message ?: "Cloud generation failed."
            } catch (exception: IllegalArgumentException) {
                _message.value = exception.message ?: "Invalid instruction."
            } catch (_: Exception) {
                _message.value = "Could not generate this snippet. Try a shorter instruction or Offline mode."
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun setGenerationMode(mode: GenerationMode) {
        updateSettings { it.copy(generationMode = mode) }
    }

    fun updateCloudEndpoint(providerLabel: String, baseUrl: String, model: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                cloudSettings.updateEndpoint(providerLabel, baseUrl, model)
                _message.value = "Cloud endpoint saved."
            } catch (exception: IllegalArgumentException) {
                _message.value = exception.message ?: "Invalid cloud endpoint."
            } catch (_: Exception) {
                _message.value = "Could not save the cloud endpoint."
            }
        }
    }

    fun setApiKey(apiKey: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                cloudSettings.setApiKey(apiKey)
                _message.value = "API key saved securely on this device."
            } catch (exception: IllegalArgumentException) {
                _message.value = exception.message ?: "Invalid API key."
            } catch (_: Exception) {
                _message.value = "Could not save the API key."
            }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch(ioDispatcher) {
            try {
                cloudSettings.clearApiKey()
                _message.value = "API key removed from this device."
            } catch (_: Exception) {
                _message.value = "Could not remove the API key."
            }
        }
    }

    fun testCloudConnection() {
        if (_isTestingCloud.value) return
        _isTestingCloud.value = true
        viewModelScope.launch(ioDispatcher) {
            try {
                _message.value = coordinator.testConnection()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: CloudGenerationException) {
                _message.value = exception.message ?: "Cloud connection test failed."
            } catch (_: Exception) {
                _message.value = "Cloud connection test failed."
            } finally {
                _isTestingCloud.value = false
            }
        }
    }

    fun debugCodeOffline(code: String, language: SupportedLanguage) {
        if (code.isBlank()) {
            _message.value = "Paste code to analyze first."
            return
        }
        if (_isDebugging.value) return
        _isDebugging.value = true
        viewModelScope.launch(defaultDispatcher) {
            try {
                _debugResult.value = OfflineCodeLinter.analyzeAndFix(code, language)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IllegalArgumentException) {
                _message.value = exception.message ?: "Invalid code input."
            } catch (_: Exception) {
                _message.value = "Could not analyze this code. Try a smaller snippet."
            } finally {
                _isDebugging.value = false
            }
        }
    }

    fun saveSnippet(title: String, code: String, language: SupportedLanguage, tags: List<String> = emptyList()) {
        if (code.isBlank()) return
        viewModelScope.launch(ioDispatcher) {
            try {
                store.save(
                    SavedSnippet(
                        title = title.trim().ifEmpty { "Untitled snippet" }.take(80),
                        language = language,
                        code = code.take(MAX_EDITOR_LENGTH),
                        tags = tags.map { it.trim().take(30) }.filter { it.isNotBlank() }.take(10)
                    )
                )
                _message.value = "Snippet saved on this device."
            } catch (_: Exception) {
                _message.value = "Could not save snippet."
            }
        }
    }

    fun updateSnippet(snippet: SavedSnippet) {
        viewModelScope.launch(ioDispatcher) {
            try {
                store.save(snippet.copy(updatedAt = System.currentTimeMillis(), title = snippet.title.take(80)))
                _message.value = "Snippet updated."
            } catch (_: Exception) {
                _message.value = "Could not update snippet."
            }
        }
    }

    fun renameSnippet(id: String, title: String, tags: List<String>) {
        viewModelScope.launch(ioDispatcher) {
            val existing = store.findById(id) ?: return@launch
            store.save(existing.copy(
                title = title.trim().ifEmpty { existing.title }.take(80),
                tags = tags.map { it.trim().take(30) }.filter { it.isNotBlank() }.take(10),
                updatedAt = System.currentTimeMillis()
            ))
            _message.value = "Snippet updated."
        }
    }

    fun duplicateSnippet(id: String) {
        viewModelScope.launch(ioDispatcher) {
            val existing = store.findById(id) ?: return@launch
            val now = System.currentTimeMillis()
            store.save(existing.copy(id = UUID.randomUUID().toString(), title = (existing.title + " copy").take(80), dateAdded = now, updatedAt = now))
            _message.value = "Snippet duplicated."
        }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch(ioDispatcher) {
            val existing = store.findById(id) ?: return@launch
            store.save(existing.copy(favorite = !existing.favorite, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteSnippet(id: String) {
        viewModelScope.launch(ioDispatcher) {
            store.delete(id)
            _message.value = "Snippet deleted."
        }
    }

    fun deleteAllSnippets() {
        viewModelScope.launch(ioDispatcher) {
            store.deleteAll()
            _message.value = "All snippets deleted."
        }
    }

    suspend fun buildExportPack(appVersion: String): String = withContext(ioDispatcher) {
        SnippetPackCodec.encode(store.getAll(), appVersion)
    }

    fun importPack(json: String, replaceExisting: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val decoded = SnippetPackCodec.decode(json)
                if (replaceExisting) {
                    store.replaceAll(decoded)
                } else {
                    val merged = store.getAll().associateBy { it.id }.toMutableMap()
                    decoded.forEach { merged[it.id] = it }
                    store.replaceAll(merged.values.toList())
                }
                _message.value = "Imported ${decoded.size} snippet(s)."
            } catch (exception: IllegalArgumentException) {
                _message.value = exception.message ?: "Invalid snippet pack."
            } catch (_: Exception) {
                _message.value = "Could not import snippet pack."
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch(ioDispatcher) { settingsStore.update(transform) }
    }
}
