package com.ultimate.codecreator.model

import java.util.UUID

enum class SupportedLanguage(
    val displayName: String,
    val fileExtension: String,
    val quickWords: List<String>
) {
    PYTHON("Python", "py", listOf("Binary Search", "Fibonacci", "Quicksort", "LRU Cache", "REST API", "File I/O", "Thread Pool")),
    HTML_CSS("HTML/CSS/JS", "html", listOf("Login Form", "Glassmorphism Card", "Calculator")),
    JAVA("Java", "java", listOf("Binary Search", "Fibonacci", "LRU Cache", "Thread Pool", "HTTP Client", "File I/O")),
    KOTLIN("Kotlin", "kt", listOf("Binary Search", "Fibonacci", "LRU Cache", "Coroutines", "HTTP Client", "File I/O")),
    JAVASCRIPT("JavaScript", "js", listOf("Binary Search", "LRU Cache", "Fetch API", "Async Tasks", "File I/O")),
    CPP("C++", "cpp", listOf("Binary Search", "Fibonacci", "Thread Pool", "File I/O", "Stack")),
    SQL("SQL", "sql", listOf("CRUD", "Create Table", "Inner Join", "Window Rank")),
    GO("Go", "go", listOf("Binary Search", "REST API", "Worker Pool", "HTTP Client", "File I/O")),
    RUST("Rust", "rs", listOf("Binary Search", "Fibonacci", "Thread Pool", "File I/O", "Stack")),
    C_SHARP("C#", "cs", listOf("Binary Search", "Fibonacci", "LRU Cache", "Async Tasks", "File I/O")),
    TYPESCRIPT("TypeScript", "ts", listOf("Binary Search", "LRU Cache", "Fetch API", "Async Tasks"));

    companion object {
        fun fromExtension(extension: String): SupportedLanguage? =
            entries.firstOrNull { it.fileExtension.equals(extension, ignoreCase = true) }

        fun fromName(name: String): SupportedLanguage? =
            entries.firstOrNull { it.name == name }
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * How code generation is sourced.
 * - AUTO: the app decides. Confident bundled recipes stay offline; novel requests
 *   use cloud AI only when a provider is configured and the device is online,
 *   falling back to offline on any failure.
 * - OFFLINE: always use the on-device recipe engine. No network is used.
 * - CLOUD: always use the configured cloud provider; surface an error if it is
 *   unavailable rather than silently substituting a recipe.
 */
enum class GenerationMode { AUTO, OFFLINE, CLOUD }

/** Where a generation result actually came from, for honest labelling in the UI. */
enum class GenerationSource { OFFLINE_RECIPE, OFFLINE_FALLBACK, CLOUD_AI }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val previewJavaScriptEnabled: Boolean = false,
    val editorFontSizeSp: Int = 13,
    val editorWordWrap: Boolean = true,
    val generationMode: GenerationMode = GenerationMode.AUTO
)

data class RecipeParameter(
    val key: String,
    val label: String,
    val defaultValue: String,
    val validationPattern: String,
    val helpText: String
)

data class RecipeInfo(
    val id: String,
    val title: String,
    val explanation: String,
    val keywords: List<String>,
    val languages: List<SupportedLanguage>,
    val dependencies: Map<SupportedLanguage, List<String>> = emptyMap(),
    val safetyNotes: List<String> = emptyList(),
    val parameters: List<RecipeParameter> = emptyList()
)

data class GenerationResult(
    val code: String,
    val intentDetected: String,
    val explanation: String,
    val language: SupportedLanguage,
    val isTemplateFallback: Boolean = false,
    val recipeId: String? = null,
    val confidence: Float = if (isTemplateFallback) 0.2f else 0.9f,
    val alternatives: List<String> = emptyList(),
    val appliedParameters: Map<String, String> = emptyMap(),
    val source: GenerationSource = if (isTemplateFallback) GenerationSource.OFFLINE_FALLBACK else GenerationSource.OFFLINE_RECIPE,
    val model: String? = null
)

enum class Severity { ERROR, WARNING, OPTIMIZATION }

data class CodeError(
    val line: Int,
    val severity: Severity,
    val description: String,
    val fixSuggestion: String
)

data class DebugResult(
    val hasErrors: Boolean,
    val errors: List<CodeError>,
    val fixedCode: String,
    val analysisSummary: String
)

data class SavedSnippet(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val language: SupportedLanguage,
    val code: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val updatedAt: Long = dateAdded,
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false
)
