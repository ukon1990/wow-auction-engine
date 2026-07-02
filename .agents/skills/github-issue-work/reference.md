# GitHub issue work — reference index

Pass **focus area** files to workers and reviewers — one topic per file, easy to attach.

## Focus areas (`focus/`)

| File | Pass to | Topics |
|------|---------|--------|
| [worker-backend.md](focus/worker-backend.md) | Backend worker | Kotlin conventions + mandatory focus files |
| [kotlin-backend.md](focus/kotlin-backend.md) | Backend worker + maintainability reviewer | Kotlin style, layering, companion-object rules |
| [kotlin-best-practices/SKILL.md](../kotlin-best-practices/SKILL.md) | Backend worker + reviewer | Scope functions, backing properties, visibility |
| [spring-boot-testing/SKILL.md](../spring-boot-testing/SKILL.md) | Backend worker + reviewer | TDD, Testcontainers, integration tests |
| [worker-frontend.md](focus/worker-frontend.md) | Frontend worker | UX brief + best-practices.md + angular-developer skill |
| [worker-fix.md](focus/worker-fix.md) | Fix worker (resume or new) | Review failure handoff |
| [ui-ux-expert.md](focus/ui-ux-expert.md) | UI/UX subagent (pre-work) | Flows, component mapping |
| [testing.md](focus/testing.md) | Workers + maintainability reviewer | Coverage, fixtures, builders, authz tests |
| [code-quality.md](focus/code-quality.md) | Workers + maintainability reviewer | KISS, DRY, LOC, complexity, similar-code reuse, layering |
| [api-errors.md](focus/api-errors.md) | Backend + frontend + reviewer | ProblemDetails, field `errors[]` |
| [openapi.md](focus/openapi.md) | Backend + frontend + maintainability reviewer | Contract-first, generate, client parity |
| [authz.md](focus/authz.md) | Security + backend + maintainability reviewer | Authz matrix, 403/404, list filtering |
| [migrations.md](focus/migrations.md) | Backend + maintainability reviewer | Flyway, indexes, pagination, N+1 |
| [logging.md](focus/logging.md) | Backend + frontend + maintainability reviewer | BFF telemetry, MDC, correlationId |
| [a11y.md](focus/a11y.md) | Frontend + Bugbot + maintainability reviewer | WCAG, keyboard, labels, focus |
| [seo.md](focus/seo.md) | Frontend + Bugbot + maintainability reviewer | SSR, TransferState, metadata |
| [edge-cases.md](focus/edge-cases.md) | All reviewers | Checklist for Custom Instructions |
| [i18n.md](focus/i18n.md) | Frontend worker + i18n reviewer | XLF translations, `i18n:check` |
| [reviewers.md](focus/reviewers.md) | Orchestrator | Bugbot, security, maintainability, i18n prompts |
| [lint-format.md](focus/lint-format.md) | Backend + frontend workers + reviewer | ktlint/ktlint, Prettier, ESLint |
| [examples.md](focus/examples.md) | Orchestrator | Split patterns, fix routing |

## Other docs

| File | Purpose |
|------|---------|
| [ui-catalog.md](ui-catalog.md) | Existing `ethereal-ui components` + Storybook |
| [SKILL.md](SKILL.md) | Full workflow phases |
| `.github/pull_request_template.md` | Mandatory PR body layout (Phase 10) |

## Orchestrator: what to attach per agent

| Agent | Attach |
|-------|--------|
| UI/UX expert | `focus/ui-ux-expert.md`, `ui-catalog.md` |
| Backend worker | `focus/worker-backend.md` + `focus/kotlin-backend.md` + [kotlin-best-practices](../kotlin-best-practices/SKILL.md) + [spring-boot-testing](../spring-boot-testing/SKILL.md) + slice details |
| Frontend worker | `focus/worker-frontend.md` + UX brief + `frontend/best-practices.md` + `frontend/.agents/skills/angular-developer/SKILL.md` (when needed) |
| Bugbot | `focus/reviewers.md` § Bugbot, `focus/edge-cases.md`; + `focus/seo.md` / `focus/a11y.md` when applicable |
| Security | `focus/reviewers.md` § Security, `focus/authz.md` |
| Maintainability | `focus/reviewers.md` § Maintainability + `focus/testing.md` + `focus/code-quality.md` + `focus/api-errors.md` + `focus/lint-format.md` + conditional focus files (openapi, migrations, logging, a11y) |
| i18n (if frontend) | `focus/reviewers.md` § i18n + `focus/i18n.md` + [frontend-i18n](../frontend-i18n/SKILL.md) |
| Fix worker | `focus/worker-fix.md` + relevant focus files for findings |

Fill `<…>` placeholders in prompt templates with issue-specific context.
