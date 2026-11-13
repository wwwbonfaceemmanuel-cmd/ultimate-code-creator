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
    TYPESCRIPT("TypeScript", "ts", listOf("Binary Search", "LRU Cache", "Fetch API", "Async Tasks"))
}

data class GenerationResult(
    val code: String,
    val intentDetected: String,
    val explanation: String,
    val language: SupportedLanguage,
    val isTemplateFallback: Boolean = false
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
    val dateAdded: Long = System.currentTimeMillis()
)
