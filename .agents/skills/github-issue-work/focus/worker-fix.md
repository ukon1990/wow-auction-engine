# Fix worker

Use when review **fails**. Resume original worker (context ≤50%) or new worker with this block.

Also attach the focus files relevant to findings (e.g. `testing.md`, `api-errors.md`).

```text
Fix review findings for GitHub issue #<N>: <title>
Issue URL: <url>
Branch: <suggestedBranch>

## Slice scope
<original slice + acceptance criteria>

## Review: FAILED
Fix all Critical and High. Medium if in your slice.

## Findings
| Severity | Location | Finding | Suggested fix | Reviewer |
|----------|----------|---------|---------------|----------|
| … | … | … | … | … |

## Edge cases still missing
- …

## Files involved
- …

## Previous implementation summary
- …

## Focus docs to apply
- <list paths under focus/ e.g. kotlin-backend.md, lint-format.md, testing.md, api-errors.md, authz.md, openapi.md, logging.md, a11y.md, seo.md>

## Rules
- Fix only findings in scope
- Do not commit unless instructed
- Add/update tests per finding — simple tests, fixtures/builders
- Run tests and report results

## Deliverable
1. Finding → fix mapping
2. Files changed
3. Test results
4. Unresolved items
```
