package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.interceptor.authHeaderFilterFunction
import net.jonasmf.auctionengine.service.AuthService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient


@Configuration
class WebClientConfig(private val blizzardApiProperties: BlizzardApiProperties) {

    @Bean
    fun webClientBuilder(): WebClient.Builder {
        return WebClient.builder()  // Customize here if needed, e.g., set base URL, default headers, etc.
    }

    @Bean
    fun webClientWithAuth(authService: AuthService): WebClient {
        // Due to the large size of the auction responses, we need to increase the buffer size
        val size = 100 * 1024 * 1024 // 100 MB
        val strategies = ExchangeStrategies.builder()
            .codecs { configurer: ClientCodecConfigurer -> configurer.defaultCodecs().maxInMemorySize(size) }
            .build()

        return webClientBuilder()
            .filter(authHeaderFilterFunction(authService, blizzardApiProperties))
            .exchangeStrategies(strategies)
            .build()
    }
}
