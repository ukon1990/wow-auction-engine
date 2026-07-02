# Examples & routing

## Full-stack split (3 workers)

| Worker   | Slice                                  | Depends on |
| -------- | -------------------------------------- | ---------- |
| worker-1 | Migration + DBO + repository           | —          |
| worker-2 | OpenAPI + service + controller + tests | worker-1   |
| worker-3 | Frontend + specs + stories             | worker-2   |

Record each worker **agent ID** for fix-loop resume.

**Attach to workers:** `worker-backend.md` (1–2), `worker-frontend.md` (3).

## Parallel workers

No shared files → launch in one message with multiple `Task` calls.

## Fix routing

| Finding                    | Worker context | Action                                      |
| -------------------------- | -------------- | ------------------------------------------- |
| SQLi in service            | 40%            | Resume worker                               |
| Missing empty state UI     | 75%            | New worker + `worker-fix.md` + `testing.md` |
| High cyclomatic complexity | any            | Fix worker + `code-quality.md`              |

## When to reduce workers

| Situation                    | Action                 |
| ---------------------------- | ---------------------- |
| Single-layer issue           | 1 worker               |
| < ~3 files                   | 1 worker               |
| Tightly coupled schema + API | 1 worker               |
| Single PR requested          | 1 worker or sequential |

## When to stop and re-plan

- Ambiguous acceptance criteria
- Overlap with unmerged PR
- Architectural surprise
- Systemic design flaw in review
- Fix loop > 3 cycles → escalate to user
