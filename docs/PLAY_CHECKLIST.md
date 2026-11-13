# Play Store checklist

## Build

- [ ] Current required target API verified in Play Console.
- [ ] Signed AAB produced by release workflow.
- [ ] R8 release build tested on physical devices.
- [ ] versionCode incremented.
- [ ] Release notes written.

## Policy and privacy

- [ ] Privacy policy URL published.
- [ ] Data Safety form matches actual behavior: local-only by default; if cloud AI is enabled, declare that user instructions are sent to a third-party provider.
- [ ] No analytics/crash SDK added without review.
- [ ] Export/share behavior documented.
- [ ] Permissions reviewed: INTERNET and ACCESS_NETWORK_STATE are used only for opt-in cloud AI.

## Store listing

- [ ] App title and short/full description.
- [ ] Phone screenshots.
- [ ] Tablet screenshots if claiming tablet support.
- [ ] Feature graphic.
- [ ] App icon reviewed.
- [ ] Content rating questionnaire completed.
- [ ] Target audience declared.

## Quality

- [ ] API 26 and current API emulator tests pass.
- [ ] Physical device smoke test.
- [ ] Airplane-mode test.
- [ ] Accessibility/font-scaling test.
- [ ] Import/export round-trip test.
- [ ] Preview safety test.
- [ ] Crash/ANR review from internal testing track.

## Operations

- [ ] Internal testing track first.
- [ ] Support contact configured.
- [ ] Security reporting channel configured.
- [ ] Dependency alerts monitored.
