# Kotlin backend conventions

Pass to **backend workers** and **maintainability reviewer** for every `backend/` slice.

Also read: [touch-map.md](../../github-issue-planning/touch-map.md), `.github/copilot-instructions.md` § Kotlin / Spring.

**Skills** (read when writing or reviewing `backend/...*.kt`):

- [kotlin-best-practices](../../kotlin-best-practices/SKILL.md) — scope functions, backing properties, visibility, kotlinlang.org idioms
- [spring-boot-testing](../../spring-boot-testing/SKILL.md) — Testcontainers, TDD, integration tests

**Required focus files for backend work** (read before implementing):

- [migrations.md](migrations.md) — Flyway, indexes, pagination, layering
- [logging.md](logging.md) — MDC, correlation ID, BFF ↔ API propagation
- [authz.md](authz.md) — per-endpoint authz matrix, service-layer enforcement

## Kotlin style (latest conventions)

Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) via
[kotlin-best-practices](../../kotlin-best-practices/SKILL.md) (naming, idioms, layout) and match existing `backend/...ode.

- **Readable names** — full words; functions read as verbs; types as nouns; no `*Util` files
- **Immutability** — `val` by default; `data class` for DTOs/domain records; `listOf`/`setOf` when not mutating
- **Expression style** — expression `if`/`when`; default params over overloads; expression-bodied single-line functions
- **Early returns** — guard clauses over deep nesting
- **Small functions** — one responsibility; extract when logic branches multiply
- **Constructor injection** — Spring `@Service` / `@Component` primary constructors; no field injection
- **Extension functions** — mappers in `mapper/` packages, not instance methods on unrelated types
- **Null safety** — avoid `!!`; use `?.`, `?:`, `requireNotNull`, or explicit checks
- **Sealed types / enums** — for closed sets (status, category); prefer `enum class` over string constants
- **Exceptions** — typed domain exceptions (`NotFoundException`, `ForbiddenException`); map to ProblemDetails at boundary

## Companion objects — avoid unless necessary

**Default: do not add `companion object`.** Prefer:

| Instead of companion    | Use                                                                 |
| ----------------------- | ------------------------------------------------------------------- |
| Static factory on class | Top-level `fun createX(...)` in same file                           |
| Constants               | `private const val` at file level or nested in config object        |
| `fromValue` parsing     | Top-level function or enum method on the enum itself (no companion) |
| Testcontainers setup    | `companion object` in test base only — acceptable                   |

Acceptable companion uses: JUnit/Testcontainers shared containers, rare enum `fromValue` when matching existing enum pattern.

Flag new companions used for generic helpers, constants blobs, or “utility” namespaces — extract to a named file/object or top-level functions.

## Layering (reusable, clean code)

```
Controller (generated *Api) → Service (authz + validation + orchestration) → Repository
```

- Controllers stay thin — no SQL, no business rules
- Services own authz ([authz.md](authz.md)) and coordinate repositories
- Repositories own SQL/JPA only
- Validation collects field errors → ProblemDetails ([api-errors.md](api-errors.md))
- OpenAPI-generated types at the edge; domain types inside services ([openapi.md](openapi.md))

Copy patterns from similar features before inventing new structure ([code-quality.md](code-quality.md) similar-code search).

## Reviewer checks

- [ ] Matches Kotlin conventions ([kotlin-best-practices](../../kotlin-best-practices/SKILL.md), kotlinlang.org) and project layering
- [ ] No unnecessary `companion object`
- [ ] migrations.md, logging.md, authz.md requirements met for this slice
- [ ] Reuses existing services/validators/repos where semantics match
