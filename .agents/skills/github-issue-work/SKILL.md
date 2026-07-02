---
name: github-issue-work
description: >-
  Start implementation on a GitHub issue for wow-auction-engine — load the issue, plan
  work splits, run a UI/UX expert before frontend work, launch worker subagents,
  run review subagents (including edge cases, similar-code reuse, authz, logging/MDC, OpenAPI parity,
  and i18n when frontend), fix until review passes, and open PRs using
  `.github/pull_request_template.md` with issue references.
  Use when picking up an issue, implementing a feature, or working from gh issue #N.
  Requires github-issues toolkit; read that skill first for gh setup and stack context.
---

# GitHub issue work (wow-auction-engine)

**Start with:** [github-issues](../github-issues/SKILL.md) for gh auth, stack layout, and lifecycle.

This skill covers **implementing** an existing issue. For filing new issues, use
[github-issue-planning](../github-issue-planning/SKILL.md). For backlog audit, use
[github-issue-triage](../github-issue-triage/SKILL.md).

## Agent roles

| Role             | Who                                                    | Responsibility                                                                  |
| ---------------- | ------------------------------------------------------ | ------------------------------------------------------------------------------- |
| **Orchestrator** | Parent agent (this chat)                               | Load issue, plan slices, confirm with user, launch agents, integrate            |
| **UI/UX expert** | readonly `generalPurpose` or `explore`                 | Pre-work UX brief: flows, existing components, new shared components + stories  |
| **Workers**      | `generalPurpose` subagents                             | Implement one slice each                                                        |
| **Reviewers**    | `bugbot`, `security-review`, readonly `generalPurpose` | Pass/fail; attach `focus/reviewers.md` + standards |
| **i18n reviewer** | readonly `generalPurpose` (if frontend)              | Translations via `i18n:check`; [frontend-i18n](../frontend-i18n/SKILL.md) |
| **Fix workers**  | Resume or new `generalPurpose`                         | `focus/worker-fix.md` + relevant focus files       |

The orchestrator does not implement slices directly; it routes fix work to workers
in the review fix loop (Phase 8).

## Golden rules

1. **Planning before workers** — enter planning mode and present a work split; do not
   launch worker subagents until the user confirms the plan.
2. **Review after workers** — always run review subagents on the combined result;
   work is not done until review **passes** (see Phase 6).
3. **Fix review failures** — assign fixes to the original worker (resume) when
   context is ≤50%; otherwise launch a new worker with a full context handoff.
4. **No surprise commits** — workers may implement; only commit when the user asks.
5. **Respect issue scope** — stay within acceptance criteria; flag scope creep.
6. **Branch naming** — use `suggestedBranch` from `view-issue.mjs`; never ad-hoc names.
7. **UI/UX before frontend work** — run the UI/UX expert when any slice touches
   `frontend/`; frontend workers follow the UX brief and [ui-catalog.md](ui-catalog.md).
8. **PR template is mandatory** — when opening a pull request, read
   `.github/pull_request_template.md` and use it **verbatim** for the PR body (same
   headings, section order, and checklist). Never substitute a custom layout. Always
   reference every implemented GitHub issue in **Links** (and use `Closes #N` /
   `Fixes #N` where appropriate).

## Workflow overview

```
1. Load issue     → view-issue.mjs (parent + sub-issues + blockers + suggestedBranch)
2. Branch         → create <issue-type>/<id>-<title-slug> from suggestedBranch
3. Plan (mode)    → break into slices, assign workers, set order
4. UI/UX expert   → if frontend in scope: flows, components, new shared + stories
5. Confirm        → user approves work split + UX brief
6. Execute        → launch worker subagents (parallel where safe)
7. Review         → bugbot + security + maintainability + i18n (if frontend)
8. Fix loop       → original or new worker until review passes
9. Integrate      → run tests, report status
10. Pull request   → `.github/pull_request_template.md` + issue refs
```

---

## Phase 1 — Load issue

Use the toolkit — **not** raw `gh issue view`:

```bash
gh auth status
node scripts/github-issues/view-issue.mjs <#> --json
```

From the JSON:

- **`parent`** — if set, you are on a sub-issue; read parent for epic context
- **`subIssues`** — if non-empty, you are on an epic; pick one child to implement or plan per child
- **`dependencies.blockedBy`** — do not start until blockers are done (or scope explicitly excludes them)
- **`body`** — acceptance criteria and touch points
- **`suggestedBranch`** — e.g. `feat/76-add-public-profile-fields`

Also check:

- **Touch points** — cross-ref `planning/topics.json` and [touch-map.md](../github-issue-planning/touch-map.md)
- **Overlap** — if this issue duplicates open work, stop and use planning skill

If the issue is an epic with unfiled children (body refs but empty `subIssues`),
suggest [github-issue-planning](../github-issue-planning/SKILL.md) to link or file children.

---

## Phase 2 — Branch

After loading the issue, create a branch from `master` using **`suggestedBranch`**
from `view-issue.mjs --json` (see [github-issues](../github-issues/SKILL.md) Branch names).

```bash
git checkout master && git pull
git checkout -b <suggestedBranch>
```

Confirm the branch name with the user if the inferred `issue-type` looks wrong
(e.g. a `fix:` title filed under `feat`). Do not start workers on `master`.

---

## Phase 3 — Planning mode (required)

**Switch to Plan mode** (`SwitchMode` → `plan`) for the work-split conversation unless
the user already approved a split in this session.

Research before proposing slices:

1. Read issue body acceptance criteria and touch points.
2. Explore affected code (`backend/`, `frontend/`) — copy patterns from similar features.
3. Identify dependencies: schema before API before UI; OpenAPI gen before controllers.
4. Note tests required (`*IntegrationTest.kt`, colocated `*.spec.ts`).

### Slice heuristics

Align with `scripts/github-issues/planning/split-templates.json` when full-stack:

| Order | Slice              | Typical paths                       | Worker                 |
| ----- | ------------------ | ----------------------------------- | ---------------------- |
| 1     | Schema / migration | `backend/.../db/migration/`             | 1 worker               |
| 2     | Domain + API       | `backend/` OpenAPI, service, controller | 1 worker               |
| 3     | Frontend           | `frontend/features/`                | 1 worker               |
| 4     | E2E / i18n         | `e2e/`, XLF catalogs                | 1 worker (if in scope) |

**One worker per independent slice.** Merge tiny slices (e.g. DBO + repository) into
one worker. Never parallelize slices that share files or ordering dependencies.

### Work split template

Present this and fill it in:

```markdown
## Implementation plan (issue #N — title)

### Goal

[One sentence from issue acceptance criteria]

### Slices

| #   | Slice | Owner    | Paths          | Depends on | Parallel? |
| --- | ----- | -------- | -------------- | ---------- | --------- |
| 1   | …     | worker-1 | `backend/...`      | —          | —         |
| 2   | …     | worker-2 | `frontend/...` | 1          | after 1   |

### Workers to launch: **N**

- **worker-1** (generalPurpose): …
- **worker-2** (generalPurpose): …

### UI/UX expert (before workers, if frontend in scope)

- Flows, component mapping, new shared components + Storybook stories
- Output: UX brief → attached to frontend worker prompt

### Reviewers to launch after workers

**Always (3):**

- **bugbot** — correctness, edge cases, error paths, test gaps; SSR/SEO and a11y when UI/routes change
- **security-review** — authz matrix, injection, secrets, abuse scenarios ([focus/authz.md](focus/authz.md))
- **maintainability** — complexity, tests, ProblemDetails, similar-code reuse, OpenAPI, migrations, logging ([focus/reviewers.md](focus/reviewers.md))

**When frontend / UI strings in scope (4th reviewer):**

- **i18n** — run `bun run extract-i18n`; verify all locales ([focus/i18n.md](focus/i18n.md), [frontend-i18n](../frontend-i18n/SKILL.md))

If review fails → fix loop (Phase 8): resume original worker (context ≤50%) or new worker with [focus/worker-fix.md](focus/worker-fix.md).

### Risks / open questions

- …

### Out of scope (for this pass)

- …
```

Iterate until the split is clear. Re-enter planning when the user changes scope.

---

## Phase 4 — UI/UX expert (required when frontend is in scope)

**Skip** this phase for backend-only or API-only issues.

After the work split is drafted (Phase 3) and **before** user confirmation, launch one
readonly **UI/UX expert** subagent (`generalPurpose` or `explore`, `readonly: true`).

The expert assists the orchestrator — it does not implement code. Prompt:
[focus/ui-ux-expert.md](focus/ui-ux-expert.md) + [ui-catalog.md](ui-catalog.md).

### Expert inputs

- Issue body, acceptance criteria, user-facing goals
- Draft slices (especially frontend slice)
- [ui-catalog.md](ui-catalog.md) — existing `ethereal-ui components`
- Similar features (`features/market-browser/`, `features/crafting/`, etc.)

### Expert deliverable — UX brief

```markdown
## UX brief (issue #N)

### User flows
- [Primary flow: entry → action → success/error]
- [Secondary flows, empty states, loading]

### Screen structure
- Routes / sections / modals

### Use existing components
| UI need | Component | Path |
|---------|-----------|------|
| … | `ee-item-stat-card` | `frontend/ethereal-ui/src/lib/components/market/item-stat-card.component.ts` |

### Propose feature-local only
- Components that stay in `features/<name>/` (one-off layout)

### Propose new shared components
| Name | Selector | Rationale | Story file |
|------|----------|-----------|------------|
| … | `ui-…` | Reused across … | `frontend/ethereal-ui/src/lib/components/<name>.stories.ts` or add to `*.stories.ts` |

### Interaction & a11y notes
- Focus order, keyboard, labels, error announcements
- Loading / skeleton / empty / error UX

### Open UX questions
- …
```

Merge the UX brief into the implementation plan. **Frontend worker prompts must
include the UX brief** and [focus/worker-frontend.md](focus/worker-frontend.md).

If the expert proposes new shared components, list them in the work split and
acceptance criteria (component + Storybook story).

---

## Phase 5 — Confirm

Require explicit approval before launching workers.

**Valid:** "go ahead", "launch workers", "start implementation", "confirmed"

**Not valid:** silence, "looks good" without implement intent, further edits

Ask if unclear:

> Ready to launch **N workers** for issue #X? Reply **confirm** or tell me what to change.

Include the **UX brief** in what the user confirms when frontend is in scope.

---

## Phase 6 — Execute workers

Launch workers in **dependency order**. Independent slices at the same tier may run
**in parallel** (single message, multiple `Task` calls).

### Worker subagent defaults

| Setting             | Value                                   |
| ------------------- | --------------------------------------- |
| `subagent_type`     | `generalPurpose`                        |
| `run_in_background` | `false` unless user requests background |
| `readonly`          | `false`                                 |

Each worker prompt must include the matching **focus worker file** plus issue-specific details:

| Slice | Attach |
|-------|--------|
| Backend / API | [focus/worker-backend.md](focus/worker-backend.md), [focus/kotlin-backend.md](focus/kotlin-backend.md), [kotlin-best-practices](../../kotlin-best-practices/SKILL.md), [spring-boot-testing](../../spring-boot-testing/SKILL.md) |
| Frontend | [focus/worker-frontend.md](focus/worker-frontend.md), `frontend/best-practices.md`, [frontend/.agents/skills/angular-developer/SKILL.md](../../../frontend/.agents/skills/angular-developer/SKILL.md) when components/forms/routing/SSR need it |

Shared standards (linked from worker files):

| Topic | File |
|-------|------|
| Kotlin conventions | [focus/kotlin-backend.md](focus/kotlin-backend.md) |
| Lint & format | [focus/lint-format.md](focus/lint-format.md) |
| Tests, fixtures, builders | [focus/testing.md](focus/testing.md) |
| KISS, DRY, cyclomatic complexity | [focus/code-quality.md](focus/code-quality.md) |
| ProblemDetails | [focus/api-errors.md](focus/api-errors.md) |
| OpenAPI contract | [focus/openapi.md](focus/openapi.md) |
| Authz | [focus/authz.md](focus/authz.md) |
| Migrations & queries | [focus/migrations.md](focus/migrations.md) |
| Logging & MDC | [focus/logging.md](focus/logging.md) |
| a11y | [focus/a11y.md](focus/a11y.md) |
| SSR / SEO | [focus/seo.md](focus/seo.md) |

Also include: issue `#`, title, URL, `suggestedBranch`, slice acceptance criteria,
patterns to copy, **do not commit** unless told.

Full index: [reference.md](reference.md)

### After workers finish

1. Collect summaries from each worker — **keep agent IDs**, files touched, and slice mapping for fix routing.
2. Check for conflicts (same files edited by multiple workers).
3. If conflicts or gaps, fix sequentially or launch a follow-up worker — do not skip to review.

---

## Phase 7 — Review

Run **after all workers complete** and conflicts are resolved. Launch reviewers
**in parallel** (single message, multiple `Task` calls).

If `bugbot` or `security-review` subagents fail (usage limits, non-Cursor agent),
use fallback skills instead — parent agent reviews directly (no `Task`):

- [review-local](../review-local/SKILL.md) — runs both
- [review-bugbot-fallback](../review-bugbot-fallback/SKILL.md)
- [review-security-fallback](../review-security-fallback/SKILL.md)

All reviewers must check **edge cases**, **test coverage**, and **error handling** —
not only happy paths.

**Maintainability reviewer** owns KISS, DRY, complexity, test style, ProblemDetails,
**similar-code / reusability**, **OpenAPI parity**, **migrations/query shape**, and
**logging/MDC**. Attach: [focus/reviewers.md](focus/reviewers.md) § Maintainability plus
[focus/testing.md](focus/testing.md), [focus/code-quality.md](focus/code-quality.md),
[focus/api-errors.md](focus/api-errors.md), and when applicable [focus/openapi.md](focus/openapi.md),
[focus/migrations.md](focus/migrations.md), [focus/logging.md](focus/logging.md),
[focus/a11y.md](focus/a11y.md).

**Security reviewer** owns the **authz matrix** per endpoint — attach [focus/authz.md](focus/authz.md).

**Bugbot** — when public routes or frontend UI change, also apply [focus/seo.md](focus/seo.md)
and [focus/a11y.md](focus/a11y.md) via Custom Instructions.

| Reviewer | `subagent_type` | `readonly` | Attach |
|----------|-----------------|------------|--------|
| Bugbot | `bugbot` | `true` | [focus/reviewers.md](focus/reviewers.md) § Bugbot |
| Security | `security-review` | `true` | [focus/reviewers.md](focus/reviewers.md) § Security |
| Maintainability | `generalPurpose` | `true` | focus files above |
| i18n (if frontend) | `generalPurpose` | `true` | [focus/i18n.md](focus/i18n.md), [frontend-i18n](../frontend-i18n/SKILL.md) |

**Bugbot** and **security-review** — use prompt shapes from [focus/reviewers.md](focus/reviewers.md)
and the `review-bugbot` / `review-security` skills (`Diff: branch changes`).

**Maintainability reviewer** — prompt in [focus/reviewers.md](focus/reviewers.md).

**i18n reviewer** — when diff touches `frontend/` UI strings or a frontend slice ran:
read [frontend-i18n](../frontend-i18n/SKILL.md), run `cd frontend && bun run extract-i18n`,
report errors (`unit.missing`, `missing_target`, `stale_source`, `obsolete`, `registry.*`).
Prompt: [focus/reviewers.md](focus/reviewers.md) § i18n.

### Review pass criteria

Review **passes** only when:

- No **Critical** or **High** findings remain (bugs, security, correctness)
- Edge cases called out in issue acceptance criteria are covered or explicitly deferred
- **Test coverage** — meaningful tests; simple and readable; fixtures/builders; authz cases ([focus/testing.md](focus/testing.md))
- **KISS / DRY / complexity** — minimal LOC; Kotlin conventions; no unnecessary companion objects; similar code reused ([focus/kotlin-backend.md](focus/kotlin-backend.md), [focus/code-quality.md](focus/code-quality.md))
- **OpenAPI** — spec, bundle, and generated client in sync; `bash scripts/openapi/generate-all.sh` passes when API touched ([focus/openapi.md](focus/openapi.md))
- **Authz** — matrix verified for new/changed endpoints ([focus/authz.md](focus/authz.md))
- **Migrations** — indexes, safe backfill, bounded lists ([focus/migrations.md](focus/migrations.md))
- **Logging** — BFF route/API telemetry; backend MDC with `correlationId` and `userId`; propagated across hops ([focus/logging.md](focus/logging.md))
- **Errors** — clear messages; ProblemDetails with field `errors[]` ([focus/api-errors.md](focus/api-errors.md))
- **Frontend:** existing `ui-*` components per UX brief; Storybook for new shared components; `frontend/best-practices.md` (signals, `@if`/`@for`, Signal Forms, `inject()`)
- **a11y** — labels, keyboard, focus, error exposure ([focus/a11y.md](focus/a11y.md))
- **Public routes** — SSR metadata + TransferState ([focus/seo.md](focus/seo.md))
- **Lint & format** — `ktlint:check` (backend) and/or `bun run lint` (frontend) pass ([focus/lint-format.md](focus/lint-format.md))
- **Translations** (if frontend): `bun run extract-i18n` passes with no errors; missing/stale targets fixed ([focus/i18n.md](focus/i18n.md))

Review **fails** when any must-fix item remains. Tally findings by severity and map
each finding to the **responsible worker** (by files/slice).

Summarize for the user: pass/fail, finding counts, which worker owns fixes.
**Do not call work done on a failed review** — go to Phase 8.

---

## Phase 8 — Fix loop (until review passes)

When review fails, route fixes to the responsible worker:

### Choose fix agent

| Condition | Action |
|-----------|--------|
| Original worker context **≤50%** | **Resume** that worker (`Task` `resume: <agent-id>`) with fix instructions |
| Original worker context **>50%** | Launch a **new** `generalPurpose` worker with full handoff (below) |
| Finding spans multiple slices | One fix worker per slice, or one worker if tightly coupled |
| Systemic design flaw | Stop — return to Phase 3 (re-plan); ask user |
| UX/component gap | Re-run UI/UX expert (Phase 4) or include UX brief in fix handoff |
| Missing/stale translations | Frontend or fix worker + [focus/i18n.md](focus/i18n.md), [frontend-i18n](../frontend-i18n/SKILL.md) |

### Fix worker handoff (required for resume or new worker)

Include everything the fixer needs without re-discovery:

1. Issue `#`, title, URL, `suggestedBranch`
2. **Slice scope** and acceptance criteria for affected area
3. **Review findings** — full table: Severity | Location | Finding | Suggested fix | Source reviewer
4. **Files to touch** (from finding locations + original worker's file list)
5. **What was already tried** — original worker summary
6. **Edge cases to cover** — explicit list from reviewers + issue body
7. **Do not commit** unless instructed
8. **Deliverable** — summary of fixes, files changed, tests run

Use [focus/worker-fix.md](focus/worker-fix.md) + relevant focus files for finding types.

After fixes:

1. Re-run **all reviewers** (including i18n when frontend) — not just the one that failed.
2. Repeat Phase 7 → Phase 8 until **pass** or user intervenes.
3. Cap at **3 review/fix cycles** — then escalate to user with open findings.

---

## Phase 9 — Integrate

Run only after review **passes**.

1. Run relevant tests:

```bash
cd backend && ./mvnw test          # Docker required
cd backend && ./mvnw ktlint:check
cd frontend && bun test
cd frontend && bun run lint
cd frontend && bun run extract-i18n   # when UI strings changed
bash scripts/openapi/generate-all.sh                   # when API contract changed
```

2. Report: what was implemented, test results, open review items, suggested next steps
   (commit, PR, issue comment).

Optional: comment on the issue with progress (`gh issue comment`).

---

## Phase 10 — Pull request

Run when the user asks to open a PR (after Phase 9 passes, or when they explicitly
request a PR before integration — prefer Phase 9 first).

### Template (mandatory)

**Read** `.github/pull_request_template.md` from the repo root before drafting the PR.
The PR body must follow that file **exactly**:

- Keep every `##` heading, bullet, and checkbox line unchanged in structure and order.
- Fill placeholders with real content; do not remove, rename, or reorder sections.
- Do not use alternate PR layouts (e.g. a bare `## Summary` + bullets only).

### Issue references (required)

List **every** GitHub issue this branch implements in the **Links** section:

- Primary issue from Phase 1 (`view-issue.mjs` — include `#`, title context).
- Parent epic when implementing a sub-issue (reference both).
- Additional sub-issues when an epic PR closes multiple children.

Use GitHub closing keywords where the work fully satisfies the issue:

```markdown
## Links
- Related issue(s): Closes #123, #456
```

For partial work or follow-ups, link without `Closes` (e.g. `Related issue(s): #123`).

### Create the PR

Follow [github-issues](../github-issues/SKILL.md) / user PR workflow: gather `git status`,
diff vs base, and commit history; push if needed; then:

```bash
git push -u origin HEAD
gh pr create --title "<concise title>" --body "$(cat <<'EOF'
## Summary
…

## Type
- [x] Feature
- [ ] Bug Fix
…

## Details
…

## Checklist
- [x] Acceptance criteria met
…

## Links
- Related issue(s): Closes #N
EOF
)"
```

- **Title** — short, imperative; may include `(#N)` when it aids scanability.
- **Body** — filled copy of `.github/pull_request_template.md`; check the correct
  **Type** box(es); mark **Checklist** items honestly from Phase 9 results.
- Return the PR URL to the user.

---

## Quick reference

| Step       | Command / tool                                                  |
| ---------- | --------------------------------------------------------------- |
| Load issue | `node scripts/github-issues/view-issue.mjs <#> --json`          |
| Branch     | `git checkout -b <suggestedBranch>`                             |
| Touch map  | [touch-map.md](../github-issue-planning/touch-map.md)           |
| Plan mode  | `SwitchMode` → `plan`                                           |
| UI/UX      | `Task` → `generalPurpose` or `explore` readonly                 |
| Workers    | `Task` → `generalPurpose` + `focus/worker-*.md`                |
| Reviews    | `Task` → reviewers + `focus/reviewers.md`; + i18n if frontend |
| Fix loop   | `Task` resume or new + `focus/worker-fix.md`                   |
| Pull request | Read `.github/pull_request_template.md`; `gh pr create` with issue refs |

Focus areas: [reference.md](reference.md) · Components: [ui-catalog.md](ui-catalog.md)
