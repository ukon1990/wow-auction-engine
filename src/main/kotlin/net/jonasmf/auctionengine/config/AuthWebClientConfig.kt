package net.jonasmf.auctionengine.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class AuthWebClientConfig(private val blizzardApiProperties: BlizzardApiProperties) {

    @Bean
    fun authWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(blizzardApiProperties.tokenUrl) // You can set a base URL specific to auth if needed
            .build()
    }
}
