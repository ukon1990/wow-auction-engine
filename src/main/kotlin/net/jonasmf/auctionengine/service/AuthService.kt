package net.jonasmf.auctionengine.service

import com.fasterxml.jackson.annotation.JsonProperty
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@Service
class AuthService(
    private val properties: BlizzardApiProperties,
    private val authWebClient: WebClient,
) {
    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenExpiresAtEpochMillis: Long = 0

    @EventListener(ApplicationReadyEvent::class)
    fun refreshTokenAfterStartup() {
        refreshToken().subscribe()
    }

    @Scheduled(
        fixedDelayString = "PT1H",
        initialDelayString = "\${app.scheduling.initial-delay:PT30S}",
    )
    fun scheduledTokenRefresh() {
        refreshToken().subscribe()
    }

    private fun refreshToken(): Mono<String> {
        val body =
            "grant_type=client_credentials&client_id=${properties.clientId}&client_secret=${properties.clientSecret}"
        return authWebClient
            .post()
            .header("Content-Type", "application/x-www-form-urlencoded")
            .bodyValue(body)
            .retrieve()
            .onStatus({ status -> status.isError }, { response ->
                response.bodyToMono(String::class.java).flatMap { errorBody ->
                    LOG.error("Failed to fetch token, status code: ${response.statusCode()}, body: $errorBody")
                    Mono.error(RuntimeException("Error during token request: $errorBody"))
                }
            })
            .bodyToMono(TokenResponse::class.java)
            .map { response ->
                if (response.accessToken == null) {
                    throw IllegalStateException("Token was not provided in the response")
                }
                accessToken = response.accessToken
                tokenExpiresAtEpochMillis = System.currentTimeMillis() + (response.expiresIn * 1000)
                LOG.info("Token refreshed successfully. Access Token: ${response.accessToken}")
                response.accessToken
            }.doOnError { error ->
                if (error is WebClientResponseException) {
                    LOG.error("WebClient response error: ${error.statusCode} - ${error.statusText}")
                } else {
                    LOG.error("Error obtaining token: ${error.localizedMessage}")
                }
            }
    }

    fun getToken(): Mono<String> =
        Mono
            .justOrEmpty(accessToken)
            .filter { isTokenValid() }

    fun ensureToken(): Mono<String> =
        Mono.defer {
            accessToken?.takeIf { isTokenValid() }?.let { Mono.just(it) } ?: refreshToken()
        }

    private fun isTokenValid(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        accessToken != null && nowEpochMillis < tokenExpiresAtEpochMillis - TOKEN_REFRESH_SKEW_MILLIS

    companion object {
        private const val TOKEN_REFRESH_SKEW_MILLIS = 60_000L
        val LOG: Logger = LoggerFactory.getLogger(AuthService::class.java)
    }
}

data class TokenResponse(
    @JsonProperty("access_token")
    val accessToken: String?,
    @JsonProperty("expires_in")
    val expiresIn: Long,
    @JsonProperty("token_type")
    val tokenType: String,
)
