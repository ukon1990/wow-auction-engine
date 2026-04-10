package net.jonasmf.auctionengine.testsupport

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class BlizzardApiCallSupport {
    companion object {
        @JvmStatic
        fun okJson(body: String) =
            Mono.just(
                ClientResponse
                    .create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build(),
            )

        @JvmStatic
        fun buildWebClient(handler: (ClientRequest) -> Mono<ClientResponse>): WebClient =
            WebClient
                .builder()
                .exchangeFunction(ExchangeFunction(handler))
                .build()
    }
}
