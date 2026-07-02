# Lint & format

Pass to **backend workers**, **frontend workers**, and **maintainability reviewer**.

Run formatting and lint **before** marking a slice done or opening a PR. Use the **direct**
command for the stack you changed — do not rely on a repo-root shortcut.

## Backend (`backend/`)

ktlint via Maven plugin:

```bash
cd backend && ./mvnw ktlint:format   # format (apply fixes)
cd backend && ./mvnw ktlint:check    # lint (fail if not formatted)
```

Or from repo root: `bun run format:backend`

- Format **after** editing Kotlin under `backend/src/main/kotlin/` or `backend/src/test/kotlin/`
- OpenAPI YAML changes: review spec consistency ([openapi.md](openapi.md))

## Frontend (`frontend/`)

Prettier + ESLint:

```bash
cd frontend && bun run format   # format (Prettier --write)
cd frontend && bun run lint     # lint (ESLint)
```

Or from repo root: `bun run format:frontend`

- Run on touched `.ts`, `.html`, `.scss`, and config under `frontend/`
- After UI strings: also `bun run extract-i18n` ([i18n.md](i18n.md))

## Worker checklist

- [ ] Format applied for every file changed in the slice
- [ ] Lint/check passes with no errors
- [ ] No committed debug logging or formatting-only noise mixed into unrelated hunks without reason

## Reviewer checks

- [ ] Diff looks ktlint/Prettier-clean (no obvious style drift)
- [ ] CI would pass `ktlint:check` / `bun run lint` for touched areas
