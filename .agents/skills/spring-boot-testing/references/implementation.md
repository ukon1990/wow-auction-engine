# Spring Boot testing — wow-auction-engine examples

Extend `IntegrationTestBase` for real MariaDB via Testcontainers:

```kotlin
class ExampleIntegrationTest : IntegrationTestBase() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `returns 404 for unknown item`() {
        mockMvc
            .perform(get("/auctions/items/999999999"))
            .andExpect(status().isNotFound)
    }
}
```

See `backend/src/test/kotlin/net/jonasmf/auctionengine/config/IntegrationTestBase.kt`
and existing controller integration tests for patterns.
