package net.jonasmf.auctionengine.interceptor

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.service.AuthService
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction

fun authHeaderFilterFunction(
    authService: AuthService,
    blizzardApiProperties: BlizzardApiProperties,
): ExchangeFilterFunction {
    return ExchangeFilterFunction.ofRequestProcessor { clientRequest ->
        if (!clientRequest.url().toString().contains(blizzardApiProperties.baseUrl)) {
            return@ofRequestProcessor reactor.core.publisher.Mono
                .just(clientRequest)
        }

        authService
            .ensureToken()
            .map { token ->
                ClientRequest
                    .from(clientRequest)
                    .header("Authorization", "Bearer $token")
                    .build()
            }
    }
}
