# Reviewers

Pass the relevant section to each review subagent. Read focus files cited in each prompt.

## Bugbot

```text
Full Repository Path: <absolute repo path>
Diff: branch changes
Custom Instructions: Issue #<N>. Focus: correctness, edge cases, error paths, authz.
Read: .agents/skills/github-issue-work/focus/edge-cases.md
Flag: missing test coverage; generic errors where ProblemDetails expected (focus/api-errors.md).
Flag: untested validation branches.
If diff touches app.routes.ts, resolvers, or public features: read focus/seo.md (SSR, TransferState, meta).
If diff touches frontend UI/templates: read focus/a11y.md (labels, keyboard, focus, errors).
```

## Security review

```text
Full Repository Path: <absolute repo path>
Diff: branch changes
Custom Instructions: Issue #<N> touches <authz/API/UI>. Authz, XSS, injection, secrets, IDOR, unsafe URLs.
Read:
- .agents/skills/github-issue-work/focus/edge-cases.md
- .agents/skills/github-issue-work/focus/authz.md

Authz (required for new/changed endpoints):
1. Build authz matrix: anonymous / owner / other user / admin per route.
2. Verify service-layer enforcement — UI guards are not sufficient.
3. Verify list endpoints filter unauthorized rows.
4. Verify 403 vs 404 matches existing feature conventions.
5. Flag missing authz integration tests.
```

## Maintainability + quality

```text
Full Repository Path: <absolute repo path>
Issue: #<N> — <title>
Diff scope: branch changes against default branch

Read and apply:
- focus/kotlin-backend.md (Kotlin conventions, layering, companion objects — if backend/ touched)
- kotlin-best-practices/SKILL.md (scope functions, backing properties — if backend/ touched)
- focus/lint-format.md (if backend/ or frontend/ touched)
- focus/code-quality.md (KISS, DRY, LOC, cyclomatic complexity, similar-code search, layering)
- focus/testing.md (coverage, readable tests, fixtures/builders, authz tests)
- focus/api-errors.md (ProblemDetails on POST/PATCH/PUT)
- focus/edge-cases.md
- focus/openapi.md (if API contract touched)
- focus/migrations.md (if SQL/schema/repositories touched)
- focus/logging.md (if routes, BFF, or request handling touched)
- focus/a11y.md (if frontend UI touched)
- frontend/best-practices.md (if frontend touched)
- ui-catalog.md (if frontend changed)

Similar code & reusability (required):
1. Search the repo for logic/UI resembling the changed code — not only files in the diff.
2. Compare against existing services, validators, helpers, fixtures/builders, and ui-* components.
3. Flag reimplemented logic that should call or extend existing shared code.
4. Flag new code that should be extracted for reuse when the same pattern exists elsewhere.
5. Cite both locations (new + existing) and name the concrete reuse target.

File size & component responsibility (required):
1. Check changed production files for length, growth, and mixed responsibilities.
2. Flag frontend page components that own page orchestration plus substantial modal/form/search/list/card behavior.
3. Use focus/code-quality.md thresholds: review at ~500-700 LOC, > ~300 LOC growth, or 3+ responsibilities; do not wait for >1000 LOC.
4. Prefer feature-local extraction before shared ui-* extraction unless cross-feature reuse is clear.
5. Cite the line count, the responsibilities being mixed, and a concrete split suggestion.

OpenAPI (when API touched):
1. Spec paths/schemas updated; run bash scripts/openapi/generate-all.sh (or verify CI would pass).
2. Controller implements generated *Api; frontend uses generated *Service.

Logging (when routes/endpoints/BFF touched):
1. BFF logs page visits and proxied /api calls with correlationId.
2. Backend sets MDC (correlationId, userId, path) per request.
3. Correlation ID propagated BFF → backend.
4. Errors and authz denials logged with MDC context; no secrets/PII.

Output: markdown table — Severity | Location | Finding | Suggested fix
Severity: Critical | High | Medium | Low
```

## i18n reviewer

**When:** frontend slice ran, or diff touches `frontend/` templates/TS with user-visible strings.

Read [frontend-i18n/SKILL.md](../../frontend-i18n/SKILL.md) and [focus/i18n.md](i18n.md).

```text
Full Repository Path: <absolute repo path>
Issue: #<N> — <title>
Diff scope: branch changes against default branch

Verify Angular UI translations:
1. Run: cd frontend && bun run extract-i18n
2. If new strings in diff but messages.xlf unchanged → FAIL (extract not run)
3. Fail on errors: unit.missing, unit.missing_target, unit.stale_source, unit.obsolete, registry.*
4. Report warnings: unit.untranslated (note allowlist exceptions)

Output:
- ok: true/false
- Per-locale error counts
- Table: code | locale | unitId | sourcePreview | fix hint
- Pass / fail
```

## Pass criteria (orchestrator)

Review **passes** when no Critical/High remain and:

- Edge cases from issue AC covered or deferred
- Tests: meaningful, readable, fixtures used; authz cases covered ([testing.md](testing.md))
- Code: KISS/DRY, low complexity; Kotlin conventions; no unnecessary companion objects ([kotlin-backend.md](kotlin-backend.md), [code-quality.md](code-quality.md))
- File/component size: long or fast-growing files reviewed; split or justified ([code-quality.md](code-quality.md))
- API contract: spec + generated client in sync; controllers implement OpenAPI ([openapi.md](openapi.md))
- Authz: matrix verified for new/changed endpoints ([authz.md](authz.md))
- Migrations: indexes, safe backfill, pagination ([migrations.md](migrations.md))
- Logging: BFF route/API telemetry + backend MDC with correlationId ([logging.md](logging.md))
- Mutations: ProblemDetails + field `errors[]` ([api-errors.md](api-errors.md))
- Frontend: UX brief, ui-\* components, Storybook; best-practices.md (signals, control flow, Signal Forms)
- a11y: labels, keyboard, focus, error exposure ([a11y.md](a11y.md))
- Public routes: SSR metadata + TransferState ([seo.md](seo.md))
- Lint/format: `ktlint:check` / `bun run lint` clean for touched stacks ([lint-format.md](lint-format.md))
- Translations: `i18n:check` clean when UI strings changed ([i18n.md](i18n.md))
