package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.interceptor.authHeaderFilterFunction
import net.jonasmf.auctionengine.service.AuthService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig(
    private val blizzardApiProperties: BlizzardApiProperties,
) {
    @Bean
    fun webClientBuilder(): WebClient.Builder {
        return WebClient.builder() // Customize here if needed, e.g., set base URL, default headers, etc.
    }

    @Bean
    fun webClientWithAuth(authService: AuthService): WebClient =
        webClientBuilder()
            .filter(authHeaderFilterFunction(authService, blizzardApiProperties))
            .build()
}
