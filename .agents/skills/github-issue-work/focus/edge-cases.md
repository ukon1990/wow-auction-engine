# Edge cases

Pass excerpt to **all reviewers** via `Custom Instructions` (adapt to the issue).

- **Validation boundaries** — empty input, max length, invalid formats, duplicates
- **Authz** — anonymous vs owner vs other user vs admin; 403 vs 404 semantics
- **Empty / missing data** — no rows, null optionals, first-time user
- **Concurrency / ordering** — migration deploy order, pagination, sort ties
- **Error paths** — ProblemDetails in UI; per-field form errors on 400
- **Security abuse** — XSS, open redirects, IDOR, rate limits
- **i18n** — new UI strings marked and extracted; locales synced ([i18n.md](i18n.md))
- **Logging** — correlation ID + MDC on new routes/endpoints ([logging.md](logging.md))

Full error rules: [api-errors.md](api-errors.md)
Authz detail: [authz.md](authz.md)
