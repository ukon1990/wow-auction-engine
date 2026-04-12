package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dto.Href
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.buildWebClient
import net.jonasmf.auctionengine.testsupport.BlizzardApiCallSupport.Companion.createSupport
import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Mono

class BlizzardConnectedRealmApiClientTest {
    @Test
    fun `getConnectedRealmIndex builds expected uri`() {
        var capturedRequest: ClientRequest? = null
        val webClient =
            buildWebClient { request ->
                capturedRequest = request
                response("""{"connected_realms":[{"href":"https://realm.test/1"}]}""")
            }

        val client = BlizzardConnectedRealmApiClient(createSupport(webClient))

        val result = client.getConnectedRealmIndex(Region.Korea).block()!!

        assertEquals(1, result.connectedRealms.size)
        assertEquals(
            "https://kr.api.blizzard.test/data/wow/connected-realm/index?namespace=dynamic-kr&locale=en_GB",
            capturedRequest!!.url().toString(),
        )
    }

    @Test
    fun `getConnectedRealm uses href directly`() {
        var capturedRequest: ClientRequest? = null
        val webClient =
            buildWebClient { request ->
                capturedRequest = request
                response(connectedRealmBody())
            }

        val client = BlizzardConnectedRealmApiClient(createSupport(webClient))

        val result = client.getConnectedRealm(Href("https://realm.test/connected-realm/42")).block()!!

        assertEquals(42, result.id)
        assertEquals("https://realm.test/connected-realm/42", capturedRequest!!.url().toString())
    }

    private fun response(body: String): Mono<ClientResponse> =
        Mono.just(
            ClientResponse
                .create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build(),
        )

    private fun connectedRealmBody(): String =
        loadFixture(this, "/blizzard/connected-realm/connected-realm-response.json")
}
