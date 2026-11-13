# Privacy Policy

Ultimate Code Creator is designed as an offline-first Android application.

## Data collected by the app

The app does **not** collect, transmit, or sell personal data to the developer. It does not include analytics, advertising, or crash reporting. Optional cloud AI generation (described below) sends your instruction to a provider **you** configure, only when you enable it.

## Data stored locally

The app stores the following only on your device:

- saved code snippets, titles, tags, favorite state, and timestamps;
- editor and preview settings, including the selected generation mode;
- cloud provider settings (provider label, base URL, model);
- your cloud API key, encrypted with a non-exportable Android Keystore key (AES/GCM);
- temporary files created when you explicitly share a snippet.

Saved snippets are stored in an app-private Room/SQLite database. Temporary share files are stored in the app cache and can be removed by Android or by clearing app storage.

## Network access

Core generation (offline recipes), linting, storage, and preview work without any network access. The app requests the Internet permission solely for **optional, opt-in cloud AI generation**.

- **Offline mode:** no network requests are made.
- **Auto mode:** the app contacts a cloud provider only if you have configured one and the device is online; otherwise it stays offline. Confident bundled recipes always stay offline.
- **Cloud mode:** the app sends your instruction and selected language to the provider you configured.

When cloud generation is used, your instruction and target language are transmitted over HTTPS to the endpoint you set (for example OpenAI, OpenRouter, Groq, or a custom compatible server). That provider processes the request under its own privacy policy and may log or retain it. The developer of this app does not receive your prompts. Do not put secrets in instructions you send to a provider.

Your API key is stored encrypted on your device, used only to authenticate to your chosen provider, and is never sent to the developer, logged, or included in exports. Removing the key in Settings deletes it from the device.

Generated HTTP/API code may require network access **if you run that generated code elsewhere**. The app itself does not run those services.

## Import, export, and sharing

Import/export and sharing happen only when you explicitly choose a file or share target. Exported snippet packs are plain JSON and may contain sensitive code. Store and share them carefully.

## Backups

Android cloud backup is disabled. Uninstalling the app or clearing app storage removes local data. Use the in-app export feature if you want a backup.

## Children

The app is a developer utility and is not directed at children.

## Changes

If future versions add analytics, crash reporting, or additional network features, this policy and the Play Data Safety form must be updated before release, and such features should be opt-in where possible. Because cloud AI transmits user instructions to a third-party provider, the Play Data Safety form must declare this data flow before a store release that enables it.
