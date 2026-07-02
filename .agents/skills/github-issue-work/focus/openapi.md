# OpenAPI contract

Pass to **backend workers**, **frontend workers**, and **maintainability reviewer** when API
shape changes.

Source: [touch-map.md](../../github-issue-planning/touch-map.md).

## Contract-first flow

1. Edit split YAML — `openapi/paths/`, `openapi/components/schemas/`, `openapi/openapi.yml`
2. Run `bash scripts/openapi/generate-all.sh` (from repo root)
3. Review generated artifacts:
   - Backend Kotlin interfaces (via Maven `generate-sources`)
   - `frontend/src/app/api/generated/**`
4. Implement controller against generated `*Api` interface — do not invent parallel DTOs
5. Frontend calls generated `*ApiService` from `api/generated/` — no hand-rolled `HttpClient` for contract endpoints

## Verification (reviewer / orchestrator)

```bash
bash scripts/openapi/generate-all.sh
git diff --stat   # spec + generated output should be committed together
```

## Reviewer checks

- [ ] Paths/schemas/responses updated for every new or changed endpoint
- [ ] Frontend generated client committed together with spec edits
- [ ] Controller implements generated interface; request/response types match spec
- [ ] Frontend uses generated services and models — no duplicate TS types for API payloads
- [ ] Error responses documented where applicable ([api-errors.md](api-errors.md))
- [ ] List endpoints define pagination parameters and response shape when returning collections

## Anti-patterns

- Controller-only route with no OpenAPI entry
- Manual `HttpClient` in Angular for endpoints that have a generated service
- Spec and implementation drift (status codes, field names, required flags)
