package com.ultimate.codecreator.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultimate.codecreator.data.SnippetRepository
import com.ultimate.codecreator.engine.OfflineCodeLinter
import com.ultimate.codecreator.engine.OfflineCodeSynthesizer
import com.ultimate.codecreator.model.DebugResult
import com.ultimate.codecreator.model.GenerationResult
import com.ultimate.codecreator.model.SavedSnippet
import com.ultimate.codecreator.model.SupportedLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CodeCreatorViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val MAX_EDITOR_LENGTH = 50_000
    }

    private val repository = SnippetRepository(application)
    private val _generationResult = MutableStateFlow<GenerationResult?>(null)
    val generationResult: StateFlow<GenerationResult?> = _generationResult.asStateFlow()
    private val _debugResult = MutableStateFlow<DebugResult?>(null)
    val debugResult: StateFlow<DebugResult?> = _debugResult.asStateFlow()
    private val _snippets = MutableStateFlow<List<SavedSnippet>>(emptyList())
    val snippets: StateFlow<List<SavedSnippet>> = _snippets.asStateFlow()
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    private val _isDebugging = MutableStateFlow(false)
    val isDebugging: StateFlow<Boolean> = _isDebugging.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init { loadSnippets() }

    fun generateCodeOffline(prompt: String, language: SupportedLanguage) {
        if (prompt.isBlank()) {
            _message.value = "Enter an instruction or tap a keyword first."
            return
        }
        if (_isGenerating.value) return
        _isGenerating.value = true
        viewModelScope.launch(Dispatchers.Default) {
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
        viewModelScope.launch(Dispatchers.Default) {
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

    fun clearDebugResult() { _debugResult.value = null }
    fun consumeMessage() { _message.value = null }

    fun saveSnippet(title: String, code: String, language: SupportedLanguage) {
        if (code.isBlank()) return
        val snippet = SavedSnippet(title = title.trim().ifEmpty { "Untitled snippet" }.take(80), code = code, language = language)
        repository.save(snippet)
        loadSnippets()
        _message.value = "Snippet saved on this device."
    }

    fun deleteSnippet(id: String) {
        repository.delete(id)
        loadSnippets()
        _message.value = "Snippet deleted."
    }

    fun loadSnippets() { _snippets.value = repository.getAll() }
}
