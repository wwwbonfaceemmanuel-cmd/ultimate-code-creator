package com.ultimate.codecreator.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ultimate.codecreator.data.PreferencesSettingsStore
import com.ultimate.codecreator.data.RoomSnippetRepository
import com.ultimate.codecreator.data.SettingsStore
import com.ultimate.codecreator.data.SnippetPackCodec
import com.ultimate.codecreator.data.SnippetStore
import com.ultimate.codecreator.data.local.AppDatabase
import com.ultimate.codecreator.engine.OfflineCodeLinter
import com.ultimate.codecreator.engine.OfflineCodeSynthesizer
import com.ultimate.codecreator.model.AppSettings
import com.ultimate.codecreator.model.DebugResult
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CodeCreatorViewModel(
    private val store: SnippetStore,
    private val settingsStore: SettingsStore,
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
                CodeCreatorViewModel(
                    store = RoomSnippetRepository(database.snippetDao(), legacyPrefs),
                    settingsStore = PreferencesSettingsStore(settingsPrefs)
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
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _snippetQuery = MutableStateFlow("")
    val snippetQuery: StateFlow<String> = _snippetQuery.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

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

    fun generateCodeOffline(prompt: String, language: SupportedLanguage) {
        if (prompt.isBlank()) {
            _message.value = "Enter an instruction or tap a keyword first."
            return
        }
        if (_isGenerating.value) return
        _isGenerating.value = true
        viewModelScope.launch(defaultDispatcher) {
            try {
                _generationResult.value = OfflineCodeSynthesizer.synthesize(prompt, language)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IllegalArgumentException) {
                _message.value = exception.message ?: "Invalid instruction."
            } catch (_: Exception) {
                _message.value = "Could not generate this snippet. Try a shorter instruction."
            } finally {
                _isGenerating.value = false
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
