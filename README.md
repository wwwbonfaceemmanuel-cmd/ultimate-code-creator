# Ultimate Code Creator

Offline-first Android code snippet workbench built with Kotlin, Jetpack Compose, and Room, with a deterministic on-device recipe engine and **optional cloud AI generation**.

> Professional positioning: create, inspect, repair, save, import/export, and preview code snippets. Works fully offline; cloud AI is opt-in with your own provider key. Curated recipes are labelled clearly, AI output is labelled as AI, and unsupported offline requests produce starters, not fake solutions.

## Generation modes

The app chooses where code comes from based on the mode you pick on the Generate screen:

- **Auto** (default): confident bundled recipes stay offline (fast and private). Novel requests use cloud AI only when you have configured a provider and you are online, and fall back to the offline result if the cloud call fails. With no provider configured, Auto behaves exactly like Offline.
- **Offline**: only the on-device recipe engine is used. No network.
- **Cloud AI**: always uses your configured provider; surfaces a clear error if it is unavailable.

### Cloud AI (optional, bring your own key)

Configure a provider in **Settings → AI & Cloud**: pick a preset (OpenAI, OpenRouter, Groq, or Custom), set the base URL and model, and paste your API key. Any OpenAI-compatible `/chat/completions` endpoint works.

- The API key is encrypted with a non-exportable Android Keystore key (AES/GCM). It is never exported, logged, or included in snippet packs.
- Only your instruction and target language are sent, over HTTPS, to the provider you choose. The developer receives nothing.
- Cloud output is AI-generated and unverified — review it before use.

## What works

- **Offline recipe synthesis** for algorithms, caches, stacks, trees, file I/O, concurrency, HTTP clients, REST services, SQL, and HTML components.
- **Optional cloud AI generation** with Auto/Offline/Cloud modes, bring-your-own-key OpenAI-compatible providers, and Keystore-encrypted key storage.
- **11 language targets:** Python, HTML/CSS/JavaScript, Java, Kotlin, JavaScript, C++, SQL, Go, Rust, C#, TypeScript.
- **Recipe catalog** with keywords, language coverage, dependencies, safety notes, confidence, alternatives, and parameterized LRU capacity (`capacity=10`).
- **Honest fallbacks:** unsupported requests produce a labelled starter in the selected language.
- **Editor workflows:** syntax highlighting, undo/redo, find/replace, whitespace cleanup, conservative newline indentation, file open/save/share.
- **Structural linting:** comment/string-aware delimiter checks, Python header colons and mixed-indentation warnings, conservative Java/C++/C# semicolon warnings, HTML tag nesting, embedded CSS/JS checks, and repair diff review.
- **Local snippet library:** Room/SQLite storage, search, tags, favorites, rename, duplicate, delete, delete-all, versioned JSON import/export, and legacy migration.
- **Restricted HTML preview:** network blocked in the WebView (interception + strict CSP), blocked file/content access and downloads, JavaScript disabled by default, stop control, and renderer-crash handling.
- **Settings:** system/light/dark theme, editor font size, word wrap, preview JavaScript toggle.
- **Tablet/landscape baseline:** navigation rail on wide screens.

## Important limitations

The offline engine is deterministic recipe matching, not AI. Optional cloud AI uses a remote provider and its output is unverified. This is **not** a full IDE, compiler, or language server. The linter is heuristic and does not prove correctness or safety. A clean lint result is not a guarantee.

Generated HTTP/API examples may need dependencies or network access when run elsewhere. The login recipe is a UI demo, not production authentication.

Tree-sitter native parsing is not bundled yet. The code includes a parser abstraction and an integration plan in [`docs/TREE_SITTER_PLAN.md`](docs/TREE_SITTER_PLAN.md).

## Build

Requirements:

- JDK 17
- Android SDK Platform 36 and Build Tools 36.0.0
- Android 8.0 / API 26 or newer for installation
- Internet access for the first Gradle/dependency download

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release builds enable R8 and resource shrinking. Without signing environment variables, CI produces unsigned release artifacts for inspection. For signed releases, see [`docs/RELEASE.md`](docs/RELEASE.md).

## CI

GitHub Actions runs on `main`, `arena/**` working branches, pull requests to `main`, and `v*` tags:

- unit tests;
- Android lint;
- debug APK;
- unsigned release APK/AAB;
- instrumentation tests on API 26 and API 36 emulators;
- optional dependency review on PRs;
- optional dependency graph submission on `main`.

### Enable optional dependency checks

Dependency review/submission is **off by default** because this repository currently has Dependency graph disabled. The repository owner must:

1. Enable **Dependency graph** under **Settings → Code security and analysis**.
2. Create the repository Actions variable `DEPENDENCY_GRAPH_ENABLED` with the value `true` under **Settings → Secrets and variables → Actions → Variables**.

Until both steps are complete, these jobs are skipped; a skip is not a security review. The app build/test jobs do not depend on this setting.

Signed release workflow: [`.github/workflows/release.yml`](.github/workflows/release.yml).

## Project structure

```text
app/src/main/java/com/ultimate/codecreator/
├── MainActivity.kt
├── model/                 # language, recipe, snippet, settings models
├── engine/                # recipes, linter, highlighter, diff, formatter, preview policy
│   └── parser/            # parser backend abstraction
├── data/                  # Room repository, settings, import/export codec, legacy migration
│   └── local/             # Room entity/DAO/database
├── viewmodel/             # state and use-cases
└── ui/                    # Compose theme, components, screens
```

## Documentation

- [Capability matrix](docs/CAPABILITIES.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Threat model](docs/THREAT_MODEL.md)
- [Testing](docs/TESTING.md)
- [Recipe authoring](docs/RECIPE_AUTHORING.md)
- [Release guide](docs/RELEASE.md)
- [Play Store checklist](docs/PLAY_CHECKLIST.md)
- [Tree-sitter plan](docs/TREE_SITTER_PLAN.md)
- [Privacy](PRIVACY.md)
- [Security](SECURITY.md)
- [Changelog](CHANGELOG.md)

## License

Apache-2.0. See [LICENSE](LICENSE).
