# Testing

Pass this file to **workers** (backend/frontend) and **maintainability reviewer**.

**Backend:** also read [spring-boot-testing](../../spring-boot-testing/SKILL.md) and
[references/implementation.md](../../spring-boot-testing/references/implementation.md).

## Principles

- Tests are **simple and readable** — a reviewer should understand intent in one pass
- **Arrange / Act / Assert** — keep each section short; avoid walls of setup code
- **One behavior per test** — name tests after the scenario (`rejects duplicate username`)
- Meaningful coverage over volume — happy path + validation + authz + empty/error

## Fixtures & builders (mandatory for non-trivial setup)

Do not inline large object graphs in test methods. Extract when setup exceeds ~10 lines
or is reused.

| Layer               | Pattern                                                      | Location                                               |
| ------------------- | ------------------------------------------------------------ | ------------------------------------------------------ |
| Backend integration | Private `createX()` helpers or shared `object` fixtures      | Same test class, or `backend/src/test/kotlin/.../support/` |
| Backend unit        | Test doubles / stub repos (see `TestYieldUnitRepository.kt`) | Colocated or `support/`                                |
| Frontend            | Factory functions for mock API responses / component inputs  | `*.spec.ts` top or `testing/` helper next to feature   |

**Builders** — fluent or function-style factories with sensible defaults and overrides:

```kotlin
// Prefer
val filter = marketFilter { realmId = 123; quality = 4 }

// Over
val body = RecipeBody(title = "...", portions = 4, steps = listOf(...20 lines...))
```

```typescript
// Prefer
const user = mockPublicProfile({ username: 'chef' });

// Over
const user = { username: 'chef', displayName: '...', bio: '...', ... };
```

Copy patterns from `HomeDiscoveryIntegrationTest.createRecipe()`, `AuthSecurityIntegrationTest`.

## Coverage expectations

| Layer                     | Expectation                                                                    |
| ------------------------- | ------------------------------------------------------------------------------ |
| Backend API               | `*IntegrationTest.kt` for new/changed endpoints                                |
| Backend service           | Unit tests when logic is non-trivial                                           |
| Frontend                  | Colocated `*.spec.ts` — loading, empty, error, success                         |
| POST/PATCH/PUT validation | Test asserts Problem `errors[]` per field (see [api-errors.md](api-errors.md)) |
| Gaps                      | Flag new branches with no test; flag trivial `assertTrue(true)`-style tests    |
| Authz                     | Anonymous / other-user / admin per [authz.md](authz.md)                          |
| Mock-heavy tests          | Flag when the behavior under test is entirely mocked away                        |

## Reviewer checks

- [ ] New logic has tests; non-happy paths covered
- [ ] Authz cases tested for new/changed endpoints ([authz.md](authz.md))
- [ ] Tests readable — no setup walls; fixtures/builders used
- [ ] Test names describe behavior
- [ ] Integration tests for API mutations include validation error cases
- [ ] No duplicated setup across tests — extract shared fixture

## Commands

```bash
cd backend && ./mvnw test          # Docker required
cd frontend && bun test
```
