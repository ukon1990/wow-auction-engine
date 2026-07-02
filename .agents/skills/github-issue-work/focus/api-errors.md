# API errors

Pass this file to **backend workers**, **frontend workers** (mutation forms), and **maintainability reviewer**.

wow-auction-engine uses Spring `ResponseStatusException` and controller advice for error responses.
Admin endpoints use `AdminControllerAdvice` (`backend/src/main/kotlin/net/jonasmf/auctionengine/controller/AdminControllerAdvice.kt`).

## Backend

- Throw typed domain exceptions or `ResponseStatusException` with appropriate HTTP status
- Map at controller/advice boundary — services should not return error DTOs
- Use 404 for missing resources, 403 for forbidden, 400 for validation failures
- Keep error bodies consistent within a controller group (status + detail map for admin)

## Frontend

- Handle HTTP errors from generated API services in feature code
- Show user-visible messages for 4xx; log 5xx without leaking internals
- Form validation: inline field errors before submit; server errors on submit failure

## Reviewer checks

- [ ] New endpoints return correct status codes for not-found, forbidden, validation
- [ ] No stack traces or internal details in client-visible responses
- [ ] Frontend handles loading/error/empty states for API failures
