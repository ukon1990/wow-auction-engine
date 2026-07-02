package net.jonasmf.auctionengine.testsupport

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.ClientRequest
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlizzardFixturesTest {
    @Test
    fun `returns auction metadata with deterministic last modified when conditional header is present`() {
        val response =
            BlizzardFixtures
                .handleRequest(
                    request(
                        "https://eu.api.blizzard.test/data/wow/connected-realm/123/auctions",
                        HttpHeaders.IF_MODIFIED_SINCE to "Sat, 14 Mar 3000 20:07:10 GMT",
                    ),
                ).block()!!

        assertEquals(1_735_689_600_000, response.headers().asHttpHeaders().lastModified)
    }

    @Test
    fun `returns auction payload fixture when conditional metadata header is absent`() {
        val response =
            BlizzardFixtures
                .handleRequest(
                    request("https://eu.api.blizzard.test/data/wow/connected-realm/123/auctions"),
                ).block()!!

        val body = response.bodyToMono(String::class.java).block()!!

        assertTrue(body.contains("\"auctions\""))
        assertTrue(body.contains("19019"))
    }

    @Test
    fun `rejects non Blizzard hosts before serving fixtures`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                BlizzardFixtures
                    .handleRequest(request("https://example.test/data/wow/item/171374"))
                    .block()
            }

        assertTrue(error.message!!.contains("Unexpected Blizzard request host"))
    }

    @Test
    fun `rejects non https Blizzard requests before serving fixtures`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                BlizzardFixtures
                    .handleRequest(request("http://eu.api.blizzard.test/data/wow/item/171374"))
                    .block()
            }

        assertTrue(error.message!!.contains("Unexpected Blizzard request scheme"))
    }

    @Test
    fun `rejects unsupported media routes with explicit error`() {
        val error =
            assertFailsWith<IllegalStateException> {
                BlizzardFixtures
                    .handleRequest(request("https://eu.api.blizzard.test/data/wow/media/item/171374"))
                    .block()
            }

        assertTrue(error.message!!.contains("Unsupported Blizzard media fixture route"))
    }

    private fun request(
        url: String,
        vararg headers: Pair<String, String>,
    ): ClientRequest {
        val builder = ClientRequest.create(HttpMethod.GET, URI.create(url))
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }
}
