# Recipe authoring

Recipes live in `OfflineCodeSynthesizer`. Each recipe should be treated as a small, reviewed product surface.

## Required metadata

- stable id derived from title;
- clear explanation;
- intent pattern;
- supported language templates;
- dependencies by language;
- safety notes;
- parameters, if any.

## Template rules

1. Generate code in the selected language only.
2. Prefer standard libraries unless the dependency is explicitly documented.
3. Do not embed real credentials or tokens.
4. Mark authentication examples as demos.
5. Mark network/file examples with runtime requirements.
6. Keep examples small enough to review on a phone.
7. Add or update tests when changing a recipe.

## Parameters

Parameters use `{{key}}` placeholders and are extracted from prompts such as:

```text
LRU cache capacity=10
```

Each parameter needs:

- key;
- label;
- default value;
- validation regex;
- help text.

Invalid parameter values must fail with a clear message rather than generating broken code.

## Review checklist

- [ ] Recipe passes structural lint for every supported language.
- [ ] Dependency metadata is accurate.
- [ ] Safety notes are visible in the catalog.
- [ ] Fallback behavior remains honest.
- [ ] Golden/execution tests added where practical.
