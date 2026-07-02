---
name: review-local
description: >-
  Run Bugbot + security reviews without subagents when quota is exhausted or
  using a non-Cursor agent. Use for "review locally", "fallback review", or
  when bugbot/security-review Task calls fail.
disable-model-invocation: true
---

# Local review (Bugbot + Security fallback)

Run when **subagent reviewers are unavailable**:

- Cursor usage limit on `bugbot` / `security-review`
- Another AI agent without those Task types
- User asks to "run reviews locally" or "fallback review"

## Workflow

1. Resolve base branch: `gh pr view --json baseRefName` or user says `master`
2. Gather diff: `git diff <base>...HEAD --stat` and read changed production files
3. Run **[review-bugbot-fallback](../review-bugbot-fallback/SKILL.md)** checklist → Bugbot table
4. Run **[review-security-fallback](../review-security-fallback/SKILL.md)** checklist → Security table + authz matrix
5. Summarize: pass/fail, counts by severity, must-fix before merge

## Pass criteria (same as github-issue-work Phase 7)

- No **Critical** or **High** findings remain unaddressed
- Authz matrix complete for new endpoints
- Edge cases from issue acceptance criteria covered or explicitly deferred

## Subagent path (when quota allows)

Prefer Cursor subagents via:

- `~/.cursor/skills-cursor/review-bugbot/SKILL.md`
- `~/.cursor/skills-cursor/review-security/SKILL.md`

Use **this skill** only when those fail or the environment has no Task tool.
