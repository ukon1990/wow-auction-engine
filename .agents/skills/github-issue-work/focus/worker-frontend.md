# Frontend worker

Attach to **frontend slice** worker prompts.

## Read first (mandatory)

| File | Why |
| ---- | --- |
| [../../../../frontend/AGENTS.md](../../../../frontend/AGENTS.md) | Angular standards for this repo |
| [seo.md](seo.md) | SSR, TransferState, metadata on public routes |
| [a11y.md](a11y.md) | WCAG, keyboard, labels, focus |
| [ui-catalog.md](../ui-catalog.md) | Existing ethereal-ui components |
| [openapi.md](openapi.md) | Generated API services only |
| [i18n.md](i18n.md) | XLF / extract pipeline (if UI strings change) |
| [logging.md](logging.md) | BFF route + API proxy telemetry |
| [api-errors.md](api-errors.md) | Map API errors to form fields |
| [code-quality.md](code-quality.md) | KISS, DRY, similar-code reuse |
| [testing.md](testing.md) | Readable specs, mock factories |
| [lint-format.md](lint-format.md) | Prettier + ESLint before handoff |
| UX brief from Phase 4 (paste in prompt) | Layout, components, flows |

## Lint & format (before handoff)

```bash
cd frontend && bun run format
cd frontend && bun run lint
```

See [lint-format.md](lint-format.md).

## Worker prompt template

```text
Implement frontend slice <slice#> for GitHub issue #<N>: <title>
Issue URL: <url>
Branch: <suggestedBranch> (stay on this branch)

## UX brief (follow exactly)
<paste from Phase 4>

## Acceptance criteria (this slice only)
- [ ] …

## Angular standards (mandatory)
Read frontend/AGENTS.md — standalone components, signals, @if/@for, inject(), no ngClass/ngStyle.
Read focus/seo.md for public routes; focus/a11y.md for all UI.

## Component rules
- Reuse ethereal-ui components from UX brief / ui-catalog.md
- Feature-local only where brief says so (`features/<name>/`)
- New shared → `frontend/ethereal-ui/src/lib/components/` + Storybook story (`bun run storybook`)
- Keep page components focused on route/page orchestration
- Generated API services only — focus/openapi.md
- BFF logging for page visits and proxied API calls — focus/logging.md
- Format + lint before handoff — focus/lint-format.md

## Stack
- Angular standalone; OnPush change detection
- i18n: mark strings (`i18n` / `$localize`); run extract-i18n — focus/i18n.md
- SSR: resolver + TransferState on indexable pages — focus/seo.md

## Tests
- Simple readable specs; mock factories — focus/testing.md
- AXE / WCAG expectations per AGENTS.md

## Deliverable
1. Summary
2. Files changed (feature + ethereal-ui + stories + locale/*.xlf if touched)
3. Storybook stories added/updated
4. `bun test` outcomes
5. `bun run lint` outcome
6. `bun run extract-i18n` outcome (if UI strings changed)
7. Blockers
```
