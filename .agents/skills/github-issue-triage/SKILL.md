---
name: github-issue-triage
description: >-
  Audit open GitHub issues for wow-auction-engine against the codebase using topic
  matchers and code probes; optionally close completed or obsolete issues.
  Use when triaging backlog or verifying if a feature shipped. Requires
  github-issues toolkit scripts; read that skill for shared conventions.
---

# GitHub issue triage (wow-auction-engine)

**Start with:** [github-issues](../github-issues/SKILL.md) for toolkit layout and lifecycle.

This skill covers **audit and close** only. For filing issues, use
[github-issue-planning](../github-issue-planning/SKILL.md).

## Quick commands

```bash
node scripts/github-issues/audit.mjs
node scripts/github-issues/audit.mjs --issue 42 --verbose
node scripts/github-issues/audit.mjs --close --dry-run
node scripts/github-issues/audit.mjs --close
```

(`scripts/github-issue-audit.mjs` is a forwarder to the same tool.)

## How it works

```
open issues (gh)
  → match feature in audit/features.json (title/labels/body)
  → run probes (path, grep, glob, package.json script)
  → apply audit/overrides.json (optional)
  → status → closable if implemented | obsolete
```

| Status | Meaning |
|--------|---------|
| `implemented` | All probes pass |
| `partial` | Some signal, not complete — keep open |
| `not_implemented` | Not found |
| `obsolete` | No longer applies (e.g. Go scaffold) |
| `meta` | Tracking issue — never auto-close |
| `needs_review` | No matcher — add to `features.json` |

**Auto-close:** `--close` only for `implemented` or `obsolete`, never `partial`/`meta`.

## Config (audit)

| File | Purpose |
|------|---------|
| `scripts/github-issues/audit/features.json` | Matchers + probes |
| `scripts/github-issues/audit/overrides.json` | Per-# exceptions |

## Adding a matcher

When [github-issue-planning](../github-issue-planning/SKILL.md) files a feature,
add a corresponding entry in `features.json`:

```json
{
  "id": "crafting-market",
  "summary": "Crafting market search",
  "match": { "title": ["crafting"], "labelsAny": ["backend"] },
  "implementedWhen": {
    "all": [
      { "grep": { "pattern": "auction_market|/auctions", "paths": ["backend/src"] } }
    ]
  }
}
```

Place specific matchers before generic ones (array order matters).

## Manual checks

Probes cannot verify UX/a11y, runtime SEO, or test quality. Spot-check before `--close`.

## Shared toolkit

- Label rules, gh setup, script layout: [github-issues/SKILL.md](../github-issues/SKILL.md)
