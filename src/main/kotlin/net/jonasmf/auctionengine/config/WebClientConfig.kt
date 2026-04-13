package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.interceptor.authHeaderFilterFunction
import net.jonasmf.auctionengine.service.AuthService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

private const val BLIZZARD_WEBCLIENT_MAX_IN_MEMORY_BYTES = 4 * 1024 * 1024

@Configuration
class WebClientConfig(
    private val blizzardApiProperties: BlizzardApiProperties,
) {
    @Bean
    fun webClientBuilder(): WebClient.Builder =
        WebClient.builder().codecs { codecs ->
            codecs.defaultCodecs().maxInMemorySize(BLIZZARD_WEBCLIENT_MAX_IN_MEMORY_BYTES)
        }

    @Bean
    fun webClientWithAuth(authService: AuthService): WebClient =
        webClientBuilder()
            .filter(authHeaderFilterFunction(authService, blizzardApiProperties))
            .build()
}
