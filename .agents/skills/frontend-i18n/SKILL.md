---
name: frontend-i18n
description: >-
  Audit and update Angular UI translations (XLF catalogs) for the wow-auction-engine
  frontend. Use when adding UI strings, syncing locale files after extract-i18n,
  or working on localization.
---

# Frontend UI i18n (Angular XLF)

wow-auction-engine localizes UI strings with **Angular compile-time i18n** and XLIFF 1.2
catalogs under `frontend/src/locale/`.

## Quick reference

| Item | Location |
|------|----------|
| Source catalog (English) | `frontend/src/locale/messages.xlf` |
| Locale catalogs | `frontend/src/locale/messages.{locale}.xlf` |
| Angular build locale map | `frontend/angular.json` → `projects.EE.i18n` |
| Runtime locale service | `frontend/src/app/core/services/locale.service.ts` |

**Supported locales:** `en` (source), `de`, `es`, `fr`, `it`, `ko`, `pt`, `ru`, `zh`

Build fails on missing translations (`i18nMissingTranslation: error` in `angular.json`).

## Marking strings in code

**Templates:** add Angular i18n attributes:

```html
<h1 i18n>Market Browser</h1>
<input placeholder="Search items" i18n-placeholder />
```

**TypeScript:** use `$localize`:

```ts
return $localize`No results found.`;
```

Run from `frontend/`:

```bash
bun run extract-i18n
```

This updates `messages.xlf` with new/changed trans-units (IDs are auto-generated hashes).

## Standard workflow

From `frontend/`:

```bash
# 1. Extract new/changed strings from code
bun run extract-i18n

# 2. Copy new trans-units into each locale file and add <target> translations
#    Edit frontend/src/locale/messages.{locale}.xlf

# 3. Verify build compiles with all locales
bun run build
```

## Adding or fixing translations

1. Run `bun run extract-i18n` to refresh `messages.xlf`.
2. For each locale file (`messages.de.xlf`, etc.), ensure every trans-unit from the source has a `<target>`.
3. Match units by `id` attribute — do not invent trans-unit IDs.
4. For **stale strings**: English `<source>` changed — re-translate the `<target>`.
5. Re-run `bun run build` to verify all locales compile.

## Adding a new locale

1. Add the locale → file mapping in `frontend/angular.json` → `projects.EE.i18n.locales`
2. Create `frontend/src/locale/messages.{code}.xlf` by copying structure from `messages.xlf`
3. Translate all `<target>` elements
4. Wire locale into `locale.service.ts` if needed for runtime switching

## Pitfalls

- **Trans-unit IDs are hashes** — match by `id` from `messages.xlf`, not invented key names.
- **Verify with build** after translation changes: `cd frontend && bun run build`
- Game data locale (items, realms) comes from the **API** (`game-locale` schema) — not XLF files.
