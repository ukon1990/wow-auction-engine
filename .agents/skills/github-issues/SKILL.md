---
name: github-issues
description: >-
  Shared GitHub issue toolkit for wow-auction-engine — gh CLI setup, scripts, labels,
  and the plan → file → implement → audit lifecycle. Use before github-issue-planning
  or github-issue-triage; when unsure which skill applies; or when maintaining
  scripts/github-issues/ config and libs.
---

# GitHub issues (wow-auction-engine toolkit)

Hub skill for **planning** and **triage**. Child skills are thin workflows;
**all scripts and config live here** in one place.

## Which skill when?

| Goal                                         | Skill                                                      | Script                                    |
| -------------------------------------------- | ---------------------------------------------------------- | ----------------------------------------- |
| Scope a feature, touch points, split epic    | [github-issue-planning](../github-issue-planning/SKILL.md) | `scaffold-issue.mjs`, `split-feature.mjs` |
| Implement an existing issue (workers + review) | [github-issue-work](../github-issue-work/SKILL.md)       | `view-issue.mjs`                          |
| Check if issues are done, close shipped work | [github-issue-triage](../github-issue-triage/SKILL.md)     | `audit.mjs`                               |
| gh auth, labels, layout, lifecycle           | **this skill**                                             | —                                         |

Child skills **do not duplicate scripts** — they call into `scripts/github-issues/`.

## Lifecycle

```
discuss + draft plan → overlap check → user confirms → create/update issues (gh + labels)
    → implement (github-issue-work: plan slices → workers → review)
    → audit (features.json probes) → close when implemented
```

Planning requires **explicit user confirmation** before any GitHub write — see
[github-issue-planning](../github-issue-planning/SKILL.md).

After filing issues, add an **audit matcher** in `audit/features.json` so triage
can detect completion. Planning `topics.json` (touch points) and audit
`features.json` (probes) are **related but separate** — same domain, different jobs.

## Layout

```
scripts/github-issues/
├── lib/              # shared: gh, labels, gh-issues, topics
├── planning/         # topics.json, labels.json, split-templates.json
├── audit/            # features.json, overrides.json
├── scaffold-issue.mjs
├── split-feature.mjs
├── view-issue.mjs
└── audit.mjs

scripts/github-issue-audit.mjs   → forwarder
scripts/scaffold-issue.mjs       → forwarder
scripts/split-feature.mjs        → forwarder
scripts/view-issue.mjs           → forwarder
```

Docs-only (not scripts):

- `.agents/skills/github-issue-planning/touch-map.md` — codebase conventions

## Prerequisites

```bash
gh auth status   # must be logged in
gh repo view     # ukon1990/wow-auction-engine
```

**Do not call `gh issue view` directly from child skills** — use `view-issue.mjs`
(returns parent, sub-issues, and body dependency refs in one JSON payload).

## Fetch an issue

```bash
node scripts/github-issues/view-issue.mjs 75
node scripts/github-issues/view-issue.mjs 76 --json
node scripts/github-issues/view-issue.mjs 75 --no-body   # metadata only
```

JSON shape (`getIssueWithRelations` in `lib/gh-issues.mjs`):

| Field | Description |
|-------|-------------|
| `number`, `title`, `state`, `url`, `labels`, `body` | Issue core |
| `parent` | `{ number, title, state, url }` or `null` |
| `subIssues` | Linked children via GitHub sub-issues API |
| `dependencies.parentNumber` | Parent `#` (API or body) |
| `dependencies.blockedBy` | Parsed from `**Blocked by:**` in body |
| `dependencies.bodySubIssueNumbers` | `#` refs in body when API link missing |
| `suggestedBranch` | `<issue-type>/<id>-<title-slug>` (see Branch names below) |

Stack context: **Kotlin Spring Boot** `backend/` + **Angular** `frontend/`. Ignore
legacy Go paths in old issue bodies.

## Branch names

When starting work on an issue, branch from `master`:

```
<issue-type>/<issue-id>-<issue-title-slug>
```

Example: `bug/123-users-cannot-log-in`

| Part | Rule |
|------|------|
| `issue-type` | `bug` if title starts with `fix:`/`bug:` or has `bug` label; `chore`, `docs` from title prefix; else `feat` |
| `issue-id` | GitHub issue number |
| `issue-title-slug` | Lowercase title, strip `feat(scope):` prefix, non-alphanumerics → `-` |

`view-issue.mjs --json` includes `suggestedBranch`. Helpers in `lib/branches.mjs`.

```bash
git checkout master && git pull
git checkout -b "$(node scripts/github-issues/view-issue.mjs <#> --json | node -e "process.stdin.on('data',d=>console.log(JSON.parse(d).suggestedBranch))")"
```

## Labels

Validated via `lib/labels.mjs` against `gh label list`. Config:
`planning/labels.json` (`deny: go`, step defaults, `enhancement` on features).

```bash
node scripts/github-issues/scaffold-issue.mjs --list-labels
```

## Quick commands

```bash
# Fetch issue + parent + sub-issues
node scripts/github-issues/view-issue.mjs 75 --json

# Plan one issue
node scripts/github-issues/scaffold-issue.mjs crafting --parent 40 --create --dry-run
node scripts/github-issues/scaffold-issue.mjs auction-market "Bug" --label bug,frontend --exact-labels --dry-run

# Split epic into sub-issues
node scripts/github-issues/split-feature.mjs --topic crafting --parent 40 --create --dry-run

# Audit backlog
node scripts/github-issues/audit.mjs
node scripts/github-issues/audit.mjs --issue 42 --verbose
node scripts/github-issues/audit.mjs --close --dry-run
```

## Maintenance rules

| Change                    | Edit                            |
| ------------------------- | ------------------------------- |
| New domain touch points   | `planning/topics.json`          |
| Label policy              | `planning/labels.json`          |
| Split templates           | `planning/split-templates.json` |
| “Is it shipped?” probes   | `audit/features.json`           |
| Per-issue audit exception | `audit/overrides.json`          |
| gh / create / label logic | `lib/*.mjs` (once)              |
| Issue fetch + relations   | `view-issue.mjs`, `lib/gh-issues.mjs` |

Do **not** add scripts under `.agents/skills/*/scripts/` — update this toolkit.

## Related skills

- [github-issue-planning](../github-issue-planning/SKILL.md) — scoping & filing
- [github-issue-work](../github-issue-work/SKILL.md) — implement with worker + review subagents
- [github-issue-triage](../github-issue-triage/SKILL.md) — backlog audit
- [frontend-i18n](../frontend-i18n/SKILL.md) — when UI issues need XLF work

More detail: [reference.md](reference.md)
