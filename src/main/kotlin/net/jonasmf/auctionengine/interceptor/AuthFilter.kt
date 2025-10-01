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
        authService
            .getToken()
            .map { token ->
                if (!clientRequest.url().toString().contains(blizzardApiProperties.baseUrl)) {
                    return@map clientRequest
                }
                ClientRequest
                    .from(clientRequest)
                    .header("Authorization", "Bearer $token")
                    .build()
            }.defaultIfEmpty(clientRequest)
    }
}
