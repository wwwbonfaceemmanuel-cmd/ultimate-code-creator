# Capability matrix

| Capability | Status | Notes |
|---|---:|---|
| Offline recipe generation | Implemented | Deterministic keyword/recipe matching, not AI. |
| Cloud AI generation | Implemented | Opt-in, bring-your-own-key, OpenAI-compatible endpoints (OpenAI/OpenRouter/Groq/custom). |
| Generation mode selection | Implemented | Auto / Offline / Cloud. Auto keeps confident recipes offline and uses cloud for novel requests, falling back to offline on failure. |
| Secure API key storage | Implemented | Android Keystore AES/GCM; key never exported or logged. |
| 11 language targets | Implemented | Recipe coverage varies by language. |
| Honest starter fallback | Implemented | Never mislabels another language as the selected one. |
| Recipe catalog | Implemented | Metadata, dependencies, safety notes, parameters. |
| Parameterized recipes | Partial | LRU capacity parameter implemented; registry supports more. |
| Structural linting | Implemented | Heuristic, comment/string-aware, conservative repairs. |
| Full AST parsing | Not bundled | Parser abstraction and Tree-sitter plan included. |
| Syntax highlighting | Implemented | Lightweight tokenizer; not a full parser. |
| Editor undo/redo/find/replace | Implemented | Local editor state, bounded history. |
| File open/save/share | Implemented | Android Storage Access Framework and FileProvider. |
| Room snippet storage | Implemented | Search, tags, favorites, rename, duplicate, delete-all. |
| Import/export packs | Implemented | Versioned JSON, validated, merge or replace. |
| Legacy migration | Implemented | One-time SharedPreferences to Room migration. |
| HTML preview | Implemented | Network/file/content blocked; JS off by default. |
| Preview process isolation | Partial | WebView renderer-crash handling; not a separate app service process. |
| Themes/settings | Implemented | System/light/dark, font size, word wrap, JS toggle. |
| Tablet navigation rail | Implemented | Wide-screen baseline; more adaptive layouts can follow. |
| Unit tests | Implemented | Engine, codec, ViewModel. |
| Instrumentation tests | Implemented | Room DAO and one Compose flow; emulator CI matrix. |
| Signed release pipeline | Scaffolded | Requires your keystore/secrets and Play Console setup. |
| Store listing/assets | Manual | Screenshots, feature graphic, policy review required. |
| Analytics/crash reporting | Not included | Offline-first; add only with privacy review and consent. |
| On-device AI model | Not included | No large model is downloaded; cloud AI uses a remote provider only. |
