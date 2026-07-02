# Codebase touch map (wow-auction-engine)

Companion to [topics.json](../../../scripts/github-issues/planning/topics.json). Use when scaffolding is not enough and
you need layering conventions or “where does X usually go?”.

## Standard backend feature flow

New persisted entity or API surface:

1. **Migration** — `backend/src/main/resources/db/migration/V{N}__<name>.sql`
2. **DBO** — `dbo/rds/<domain>/`
3. **Domain** — `domain/<domain>/`
4. **Repository** — `repository/rds/<Entity>Repository.kt`
5. **Service** — `service/<Entity>Service.kt` (validation, authz, orchestration)
6. **OpenAPI** — `openapi/paths/` + `openapi/components/schemas/`, then `bash scripts/openapi/generate-all.sh`
7. **Controller** — implements generated `*Api` interface from OpenAPI
8. **Tests** — `backend/src/test/.../*IntegrationTest.kt` (Testcontainers MariaDB via `IntegrationTestBase`)

Existing patterns to copy:

| Pattern        | Example                                              |
| -------------- | ---------------------------------------------------- |
| Market search  | `AuctionMarketController` / `AuctionMarketSearchService` |
| Crafting       | `CraftingController` / `CraftingMarketSearchService` |
| Admin-only     | `AdminController`, `AdminControllerAdvice`             |
| Read-heavy SQL | `AuctionMarketSearchRepository`, `CraftingMarketSearchRepository` |
| Blizzard sync  | `integration/blizzard/*`, `schedules/*`              |

## Standard frontend feature flow

1. **Route** — `frontend/src/app/app.routes.ts` (+ guards if needed)
2. **Feature folder** — `features/<name>/` with standalone components
3. **API** — call generated `*ApiService` from `api/generated/`
4. **UI primitives** — reuse `ethereal-ui` components (do not duplicate form controls)
5. **i18n** — `i18n` attributes / `$localize`; extract with `cd frontend && bun run extract-i18n`
6. **Tests** — colocated `*.spec.ts`; Vitest via `ng test`

SSR/BFF auth lives under `frontend/src/server/`.

## Domain → primary entry points

### Auth & users

- Login/profile: `features/login/`, `features/profile/`
- BFF session store: `frontend/src/server/auth/`
- Backend auth: `AuthService.kt`, `SecurityConfig.kt`
- Admin users: `features/admin/user-administration/`, admin OpenAPI paths

### Auction market

- Browser + detail: `features/market-browser/`
- Search API: `AuctionMarketController.kt`, `AuctionMarketSearchService.kt`
- Stats/history repos: `repository/rds/*Stats*`, `*Auction*`

### Crafting

- Browser: `features/crafting/`
- API: `CraftingController.kt`, `CraftingMarketSearchService.kt`
- Recipe sync: `ProfessionRecipeBulkSyncService.kt`, `schedules/ProfessionRecipeSchedule.kt`

### Realms

- Realm picker: `features/select-realm/`
- API: `RealmController.kt`, `ConnectedRealmService.kt`

### Admin

- Status + SQL editor: `features/admin/status/`
- Expansions: `features/admin/expansions/`
- Users: `features/admin/user-administration/`
- Backend: `AdminController.kt`, `service/admin/*`

### Blizzard integration

- API clients: `integration/blizzard/`
- Fixtures: `backend/src/test/resources/blizzard/`, `tools/refresh-fixtures/`

## OpenAPI workflow (required for API changes)

```
edit openapi/
  → bash scripts/openapi/generate-backend.sh   # Kotlin interfaces
  → bash scripts/openapi/generate-frontend.sh  # TypeScript client
  → implement controller methods
  → commit spec + generated output together
```

Or from repo root: `bash scripts/openapi/generate-all.sh`

Never edit `frontend/src/app/api/generated/` by hand.

Entry spec: `openapi/openapi.yml` (split YAML under `paths/` and `components/schemas/`).

## Tests map

| Area                | Location                                              | Notes                |
| ------------------- | ----------------------------------------------------- | -------------------- |
| Backend unit        | `backend/src/test/.../service/*Test.kt`               | Validators, mappers  |
| Backend integration | `backend/src/test/.../controller/*IntegrationTest.kt` | MariaDB Testcontainers |
| Frontend unit       | `frontend/src/app/**/*.spec.ts`                       | Vitest via `ng test` |
| ethereal-ui         | `frontend/ethereal-ui/src/**/*.spec.ts`               | Component tests      |

Run:

```bash
cd backend && ./mvnw test
cd frontend && bun test
        # or bun test
```

## Docs map

| Topic         | Path                    |
| ------------- | ----------------------- |
| Frontend AGENTS | `frontend/AGENTS.md`  |
| ethereal-ui   | `frontend/ethereal-ui/` |
| Infra         | `infra/`                |
| Deploy        | `scripts/deploy/`       |

## Splitting epics

Use `scripts/github-issues/split-feature.mjs`. See
[github-issue-planning/SKILL.md](SKILL.md) and
[github-issues/SKILL.md](../github-issues/SKILL.md).

When implementing new domains, update `scripts/github-issues/planning/topics.json` and
add an audit matcher in `scripts/github-issues/audit/features.json`.
