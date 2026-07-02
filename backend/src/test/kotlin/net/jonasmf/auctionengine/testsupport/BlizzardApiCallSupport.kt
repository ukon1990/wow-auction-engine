package net.jonasmf.auctionengine.testsupport

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.integration.blizzard.BlizzardApiSupport
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class BlizzardApiCallSupport {
    companion object {
        @JvmStatic
        fun okJson(
            body: String,
            lastModified: String? = null,
        ) = jsonResponse(
            body = body,
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            lastModified = lastModified,
        )

        @JvmStatic
        fun jsonResponse(
            body: String,
            mediaType: String,
            lastModified: String? = null,
        ) =
            Mono.just(
                ClientResponse
                    .create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, mediaType)
                    .apply {
                        if (lastModified != null) {
                            header(HttpHeaders.LAST_MODIFIED, lastModified)
                        }
                    }
                    .body(body)
                    .build(),
            )

        @JvmStatic
        fun createSupport(webClient: WebClient) =
            BlizzardApiSupport(
                properties =
                    BlizzardApiProperties(
                        baseUrl = "api.blizzard.test/data/wow/",
                        tokenUrl = "https://oauth.blizzard.test/token",
                        clientId = "id",
                        clientSecret = "secret",
                        regions = listOf(Region.Europe),
                    ),
                webClientWithAuth = webClient,
            )

        @JvmStatic
        fun buildWebClient(handler: (ClientRequest) -> Mono<ClientResponse>): WebClient =
            WebClient
                .builder()
                .exchangeFunction(ExchangeFunction(handler))
                .build()

        @JvmStatic
        fun buildFilteredWebClient(
            handler: (ClientRequest) -> Mono<ClientResponse>,
            vararg filters: ExchangeFilterFunction,
        ): WebClient =
            filters
                .fold(WebClient.builder()) { builder, filter -> builder.filter(filter) }
                .exchangeFunction(ExchangeFunction(handler))
                .build()
    }
}
