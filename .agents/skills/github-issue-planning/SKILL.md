---
name: github-issue-planning
description: >-
  Plan GitHub issues and features for wow-auction-engine through a back-and-forth
  conversation, check open issues for overlap, then file only after explicit
  user confirmation. Covers touch points, acceptance criteria, sub-issue splits,
  parent association, dependencies, and labels. Use when creating issues,
  scoping features, splitting epics, or updating existing issues. Requires
  github-issues toolkit scripts; read that skill for shared gh/label conventions.
---

# GitHub issue planning (wow-auction-engine)

**Start with:** [github-issues](../github-issues/SKILL.md) for toolkit layout and lifecycle.

This skill covers **scoping and filing** only. For backlog audit, use
[github-issue-triage](../github-issue-triage/SKILL.md).

## Golden rule

**Never create or update a GitHub issue until the user explicitly confirms the plan.**

- During planning: research, ask questions, draft locally, run scripts with `--dry-run` only.
- After confirmation: run `--create` or apply updates.
- If the user says "draft only" or "don't file yet", stop after presenting the plan.

## Workflow overview

```
1. Discover     → clarify problem, outcome, constraints (conversation)
2. Draft plan   → structured proposal (no GitHub writes)
3. Iterate      → refine until the plan is clear enough
4. Overlap check → search open issues; resolve duplicates / parent linkage
5. Confirm      → user explicitly approves filing
6. File         → --create / gh issue edit (and audit matcher if new feature)
```

---

## Phase 1 — Discover (conversation)

Open with what you know; ask only what is missing. Prefer 2–4 focused questions per
turn, not a long questionnaire.

**Clarify:**

| Area | Questions |
|------|-----------|
| Problem | What pain or gap? Who is affected? |
| Outcome | What does "done" look like for the user? |
| Scope | MVP vs follow-ups? In/out of scope? |
| Existing work | New epic, child of parent `#`, or update to `#`? |
| Stack | Backend-only, frontend-only, full-stack, e2e? |
| Dependencies | Blocked by other issues? Must ship in order? |

**Research the codebase** before proposing touch points:

- `scripts/github-issues/planning/topics.json` — domain paths
- [touch-map.md](touch-map.md) — layering conventions
- Similar existing features to copy patterns from

Use `node scripts/github-issues/view-issue.mjs <#> --json` when updating an
existing issue. Note what should change vs stay.

---

## Phase 2 — Draft plan (no GitHub writes)

Present a **complete draft** the user can approve or edit. Use scaffold/split
scripts with `--dry-run` (or `--json`) to generate accurate bodies and labels —
do not invent label names or paths.

### Single issue

```bash
node scripts/github-issues/scaffold-issue.mjs <topic> "short summary" \
  --title "feat: …" --parent <#> --label <extra> --dry-run
```

For bug reports or narrow follow-ups where topic/default labels would be wrong,
pass the intended labels and add `--exact-labels`:

```bash
node scripts/github-issues/scaffold-issue.mjs auction-market "share link bug" \
  --title "fix: …" --parent <#> --label bug,frontend,i18n --exact-labels --dry-run
```

### Epic split (parent + sub-issues)

```bash
node scripts/github-issues/split-feature.mjs --topic <topic> --parent <#> \
  --feature "name" --template full-stack --dry-run
```

### Plan presentation template

Copy this structure when presenting the draft:

```markdown
## Issue plan (draft — not filed)

### Summary
[One paragraph: problem → proposed solution]

### Issues to create / update

| Action | Title | Labels | Parent | Notes |
|--------|-------|--------|--------|-------|
| create | … | … | #… | … |
| update #42 | … | … | — | body/labels change |

### Touch points
- `backend/...` — …
- `frontend/...` — …

### Acceptance criteria
- [ ] …
- [ ] …

### Dependencies / order
1. … → 2. …

### Out of scope
- …

### Open questions
- … (resolve before filing, or note assumptions)

### Overlap check
| # | Title | Relationship | Resolution |
|---|-------|--------------|------------|
| #… | … | duplicate / sub-issue candidate / related | … |
```

For each issue, include the **full dry-run body** (or a collapsible summary if
very long). The user must be able to judge correctness without opening GitHub.

---

## Phase 3 — Iterate

Treat user replies as plan edits, not filing permission unless they clearly confirm.

**Iterate when the user:**

- Changes scope, titles, labels, or acceptance criteria
- Picks a different split template or parent issue
- Adds/removes sub-issues or dependencies
- Corrects touch points or out-of-scope items

After each round of edits:

1. Update the draft plan (same template).
2. Re-run `--dry-run` if script inputs changed.
3. Call out **what changed** since the last draft.
4. Return to Phase 4 (overlap check) — do not file yet.

**Ask follow-ups** when ambiguity would produce a wrong issue (wrong domain,
missing parent, unclear MVP). Do not file on assumptions the user has not accepted.

---

## Phase 4 — Overlap check (required before confirm)

Before asking the user to confirm filing, **search open GitHub issues** for
overlap. Skip this phase only when the user already named a specific parent
`#` or update target `#` and you have verified it fits.

### Search

Run multiple queries — titles alone miss related epics:

```bash
# By domain label (from planning/topics.json)
gh issue list --state open --label <domain-label> --limit 50 \
  --json number,title,labels,url

# By keywords (title + body)
gh search issues --repo "$(gh repo view --json nameWithOwner -q .nameWithOwner)" \
  "<keyword1> <keyword2>" --state open --limit 30

# Inspect a candidate (parent + sub-issues + blockers)
node scripts/github-issues/view-issue.mjs <#> --json
```

Also scan the parent epic (if any) for existing sub-issues and checklist items
in its body (`## Sub-issues`).

### Classify each candidate

| Relationship | Signals | Typical action |
|--------------|---------|----------------|
| **Duplicate** | Same outcome, same scope, same touch points | Update `#` instead of create; or close as duplicate |
| **Sub-issue candidate** | Plan is a slice of an open epic; narrower scope | File as child of parent `#` with `--parent` |
| **Related, separate** | Same domain, different outcome or phase | Keep new issue; add cross-ref / `--blocked-by` |
| **No overlap** | Different feature or already resolved | Proceed as drafted |

### Ask the user when overlap matters

If any candidate is **duplicate** or **sub-issue candidate**, stop and ask —
do not proceed to confirm silently.

Present options clearly, e.g.:

> **#84 Pantry management** looks like the parent epic for this work.
>
> 1. **Sub-issue of #84** — file under that epic (recommended if this is one slice)
> 2. **Update #84** — extend the existing issue instead of creating a new one
> 3. **Separate issue** — new standalone issue; I'll add a cross-reference
> 4. **Different parent** — specify another `#`

Wait for the user's choice before updating the plan.

### Apply the user's decision to the plan

**Sub-issue of `#P`:**

- Set `--parent P` on `scaffold-issue.mjs`, or `--parent P` on `split-feature.mjs`
  for multi-step splits.
- Narrow title/scope so the child does not repeat the parent's acceptance criteria.
- Re-run `--dry-run` with the parent flag.
- On create, scripts link via GitHub sub-issues API; `split-feature.mjs` also
  appends a checklist to the parent body.

**Update existing `#N` instead of create:**

- Change plan action from `create` → `update #N`.
- Merge new acceptance criteria / touch points into the existing body.
- Drop redundant issues from the create list.

**Separate but related:**

- Keep standalone create; add `Related: #N` in body or `--blocked-by N` if
  ordering matters.

**Wrong parent corrected:**

- Replace parent in the plan table; re-run dry-run; show updated overlap table.

After overlap resolution, include the filled **Overlap check** table in the plan
and only then move to Phase 5.

---

## Phase 5 — Confirm

Before any `gh` write or `--create`, require **explicit approval**.

**Valid confirmation** (user must say something like):

- "Looks good, create them"
- "File it" / "Go ahead and create"
- "Update #42 as drafted"

**Not confirmation:**

- Silence after a draft
- "Looks good" without create/update intent
- Further edits or questions
- "Maybe" / "probably"

If unclear, ask once:

> Ready to file this plan on GitHub? Reply **confirm** to create/update, or tell me what to change.

Do not batch-create multiple issues without the user seeing the full plan at least once.

Overlap check (Phase 4) must be complete and reflected in the plan before
asking for confirmation.

---

## Phase 6 — File (after confirmation only)

### Create issues

```bash
# Single issue
node scripts/github-issues/scaffold-issue.mjs <topic> "summary" \
  --title "…" --parent <#> --create

# Epic split
node scripts/github-issues/split-feature.mjs --topic <topic> --parent <#> \
  --feature "…" --template <id> --create
```

### Update existing issues

Use `gh` when editing bodies or labels on existing issues:

```bash
gh issue edit <#> --title "…" --body-file /tmp/issue-body.md
gh issue edit <#> --add-label "…"
```

Prefer `--dry-run` on scripts that support it before the confirmed `--create` run.

### After filing

1. Report created/updated issue URLs and numbers.
2. For **new features**, add an audit matcher in `scripts/github-issues/audit/features.json`
   (see triage skill) so completed work can be detected later.
3. If the plan diverged during filing, show the final state vs the approved draft.

---

## Quick commands

All scripts live under `scripts/github-issues/`:

```bash
node scripts/github-issues/scaffold-issue.mjs --list
node scripts/github-issues/scaffold-issue.mjs crafting --parent 40 --create --dry-run
node scripts/github-issues/split-feature.mjs --topic crafting --parent 40 --create --dry-run
node scripts/github-issues/split-feature.mjs --list-templates
```

## Split templates

`scripts/github-issues/planning/split-templates.json`:

| Template | Chain |
|----------|-------|
| `full-stack` | schema → API → frontend |
| `backend-only` | schema → API |
| `parallel-fe-be` | schema → API → frontend → e2e |

## Config (planning)

| File | Purpose |
|------|---------|
| `scripts/github-issues/planning/topics.json` | Domains → codebase paths |
| `scripts/github-issues/planning/labels.json` | Label policy |
| `scripts/github-issues/planning/split-templates.json` | Epic split patterns |

## Docs

- Codebase conventions: [touch-map.md](touch-map.md)
- Shared toolkit: [github-issues/SKILL.md](../github-issues/SKILL.md)
