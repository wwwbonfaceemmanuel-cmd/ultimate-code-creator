# Release guide

## Versioning

- `versionName` defaults to `0.2.0` and can be overridden with `-PversionNameOverride=x.y.z` or `VERSION_NAME`.
- `versionCode` defaults to `2` and can be overridden with `-PversionCodeOverride=N` or `VERSION_CODE`.
- Tag releases as `vX.Y.Z`.

## Local unsigned release

```bash
./gradlew assembleRelease bundleRelease
```

Outputs:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
app/build/outputs/bundle/release/app-release.aab
```

## Signed release

Provide these environment variables:

```text
ANDROID_KEYSTORE_PATH=/absolute/path/to/release.keystore
ANDROID_KEYSTORE_PASSWORD=...
ANDROID_KEY_ALIAS=...
ANDROID_KEY_PASSWORD=...
```

Then:

```bash
./gradlew assembleRelease bundleRelease
```

Do not commit keystores or passwords.

## GitHub release workflow

`.github/workflows/release.yml` expects repository secrets:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Create the base64 keystore locally:

```bash
base64 -w 0 release.keystore > release.keystore.base64
```

On macOS:

```bash
base64 -i release.keystore -o release.keystore.base64
```

Push a tag:

```bash
git tag v0.2.0
git push origin v0.2.0
```

The workflow builds signed APK/AAB artifacts and creates a GitHub release.

## Play release

Use the signed AAB with Play App Signing. Complete [`PLAY_CHECKLIST.md`](PLAY_CHECKLIST.md) before submission.
