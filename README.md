# Ultimate Code Creator

A native Android code-snippet workbench built with Kotlin and Jetpack Compose. Its core features run on the device without API keys, cloud inference, downloaded language models, or an Internet permission.

## What works

- **Offline recipe synthesis:** keyword/intent matching selects bundled examples for algorithms, caches, stacks, trees, file I/O, concurrency, HTTP clients, REST services, SQL, and HTML components.
- **11 language targets:** Python, HTML/CSS/JavaScript, Java, Kotlin, JavaScript, C++, SQL, Go, Rust, C#, and TypeScript. Recipe coverage varies by language.
- **Honest fallbacks:** an unsupported request produces a clearly labelled starter in the selected language—not Java or Python code mislabeled as another language.
- **Tap-to-add keywords, copy, and local bookmarks.** Saved snippets remain after restarting the app; deleting the last snippet does not resurrect the defaults.
- **Offline structural linting:** comment/string-aware delimiter checks, Python block-header colons, conservative Java/C++/C# semicolon warnings, HTML tag nesting, and embedded CSS/JavaScript block checks. Findings include line numbers. Safe repairs can be loaded back into the editor.
- **Restricted local HTML preview:** inline HTML/CSS/JavaScript runs in a WebView with no app Internet permission, blocked network requests, disabled file/content access, no native JavaScript bridge, and a restrictive Content Security Policy.

## Important limitations

This is a **deterministic template/recipe engine**, not a general natural-language code generator. It does not understand arbitrary specifications or synthesize novel production-ready applications. Unmatched requests are starting points only.

The linter is a **lightweight heuristic checker**, not a full AST parser, compiler, type checker, or language server. It does not verify indentation, types, imports, semantics, regular-expression grammar, macros, or every HTML5 optional-end-tag rule. Its repairs are deliberately conservative; ambiguous mismatches and possible semicolon omissions are left for review. A clean result does not prove that code is correct.

Generated HTTP/API examples may need a network connection and external dependencies **when you run them elsewhere**. The Android app itself does not contact cloud APIs. The login recipe is a UI demo, not an authentication system. Review generated code before using it.

The HTML preview is restricted but is not a separate hardened execution sandbox. Untrusted JavaScript can still consume CPU or memory; close the preview if necessary.

Snippets are stored in app-private SharedPreferences. Android cloud backup is disabled, and uninstalling the app removes its local data. There is no account or cloud sync.

## Build and run

Requirements:

- Android Studio with Android SDK Platform 34 and Build Tools 34.0.0
- JDK 17
- Android 8.0 / API 26 or newer on the device/emulator
- Internet access for the **first build** to download Gradle, the Android plugin, and dependencies; subsequent builds can use cached tooling

Open this repository's root folder in Android Studio, let Gradle sync, and run the `app` configuration. Or build from a terminal:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The installable debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The GitHub Actions workflow also runs unit tests, Android lint, and a debug build. After a successful run, open **GitHub → Actions → Android build → the latest run → Artifacts → UltimateCodeCreator-debug** to download the APK. APKs and build outputs are intentionally not committed to Git.

This first version uses the supplied compile/target SDK 34. Update and test against current device and app-store requirements before a public production release. Release signing is not configured; the CI artifact is a debug build for testing.

## Project layout

```text
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/ultimate/codecreator/
│       │   │   ├── MainActivity.kt
│       │   │   ├── model/Models.kt
│       │   │   ├── engine/
│       │   │   │   ├── OfflineCodeSynthesizer.kt
│       │   │   │   ├── OfflineCodeLinter.kt
│       │   │   │   └── PreviewSandbox.kt
│       │   │   ├── data/SnippetDatabase.kt
│       │   │   ├── viewmodel/CodeCreatorViewModel.kt
│       │   │   └── ui/
│       │   │       ├── theme/
│       │   │       ├── components/
│       │   │       └── screens/
│       │   └── res/
│       └── test/java/com/ultimate/codecreator/engine/
├── gradle/wrapper/
├── gradlew
├── gradlew.bat
├── build.gradle.kts
├── settings.gradle.kts
└── .github/workflows/android-build.yml
```

## Tests

JVM unit tests cover every language's quick-word recipes, fallback language correctness, comment/string masking, delimiter and colon repairs, HTML/CSS checks, safe semicolon warnings, and preview policy injection. Run them with:

```bash
./gradlew testDebugUnitTest
```

Before release, also test rotation, keyboard resizing, copy/save/delete, airplane-mode operation, and WebView previews on a physical Android device. CI compilation is not a substitute for device testing.
