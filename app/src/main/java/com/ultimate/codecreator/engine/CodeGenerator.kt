package com.ultimate.codecreator.engine

import com.ultimate.codecreator.model.GenerationResult
import com.ultimate.codecreator.model.SupportedLanguage

/** A source of generated code. Implementations must be safe to call from a coroutine. */
interface CodeGenerator {
    suspend fun generate(prompt: String, language: SupportedLanguage): GenerationResult
}

/** On-device deterministic recipe engine. Never touches the network. */
class OfflineCodeGenerator : CodeGenerator {
    override suspend fun generate(prompt: String, language: SupportedLanguage): GenerationResult =
        OfflineCodeSynthesizer.synthesize(prompt, language)
}
