# Code quality

Pass this file to **workers** and **maintainability reviewer**.

## KISS / DRY / minimal LOC

- **KISS** — simplest design that meets acceptance criteria; no speculative abstraction
- **DRY** — extract duplication only when the same semantics repeat 2+ times
- **Minimal LOC** — fewer lines when clarity is preserved; delete dead code and redundant wrappers
- Prefer extending existing services/validators over new parallel hierarchies

## Similar code & reusability (reviewers)

Before approving, **search the codebase** for logic, patterns, or UI that resemble the
changed code — not only within the diff.

1. **Find similar code** — grep or explore for the same domain concepts, validation rules,
   HTTP shapes, mappers, error handling, or UI patterns (buttons, empty states, forms).
2. **Prefer existing shared code** — flag when the diff reimplements something that already
   exists in a service, validator, helper, `ethereal-ui components`, or shared test fixture/builder.
3. **Extract when reuse is warranted** — flag when new code should be moved to a shared
   location so other call sites can use it instead of copying again.
4. **Do not over-abstract** — one-off feature logic can stay local; consolidation is required
   when semantics truly match or drift would cause bugs.

| Signal | Typical action | Severity |
| ------ | -------------- | -------- |
| Duplicate business rule / validation already elsewhere | Reuse or extend existing module | High |
| New helper mirrors an existing util with same inputs/outputs | Consolidate or call existing | High |
| New `ui-*` component duplicates an existing catalog component | Use catalog component per UX brief | High |
| Same pattern in 2+ features (pagination, confirm dialog, field errors) | Extract shared helper/component | Medium |
| Similar but intentionally different semantics | Note in review; no forced merge | Low / none |

Report findings with **both locations** (new code + existing similar code) and a concrete
reuse suggestion (which file/class/component to extend).

## Layering & leftovers

- Controller → service → repository; validation in service/validator
- No debug `console.log`, commented-out blocks, or `TODO` without issue reference
- No shared `ethereal-ui components` without Storybook when introduced in this change

## File size & responsibility

Keep files and components focused enough that reviewers can understand the behavior without
scrolling through unrelated concerns.

- Frontend page components should primarily orchestrate route/page state and compose child
  components. Extract feature-local children when a page also owns substantial modal/form,
  search, card/list rendering, or workflow state.
- Use rough thresholds, not hard limits: start looking for a split when a production component
  approaches ~500-700 LOC, a file grows by > ~300 LOC in one change, or a single component mixes
  3+ responsibilities. Do not wait for files to exceed 1000 LOC.
- Prefer feature-local extraction under `features/<name>/` before shared `ethereal-ui components` unless
  reuse across features is already clear.
- Tests may be longer than production files, but repetitive setup/assertions should move to
  fixtures, builders, or focused specs.
- Reviewers should flag oversized/god files as Medium by default, High when size hides edge cases,
  blocks testing, or mixes responsibilities that are likely to drift.

## Cyclomatic complexity

Reviewers flag **high branching** and **deep nesting** in production code and tests.

| Signal                             | Action                                              |
| ---------------------------------- | --------------------------------------------------- |
| Method with many `if`/`when`/loops | Split or extract; aim for one clear path per helper |
| Nesting > 3 levels                 | Flatten with early return / guard clauses           |
| Long methods (> ~40 lines)         | Extract named helpers                               |
| God classes                        | Move logic to focused service/validator             |

**Rough threshold:** cyclomatic complexity **> 10** per function → refactor or justify.
Reviewers estimate from structure when no metrics tool is run; orchestrator may run
`cd backend && ./mvnw verify` if detekt/detekt complexity is configured later.

## Exception handling

- Catch at boundaries (controller/advice); domain code throws typed exceptions
- Clear, user-safe messages — never swallow errors
- No generic catch-all without logging or rethrow

## Reviewer checks

- [ ] No unnecessary abstraction or scope creep
- [ ] Searched codebase for similar logic/UI; cited matches when found
- [ ] New code reuses existing shared modules/components where semantics match
- [ ] Worthwhile duplication consolidated; one-off local code not forced into abstraction
- [ ] Long files/components reviewed for focused responsibility; large additions split or justified
- [ ] Functions are short and single-purpose
- [ ] Branching complexity reasonable (see table above)
- [ ] Production code and tests both stay readable

See also: [api-errors.md](api-errors.md), [testing.md](testing.md)
