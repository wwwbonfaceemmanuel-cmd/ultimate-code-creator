# Testing

## Local commands

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease bundleRelease
```

## Test layers

- **Engine unit tests:** recipe matching, fallbacks, lint repairs, highlighting spans, diff, formatter, preview policy.
- **Codec unit tests:** import/export round-trip, invalid packs, legacy migration parsing.
- **ViewModel unit tests:** generation/save, export/import, settings updates using fake stores and test dispatchers.
- **Instrumentation tests:** Room DAO behavior and one Compose generate flow.
- **CI emulator matrix:** API 26 minimum and API 36 current target.

## Manual release checklist

- Airplane mode: generate, lint, save, search, preview static HTML.
- Trusted HTML with JavaScript enabled: calculator works; network attempts remain blocked.
- Hostile HTML: infinite loop can be stopped; renderer crash does not crash app.
- Import/export: merge and replace round-trip.
- Open/save/share code files.
- Rotation and process death preserve editor/snippet state where expected.
- Font scaling and screen reader navigation.
- Light/dark/system theme contrast.
- Release R8 build installs and runs.

## Future test work

- Screenshot tests.
- Accessibility scanner assertions.
- Fuzzing for linter and preview inputs.
- Migration tests when Room schema version increases.
- Execution tests for more generated recipes in CI.
