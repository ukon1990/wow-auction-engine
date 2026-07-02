# Backend worker

Attach to **backend / API slice** worker prompts.

## Read first (mandatory)

| File | Why |
| ---- | --- |
| [kotlin-backend.md](kotlin-backend.md) | Kotlin conventions, layering, companion-object rules |
| [../../kotlin-best-practices/SKILL.md](../../kotlin-best-practices/SKILL.md) | Scope functions, backing properties, visibility |
| [../../spring-boot-testing/SKILL.md](../../spring-boot-testing/SKILL.md) | TDD, Testcontainers, integration tests |
| [migrations.md](migrations.md) | Flyway, indexes, pagination |
| [logging.md](logging.md) | MDC, correlation ID, structured logs |
| [authz.md](authz.md) | Authz matrix, service-layer enforcement |
| [../github-issue-planning/touch-map.md](../github-issue-planning/touch-map.md) | Where code goes |
| [openapi.md](openapi.md) | Contract-first API |
| [api-errors.md](api-errors.md) | ProblemDetails |
| [code-quality.md](code-quality.md) | KISS, DRY, similar-code reuse |
| [testing.md](testing.md) | Fixtures, integration tests |
| [lint-format.md](lint-format.md) | ktlint apply + check before handoff |

## Lint & format (before handoff)

```bash
cd backend && ./mvnw spotless:apply
cd backend && ./mvnw ktlint:check
```

See [lint-format.md](lint-format.md).

```text
Implement slice <slice#> for GitHub issue #<N>: <title>
Issue URL: <url>
Branch: <suggestedBranch> (stay on this branch)

## Your slice
<name>: <one-paragraph scope>

## Acceptance criteria (this slice only)
- [ ] …

## Paths to touch
- …

## Stack
- Kotlin Spring Boot under backend/
- AGENTS.md for Docker/Testcontainers

## Kotlin & architecture (mandatory)
Read focus/kotlin-backend.md and skills kotlin-best-practices + spring-boot-testing.
Apply focus/migrations.md, focus/logging.md, focus/authz.md for this slice.

## Rules
- Implement only this slice; do not expand scope
- Do not git commit unless instructed
- OpenAPI-first: spec → generate → controller — focus/openapi.md
- KISS/DRY: minimal clear code — focus/code-quality.md
- Tests: simple, readable; fixtures/builders — focus/testing.md
- API errors: ProblemDetails — focus/api-errors.md
- Format + lint before handoff — focus/lint-format.md (`spotless:apply` + `ktlint:check`)

## Deliverable
1. Summary of implementation
2. Files changed
3. Authz matrix for new/changed endpoints (if any)
4. Tests run and outcomes
5. `ktlint:check` outcome
6. Blockers
```
