# Contributing

## Setup

1. Install JDK 17 and Android SDK 36.
2. Run `./gradlew testDebugUnitTest lintDebug assembleDebug`.
3. Use a feature branch and open a PR to `main`.

## Expectations

- Keep core features offline-first.
- Do not add network permissions or telemetry without a privacy review.
- Add tests for engine, storage, and ViewModel changes.
- Update docs and changelog for user-visible changes.
- Keep generated recipes honest: label demos, dependencies, and fallbacks.
- Run instrumentation tests when changing UI/storage.

## Code style

- Kotlin official style.
- Prefer small composables and injectable stores/dispatchers.
- Keep user-facing strings in resources.
- Avoid logging snippet contents.
