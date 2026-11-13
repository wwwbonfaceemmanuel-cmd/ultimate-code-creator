# Ultimate Code Creator

Offline-first Android code snippet workbench built with Kotlin, Jetpack Compose, Room, and a deterministic recipe engine.

> Professional positioning: create, inspect, repair, save, import/export, and preview code snippets offline. Curated recipes are labelled clearly. Unsupported requests produce starters, not fake solutions.

## What works

- **Offline recipe synthesis** for algorithms, caches, stacks, trees, file I/O, concurrency, HTTP clients, REST services, SQL, and HTML components.
- **11 language targets:** Python, HTML/CSS/JavaScript, Java, Kotlin, JavaScript, C++, SQL, Go, Rust, C#, TypeScript.
- **Recipe catalog** with keywords, language coverage, dependencies, safety notes, confidence, alternatives, and parameterized LRU capacity (`capacity=10`).
- **Honest fallbacks:** unsupported requests produce a labelled starter in the selected language.
- **Editor workflows:** syntax highlighting, undo/redo, find/replace, whitespace cleanup, conservative newline indentation, file open/save/share.
- **Structural linting:** comment/string-aware delimiter checks, Python header colons and mixed-indentation warnings, conservative Java/C++/C# semicolon warnings, HTML tag nesting, embedded CSS/JS checks, and repair diff review.
- **Local snippet library:** Room/SQLite storage, search, tags, favorites, rename, duplicate, delete, delete-all, versioned JSON import/export, and legacy migration.
- **Restricted HTML preview:** no Internet permission, blocked network/file/content access, blocked downloads, JavaScript disabled by default, stop control, and renderer-crash handling.
- **Settings:** system/light/dark theme, editor font size, word wrap, preview JavaScript toggle.
- **Tablet/landscape baseline:** navigation rail on wide screens.

## Important limitations

This is **not** a general AI code generator, full IDE, compiler, or language server. The linter is heuristic and does not prove correctness or safety. A clean lint result is not a guarantee.

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
- dependency review on PRs;
- dependency graph submission on `main`.

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
