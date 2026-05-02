package net.jonasmf.auctionengine.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@TestConfiguration(proxyBeanMethods = false)
class StubAuthWebClientConfig {
    @Bean
    @Primary
    fun stubAuthWebClient(): WebClient {
        val exchangeFunction =
            ExchangeFunction { _ ->
                Mono.just(
                    ClientResponse
                        .create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("""{"access_token":"stub-token","expires_in":3600,"token_type":"Bearer"}""")
                        .build(),
                )
            }

        return WebClient
            .builder()
            .exchangeFunction(exchangeFunction)
            .build()
    }
}
