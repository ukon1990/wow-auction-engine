---
name: review-security-fallback
description: >-
  Manual security review when the security-review subagent is unavailable
  (quota, non-Cursor agent). Use for /review-security, "run security review
  locally", or when Task security-review fails with usage limits.
disable-model-invocation: true
---

# Security review (fallback — no subagent)

Use when **`security-review` subagent cannot run**. The **parent agent reviews
directly** — do not launch `Task` with `subagent_type: security-review`.

For subagent review when quota allows, use
`~/.cursor/skills-cursor/review-security/SKILL.md` instead.

## Scope

Same diff rules as [review-bugbot-fallback](../review-bugbot-fallback/SKILL.md):
branch vs base (default `master` for wow-auction-engine) or uncommitted when requested.

```bash
git diff <base>...HEAD --stat
gh pr view --json baseRefName 2>/dev/null || true
```

## Read first

| File                                                                                 | Why                     |
| ------------------------------------------------------------------------------------ | ----------------------- |
| [../github-issue-work/focus/authz.md](../github-issue-work/focus/authz.md)           | Authz matrix (required) |
| [../github-issue-work/focus/edge-cases.md](../github-issue-work/focus/edge-cases.md) | Abuse scenarios         |
| [../github-issue-work/focus/reviewers.md](../github-issue-work/focus/reviewers.md)   | Security section        |

## Required: authz matrix

For each **new/changed endpoint**, build and verify:

| Endpoint | Anonymous | Owner | Other user | Admin | Deny (403/404) |
| -------- | --------- | ----- | ---------- | ----- | -------------- |

Rules:

- Service layer enforces — not UI guards alone
- List endpoints filter unauthorized rows (no IDOR via pagination)
- 403 vs 404 matches existing feature conventions
- Integration tests for rejected actors

## Also check

- **Injection** — SQL (parameterized?), path/query/body in logs
- **IDOR** — UUID/token ownership before read/write
- **Token abuse** — share tokens, brute force, expired/revoked
- **XSS** — user content rendered in UI; `localStorage` parsing
- **Secrets** — no credentials in diff
- **DoS** — unbounded work per request (N+1, full re-aggregation on PATCH)

## Output

One-line status, then table (severity high → low):

| Severity | Location (file:line) | Finding |

If no issues: `Security review found no issues.`

Do **not** fix findings unless the user asks.

## Example invocation

User: "Security review PR #186 vs master"

```bash
gh pr diff 186 --name-only
git diff master...HEAD -- backend/src/main/kotlin/ openapi/
```

Read controllers + services for changed routes; fill authz matrix; report gaps.
