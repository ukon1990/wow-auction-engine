package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.interceptor.authHeaderFilterFunction
import net.jonasmf.auctionengine.interceptor.correlationHeadersFilter
import net.jonasmf.auctionengine.service.AuthService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

private const val BLIZZARD_WEBCLIENT_MAX_IN_MEMORY_BYTES = 4 * 1024 * 1024

/** Codecs for Blizzard API calls; JSON goes through [JsonMappers.blizzardApi]. */
fun blizzardExchangeStrategies(): ExchangeStrategies =
    ExchangeStrategies
        .builder()
        .codecs { codecs ->
            codecs.defaultCodecs().maxInMemorySize(BLIZZARD_WEBCLIENT_MAX_IN_MEMORY_BYTES)
            codecs.defaultCodecs().jacksonJsonDecoder(JacksonJsonDecoder(JsonMappers.blizzardApi))
            codecs.defaultCodecs().jacksonJsonEncoder(JacksonJsonEncoder(JsonMappers.blizzardApi))
        }.build()

fun blizzardWebClientBuilder(): WebClient.Builder = WebClient.builder().exchangeStrategies(blizzardExchangeStrategies())

@Configuration
class WebClientConfig(
    private val blizzardApiProperties: BlizzardApiProperties,
) {
    @Bean
    fun webClientBuilder(): WebClient.Builder = blizzardWebClientBuilder()

    @Bean
    fun webClientWithAuth(authService: AuthService): WebClient =
        webClientBuilder()
            .filter(correlationHeadersFilter())
            .filter(authHeaderFilterFunction(authService, blizzardApiProperties))
            .build()
}
