package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dto.Href
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

class BlizzardConnectedRealmApiClientTest {
    @Test
    fun `getConnectedRealmIndex builds expected uri`() {
        var capturedRequest: ClientRequest? = null
        val webClient =
            webClient { request ->
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
            webClient { request ->
                capturedRequest = request
                response(connectedRealmBody())
            }

        val client = BlizzardConnectedRealmApiClient(createSupport(webClient))

        val result = client.getConnectedRealm(Href("https://realm.test/connected-realm/42")).block()!!

        assertEquals(42, result.id)
        assertEquals("https://realm.test/connected-realm/42", capturedRequest!!.url().toString())
    }

    private fun createSupport(webClient: WebClient) =
        BlizzardApiSupport(
            properties =
                BlizzardApiProperties(
                    baseUrl = "api.blizzard.test/data/wow/",
                    tokenUrl = "https://oauth.blizzard.test/token",
                    clientId = "id",
                    clientSecret = "secret",
                    region = Region.Europe,
                ),
            webClientWithAuth = webClient,
        )

    private fun webClient(handler: (ClientRequest) -> Mono<ClientResponse>): WebClient =
        WebClient
            .builder()
            .exchangeFunction(ExchangeFunction(handler))
            .build()

    private fun response(body: String): Mono<ClientResponse> =
        Mono.just(
            ClientResponse
                .create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build(),
        )

    private fun connectedRealmBody(): String =
        javaClass
            .getResourceAsStream("/blizzard/connected-realm-response.json")
            ?.use { inputStream -> String(inputStream.readAllBytes(), StandardCharsets.UTF_8) }
            ?: error("Missing test resource: /blizzard/connected-realm-response.json")
}
