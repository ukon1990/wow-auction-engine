---
name: review-bugbot-fallback
description: >-
  Manual Bugbot-style code review when the bugbot subagent is unavailable (quota,
  non-Cursor agent, offline). Use for /review-bugbot, "run bugbot locally", or
  when Task bugbot fails with usage limits.
disable-model-invocation: true
---

# Bugbot review (fallback — no subagent)

Use when **`bugbot` subagent cannot run** (usage limits, other IDE/agent). The
**parent agent performs the review directly** — do not launch `Task` with
`subagent_type: bugbot`.

For subagent review when quota allows, use
`~/.cursor/skills-cursor/review-bugbot/SKILL.md` instead.

## Scope

Review **branch changes** (default) or **uncommitted changes** only when the
user asks.

| Diff mode      | Git command                                                   |
| -------------- | ------------------------------------------------------------- |
| Branch vs base | `git diff <base>...HEAD` and `git log <base>..HEAD --oneline` |
| Uncommitted    | `git diff` and `git diff --cached`                            |

**Base branch:** infer from PR (`gh pr view --json baseRefName`) or user hint.
wow-auction-engine uses **`master`** as the default branch.

## Read first

| File                                                                                 | Why                            |
| ------------------------------------------------------------------------------------ | ------------------------------ |
| [../github-issue-work/focus/reviewers.md](../github-issue-work/focus/reviewers.md)   | Bugbot section                 |
| [../github-issue-work/focus/edge-cases.md](../github-issue-work/focus/edge-cases.md) | Validation, empty states       |
| [../github-issue-work/focus/api-errors.md](../github-issue-work/focus/api-errors.md) | ProblemDetails                 |
| [../github-issue-work/focus/a11y.md](../github-issue-work/focus/a11y.md)             | If frontend UI changed         |
| [../github-issue-work/focus/seo.md](../github-issue-work/focus/seo.md)               | If public routes / SSR changed |
| [../github-issue-work/focus/testing.md](../github-issue-work/focus/testing.md)       | Test gaps                      |

## Checklist

1. **Correctness** — happy path, edge cases, error paths, off-by-one, null/empty
2. **Authz** — service-layer enforcement (UI guards insufficient)
3. **Tests** — meaningful coverage for new behavior; authz rejection cases
4. **API errors** — ProblemDetails + field `errors[]` on validation failures
5. **Frontend** — labels, keyboard, focus, loading/error/empty states
6. **Public routes** — SSR metadata, TransferState if applicable

## Output

One-line status, then a table (severity high → low):

| Severity | Location (file:line) | Finding |

Severity: **Critical**, **High**, **Medium**, **Low**, **Info**.

If no issues: `Bugbot found no bugs.`

Do **not** fix findings unless the user asks.

## Example invocation

User: "Run bugbot locally on this branch vs master"

```bash
gh pr view --json baseRefName,headRefName 2>/dev/null || true
git diff master...HEAD --stat
git diff master...HEAD -- backend/ frontend/
```

Then review changed files and produce the table.
