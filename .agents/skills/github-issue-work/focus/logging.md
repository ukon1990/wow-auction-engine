# Logging & observability

Pass to **backend workers**, **frontend/BFF workers**, and **maintainability reviewer** when
routes, API calls, or request handling change.

Goal: enough structured logs for **telemetry** — what was visited, which APIs ran, who acted,
what failed — tied together by **correlation ID** via **MDC**.

## Correlation ID

- Generate or accept `X-Correlation-Id` (or `X-Request-Id`) at the **edge** (BFF / first hop)
- Propagate the same value on every downstream API call from the BFF
- Backend reads header or generates one; store in **SLF4J MDC** for the request lifetime
- Restore any previous MDC in a `finally` block after the request (filter/interceptor)
- Clear request-owned MDC keys at request start so anonymous requests cannot inherit stale `userId` / `sessionId`

**MDC keys (minimum):**

| Key             | Source                                                             |
| --------------- | ------------------------------------------------------------------ |
| `correlationId` | Header or generated UUID                                           |
| `httpMethod`    | Request                                                            |
| `httpPath`      | Request URI (no query secrets)                                     |
| `userId`        | Session/auth context when present                                  |
| `clientIp`      | `HttpServletRequest.safeClientIp()` (shared with `RateLimitFilter`) |

Add domain keys when useful (`itemId`, `realmId`) — keep cardinality low.

## BFF (`frontend/src/server.ts`)

Log **structured** (JSON lines preferred) events for telemetry:

| Event           | When                     | Fields                                                          |
| --------------- | ------------------------ | --------------------------------------------------------------- |
| `bff.page`      | SSR/page request handled | `correlationId`, `method`, `path`, `status`, durationMs         |
| `bff.api_proxy` | `/api` proxy invoked     | `correlationId`, `method`, `path`, `upstreamStatus`, durationMs |

- Do not log cookies, auth headers, or request bodies
- On proxy error, log with `correlationId` and error class — not full stack in production info logs

## Backend (`backend/`)

- **Request filter/interceptor**: set MDC at entry; log request start/end at INFO (path, status, duration)
- Use the shared `HttpServletRequest.safeClientIp()` helper for client IP MDC/rate-limit identity;
  do not log raw `X-Forwarded-For` values
- Preserve and restore any pre-existing MDC around request handling. Remove request-owned keys
  (`correlationId`, `userId`, `sessionId`, request/status fields) before setting this request's values
- **Service boundaries**: log business failures at WARN/ERROR with `correlationId` + `userId` from MDC
- **Controllers**: thin — log via filter + service; avoid duplicate per-method spam unless debugging
- Use SLF4J (`LoggerFactory` / `KotlinLogging`) — no `println`
- Never log passwords, tokens, session IDs, or full PII

## Error paths

Every user-visible failure path should leave a log trail:

- Validation → DEBUG or INFO with field names (not values if sensitive)
- Authz denial → INFO with `userId`, resource id, action
- Unexpected exception → ERROR with `correlationId`, exception type, safe message

## Reviewer checks

- [ ] New/changed BFF routes or proxy paths emit `bff.page` / `bff.api_proxy` (or equivalent middleware)
- [ ] New/changed backend endpoints participate in request MDC (filter present or extended)
- [ ] `correlationId` propagated BFF → backend on proxied calls
- [ ] MDC includes `userId` when authenticated
- [ ] Anonymous request logs cannot inherit stale `userId` / `sessionId` from pre-existing MDC
- [ ] Backend client IP logging uses `safeClientIp()`; raw `X-Forwarded-For` cannot pollute logfmt fields
- [ ] Error and denial paths logged with correlation context
- [ ] No secrets/PII in log statements
- [ ] No new `console.log` left in production server paths (use structured logger)

## Workers

When adding routes/endpoints without existing logging infrastructure, **add or extend** the
request filter/middleware in the same slice — do not leave observability as a follow-up.
