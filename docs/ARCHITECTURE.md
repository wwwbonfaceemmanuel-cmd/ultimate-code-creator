# Architecture

## Layers

1. **Model** — plain Kotlin data classes for languages, recipes, snippets, settings, lint findings, and generation results.
2. **Engine** — deterministic recipe synthesis, heuristic linting, preview policy, highlighting, diffing, and formatting helpers.
3. **Data** — Room database/repository, settings store, validated import/export codec, legacy migration.
4. **ViewModel** — coroutine-based state holder, injectable dispatchers and stores for tests.
5. **UI** — Jetpack Compose screens and components, resource-backed strings, theme/settings-aware rendering.

## Key contracts

- `SnippetStore` abstracts persistence so ViewModel tests can use fakes.
- `SettingsStore` abstracts preferences.
- `SyntaxParser` prepares for a future Tree-sitter backend without changing UI contracts.
- `SnippetPackCodec` defines the versioned local backup format.
- `PreviewSandbox` defines the HTML Content Security Policy used by the WebView.

## Storage

Room database: `ultimate_code_creator.db`, version 1.

Entity: `snippets`

- id, title, language, code, tags, favorite, createdAt, updatedAt.

Tags are stored using a unit-separator delimiter to avoid comma collisions. Search uses SQLite `LIKE` over title, code, and tags.

Legacy SharedPreferences data is migrated once into Room and then removed from the legacy key after successful import.

## Generation

`OfflineCodeSynthesizer` owns a recipe registry. Each recipe has:

- id/title/explanation;
- intent regex;
- language templates;
- keywords;
- dependency metadata;
- safety notes;
- optional parameters.

Unmatched requests produce a language-specific starter and are labelled as fallbacks.

## Linting

`OfflineCodeLinter` masks comments/strings before structural checks. Repairs are conservative:

- missing Python header colons;
- unambiguous trailing delimiters;
- missing HTML end tags when nesting is unambiguous.

Possible semicolon issues are warnings and are not blindly inserted.

## Preview

The WebView:

- blocks non-local network requests via interception and a strict CSP (the app Internet permission is only for opt-in cloud AI, not the preview);
- disables file/content access, downloads, popups, geolocation, and DOM storage;
- exposes no JavaScript bridge;
- disables JavaScript by default;
- handles renderer death and provides a stop control.

This is defense in depth, not a guarantee that hostile JavaScript is safe.
