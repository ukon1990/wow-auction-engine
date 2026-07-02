---
name: spring-boot-testing
description: >-
  Write unit, integration, and slice tests for Spring Boot 3 applications. Use
  when writing unit tests, integration tests, or slice tests for Spring Boot 3
  applications, or when working on *IntegrationTest.kt, Testcontainers,
  MockMvc, or backend/ test coverage.
---

# Spring Boot Testing Standards

## **Priority: P0**

## Follow TDD Workflow

1. **Red**: Write a failing test (e.g., `returns 404`).
2. **Green**: Implement minimal code to pass.
3. **Refactor**: Clean up while keeping tests green.
4. **Coverage**: Verify with JaCoCo.

## Write Slice and Integration Tests

- **Real Infrastructure**: Use **Testcontainers** for DB. Avoid H2/Embedded.
- **Assertions**: Use **AssertJ** (`assertThat`) over JUnit assertions.
- **Isolation**: Use `@MockBean` for downstream dependencies in Slice Tests.

See [implementation examples](references/implementation.md) for WebMvcTest slice tests and Testcontainers integration tests.

## Anti-Patterns

- **No Dirty Contexts**: Avoid @MockBean in base classes; it reloads context per test.
- **No network I/O in tests**: Mock external calls with WireMock.
- **No System.out in tests**: Use AssertJ assertions instead.

## wow-auction-engine context

- Base class: `backend/src/test/kotlin/net/jonasmf/auctionengine/config/IntegrationTestBase.kt`
- MariaDB via `SharedTestContainers` (Testcontainers)
- Issue-work standards: [github-issue-work/focus/testing.md](../github-issue-work/focus/testing.md)
- Run: `cd backend && ./mvnw test` (Docker required)

## References

- [Implementation Examples](references/implementation.md)
