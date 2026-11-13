# Threat model

## Assets

- user snippets and tags;
- local settings;
- the cloud API key (encrypted at rest with the Android Keystore);
- app integrity;
- device resources (CPU/memory/battery);
- user trust in generated code.

## Trust boundaries

1. **User input → generator/linter**
   - Prompts and code are untrusted text.
   - Length limits reduce memory/CPU risk.
   - Generated code is labelled and must be reviewed.

2. **HTML preview → WebView**
   - Preview HTML/JS is untrusted.
   - Network, file, content, downloads, popups, and native bridges are blocked.
   - JavaScript is off by default.
   - Renderer-crash handling and stop control reduce UI impact.
   - Hostile JS can still consume CPU/memory; preview is not a hardened sandbox.

3. **Imported snippet pack → local database**
   - JSON is validated for format version, language, size, and count.
   - Import never executes code.
   - Merge/replace is explicit.

4. **Export/share → external apps/storage**
   - User explicitly chooses destination.
   - Exported packs are plain JSON and may contain secrets.
   - Temporary share files are app-cache files exposed through FileProvider with read-only grant.

5. **Cloud AI generation → remote provider**
   - Opt-in only. Offline mode sends nothing; Auto contacts a provider only when one is configured and the device is online.
   - Only the instruction and target language are sent, over HTTPS, to the user-configured endpoint. Cleartext is disabled.
   - The API key is encrypted with a non-exportable Android Keystore key (AES/GCM); it is used only for the Authorization header and is never logged, exported, or included in snippet packs.
   - Cloud output is untrusted, labelled as AI, and must be reviewed. It is never auto-executed.
   - The provider sees the prompt; users must trust their chosen provider and avoid sending secrets in instructions.

## Out of scope

- Protecting against a compromised Android OS.
- Proving generated code correct or secure.
- Running arbitrary native code safely on-device.
- Encrypting exported packs (future optional feature).

## Security requirements for future changes

- Internet permission exists only for opt-in cloud AI; keep core features working offline and keep cloud disabled until configured.
- Never log or export the cloud API key; keep it in Keystore-backed encrypted storage.
- Do not add JavaScript bridges without explicit keep rules and security review.
- Do not log snippet contents by default.
- Keep preview JavaScript opt-in.
- Fuzz malformed code, HTML previews, and imported packs before release.
