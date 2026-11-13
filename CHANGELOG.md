# Changelog

## 0.3.0 — Optional cloud AI generation

### Added
- Hybrid generation with three modes: **Auto**, **Offline**, and **Cloud AI**. Auto keeps confident bundled recipes offline and uses cloud AI for novel requests when a provider is configured and the device is online, falling back to offline on any failure.
- Bring-your-own-key cloud generation via OpenAI-compatible chat-completions endpoints (OpenAI, OpenRouter, Groq, or custom). Provider presets, base URL/model fields, connection test, and clear result-source labelling (offline recipe / starter / AI model).
- Secure API key storage using a non-exportable Android Keystore key (AES/GCM). The key is never exported, logged, or included in snippet packs.
- Cloud provider settings panel and a generation-mode selector on the Generate screen.
- Tests: generation routing (Auto/Offline/CLOUD, fallback, offline/no-network), cloud client request/response and error handling via MockWebServer, cloud config validation, and a Keystore round-trip instrumentation test.

### Changed
- Added INTERNET and ACCESS_NETWORK_STATE permissions for optional cloud AI only; core features remain fully offline and cloud is off until configured.
- Updated privacy policy, threat model, and capability matrix to describe the optional cloud data flow.

### Known limitations
- Cloud output is AI-generated and unverified; it is labelled and must be reviewed. Offline recipes remain deterministic, not AI.
- No on-device model is downloaded; cloud AI requires a network connection and your own provider key.
- Store release still requires declaring the cloud data flow in the Play Data Safety form, plus your signing keys.

## 0.2.0 — Professional baseline

### Added
- Room/SQLite snippet storage with tags, favorites, search, rename, duplicate, delete-all, and legacy SharedPreferences migration.
- Versioned JSON snippet-pack import/export and file open/save/share workflows.
- Recipe catalog with metadata, dependencies, safety notes, confidence, alternatives, and LRU capacity parameterization.
- Syntax-highlighted editor and viewer, undo/redo, find/replace, whitespace cleanup, conservative newline indentation, and repair diff review.
- Safer HTML preview: JavaScript off by default, stop control, renderer-crash handling, blocked downloads, and explicit file/universal access denial.
- Light/dark/system theme, editor font size, word wrap, settings screen, and tablet navigation rail.
- Unit tests for engine, codec, and ViewModel; instrumentation tests for Room DAO and a core Compose flow.
- CI for main/PR/arena branches, release tags, emulator matrix, and opt-in dependency review/submission (requires repository Dependency graph and the `DEPENDENCY_GRAPH_ENABLED=true` Actions variable).
- Signed release workflow scaffold, Dependabot, issue templates, privacy/security docs, and release/play checklists.

### Changed
- Upgraded Gradle wrapper, Android Gradle Plugin, Kotlin, Compose, Room, and target/compile SDK to a modern baseline.
- Enabled R8/minification and resource shrinking for release builds.
- Extracted primary UI strings into resources and improved accessibility semantics.

### Known limitations
- Generation is still deterministic recipe/template based, not general AI code generation.
- Linting is heuristic, not a full AST/language-server/compiler pipeline.
- Tree-sitter native parsing is prepared as an abstraction/plan but not yet bundled.
- Store release requires your signing keys, Play Console account, screenshots, and final policy review.

## 0.1.0

- Initial offline recipe generator, heuristic linter, snippets, and HTML preview.
