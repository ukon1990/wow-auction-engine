# Authorization (authz)

Pass to **security reviewer**, **backend workers**, and **maintainability reviewer** when
endpoints or data access change.

Patterns: `AuthSupport.kt` (`requireAuth`, `requireRole`), service-layer owner checks.

## Authz matrix (required per new/changed endpoint)

For each endpoint, document and verify:

| Endpoint | Anonymous | Owner | Other user | Admin | Deny semantics |
| -------- | --------- | ----- | ---------- | ----- | -------------- |
| `GET …`  | …         | …     | …          | …     | 403 vs 404     |
| `POST …` | …         | …     | …          | …     | …              |

Copy semantics from similar controllers (`RecipeController`, `AdminUserService`, etc.).

## Rules

- **Every mutation** checks actor identity and ownership (or role) in service layer — not only in UI
- **List/search endpoints** filter rows the caller may not see (no IDOR via pagination)
- **403 vs 404** — match existing feature: hide existence of private resources when appropriate
- **Admin paths** use `requireRole("admin")` (or project equivalent) consistently
- **Frontend guards** (`authGuard`, `adminGuard`) are UX only — backend must enforce

## Tests

Integration tests per endpoint:

- Happy path for allowed actor
- Rejected path for wrong user / anonymous (`403` or `404` per convention)
- Admin-only paths without admin role

## Reviewer checks

- [ ] Matrix filled for all new/changed routes
- [ ] Service enforces authz; controller does not skip service
- [ ] No path leaks private fields on public DTOs
- [ ] Authz tests exist for non-owner and anonymous cases
