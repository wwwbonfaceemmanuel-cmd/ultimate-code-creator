# Security Policy

## Reporting a vulnerability

Please do not open public issues for security vulnerabilities. Use GitHub private vulnerability reporting for this repository if enabled, or contact the maintainers through a private channel listed on the repository profile.

Include:

- affected version/commit;
- reproduction steps;
- impact;
- suggested fix, if known.

## Security model

- The app requests no Internet permission.
- HTML preview blocks network requests, file/content access, downloads, popups, and native JavaScript bridges.
- JavaScript in preview is disabled by default and must be explicitly enabled for trusted HTML.
- Snippets are stored in app-private storage. Exported packs are plain JSON and are not encrypted.
- The linter is heuristic and does not prove code safe or correct.
- Generated authentication code is a demo, not a production authentication system.

## Supported versions

Only the latest released version is actively supported unless a separate maintenance policy is published.
