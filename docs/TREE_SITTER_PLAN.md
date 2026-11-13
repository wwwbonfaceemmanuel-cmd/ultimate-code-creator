# Tree-sitter integration plan

The current highlighter/linter is heuristic. A professional parser backend should be added incrementally.

## Contract

`engine/parser/SyntaxParser.kt` defines the backend contract:

```kotlin
interface SyntaxParser {
    val isFullAst: Boolean
    fun parse(code: String, language: SupportedLanguage): ParseResult
}
```

`HeuristicSyntaxParser` is the current fallback. A future `TreeSitterSyntaxParser` should implement the same contract.

## Proposed implementation

1. Add a native Android library module or `app/src/main/cpp` CMake target.
2. Vendor or fetch pinned Tree-sitter runtime sources with checksums.
3. Start with 2–3 high-value grammars: Python, JavaScript/TypeScript, HTML.
4. Expose a minimal JNI API:
   - create parser for language;
   - parse UTF-8 text;
   - return error nodes and highlight queries;
   - destroy parser.
5. Run parsing off the main thread with cancellation and input-size limits.
6. Cache parser instances per language.
7. Use Tree-sitter error nodes for syntax findings and highlight queries for editor colouring.
8. Keep heuristic fallback for unsupported languages or native-load failure.

## Acceptance criteria

- No UI freeze on 200 KB inputs.
- Native crash does not crash the app process where avoidable; fallback remains available.
- Grammar versions and checksums are pinned.
- APK size impact is documented.
- Unit/instrumentation tests cover parser success, malformed input, and fallback.

## Why not bundled yet

Native grammars increase build complexity, APK size, and CI time. The abstraction and plan are included so the next sprint can add them without rewriting screens.
