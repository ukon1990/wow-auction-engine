package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.testsupport.loadFixture
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

class BlizzardAuctionApiClientTest {
    @Test
    fun `getLatestAuctionDump builds connected realm uri with regional namespace and header`() {
        var capturedRequest: ClientRequest? = null
        val webClient =
            webClient { request ->
                capturedRequest = request
                response(auctionDumpMetadataBody(), 1_700_000_000_000)
            }

        val client = BlizzardAuctionApiClient(createSupport(webClient))

        val result = client.getLatestAuctionDump(123, Region.Europe, GameBuildVersion.CLASSIC).block()!!

        assertEquals(
            "https://eu.api.blizzard.test/data/wow/connected-realm/123/auctions/index?namespace=dynamic-eu&locale=en_US",
            result.url,
        )
        assertEquals(
            "Sat, 14 Mar 3000 20:07:10 GMT",
            capturedRequest!!.headers()[HttpHeaders.IF_MODIFIED_SINCE]?.single(),
        )
    }

    @Test
    fun `getLatestAuctionDump builds commodity uri for region`() {
        var capturedRequest: ClientRequest? = null
        val webClient =
            webClient { request ->
                capturedRequest = request
                response(auctionDumpMetadataBody(), 1_700_000_000_000)
            }

        val client = BlizzardAuctionApiClient(createSupport(webClient))

        val result = client.getLatestAuctionDump(-1, Region.NorthAmerica).block()!!

        assertEquals(
            "https://us.api.blizzard.test/data/wow/auctions/commodities?namespace=dynamic-us&locale=en_US",
            result.url,
        )
        assertEquals(result.url, capturedRequest!!.url().toString())
    }

    @Test
    fun `downloadAuctionData deserializes auction fixture with varied items`() {
        var capturedRequest: ClientRequest? = null
        val webClient =
            webClient { request ->
                capturedRequest = request
                response(auctionDataBody())
            }

        val client = BlizzardAuctionApiClient(createSupport(webClient))

        val result =
            client
                .downloadAuctionData(
                    "https://eu.api.blizzard.test/data/wow/connected-realm/123/auctions",
                ).block()!!

        assertEquals(
            "https://eu.api.blizzard.test/data/wow/connected-realm/123/auctions",
            capturedRequest!!.url().toString(),
        )
        assertEquals(3, result.auctions.size)
        assertEquals(19019, result.auctions[0].item.id)
        assertEquals(
            2,
            result.auctions[1]
                .item.modifiers!!
                .size,
        )
        assertEquals(52, result.auctions[1].item.context)
        assertEquals(39, result.auctions[2].item.pet_species_id)
        assertEquals(25, result.auctions[2].item.pet_level)
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

    private fun response(
        body: String,
        lastModified: Long? = null,
    ): Mono<ClientResponse> =
        Mono.just(
            ClientResponse
                .create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .apply {
                    if (lastModified != null) {
                        header(HttpHeaders.LAST_MODIFIED, lastModified.toString())
                    }
                }.body(body)
                .build(),
        )

    private fun auctionDataBody(): String = loadFixture(this, "/blizzard/auction/auction-data-response.json")

    private fun auctionDumpMetadataBody(): String =
        loadFixture(this, "/blizzard/auction/auction-dump-metadata-response.json")
}
