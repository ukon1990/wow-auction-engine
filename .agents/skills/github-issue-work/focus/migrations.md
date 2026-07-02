# Migrations & data access

Pass to **backend workers** and **maintainability reviewer** when schema or SQL changes.

Location: `backend/src/main/resources/db/migration/V{N}__<description>.sql`

## Flyway rules

- One logical change per file; forward-only
- Backfill existing rows before `NOT NULL` without default
- Add **indexes** for new filter/join/sort columns used in repositories
- **Unique constraints** where the app assumes uniqueness (username, slug per scope, etc.)
- Do not rely on app startup to repair schema

## Query & API shape

- No unbounded list queries — paginate in SQL and OpenAPI ([openapi.md](openapi.md))
- Avoid N+1: batch fetches or joins in repository layer
- Match read/write split patterns in [touch-map.md](../../github-issue-planning/touch-map.md)

## Reviewer checks

- [ ] Migration file present for every schema change
- [ ] Indexes for new query patterns (grep repository SQL)
- [ ] Safe rollout for existing data (defaults, backfill, nullable transition)
- [ ] List endpoints bounded (max page size, cursor/offset)
- [ ] Layering: controller → service → repository (no controller → repository)
