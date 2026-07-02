# GitHub issues toolkit — reference

## Overlap between planning and triage

Both skills use the **same topic names** (e.g. `crafting`, `auction-market`, `admin`) but
different config files:

| File                   | Used by         | Purpose                                      |
| ---------------------- | --------------- | -------------------------------------------- |
| `planning/topics.json` | scaffold, split | Where code should change; labels; docs paths |
| `audit/features.json`  | audit           | Code probes to detect if work shipped        |

When a feature is filed, add matchers to **both** if the domain is new:

1. `topics.json` entry — for future planning/scaffolding
2. `features.json` entry — for triage probes

Keep `audit/features.json` matcher `id` aligned with topic id when possible.

## Sub-issues

Created via REST `POST .../issues/{parent}/sub_issues` in `lib/gh-issues.mjs`.
Listed via `GET .../sub_issues` — exposed by `view-issue.mjs` and
`getIssueWithRelations()` in `lib/gh-issues.mjs`.

If linking fails, parent is still in the issue **Dependencies** section and
labels are applied. `view-issue.mjs` merges API-linked sub-issues with body
refs (`## Sub-issues` checklist).

## Forwarders

Old paths still work:

- `scripts/github-issue-audit.mjs`
- `scripts/scaffold-issue.mjs`
- `scripts/split-feature.mjs`
- `scripts/view-issue.mjs`

Prefer `scripts/github-issues/*.mjs` in new docs and skills.

## Anti-patterns

- Duplicating `gh` helpers outside `lib/gh.mjs`
- Hand-editing `frontend/src/app/api/generated/`
- Static issue-ID lists in audit (use `features.json` matchers)
- Putting executable scripts only in `.agents/skills/` without the toolkit
- Ad-hoc branch names (use `<issue-type>/<id>-<title-slug>` from `lib/branches.mjs`)
