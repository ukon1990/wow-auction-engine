package net.jonasmf.auctionengine.integration.blizzard

import org.slf4j.Logger
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.Exceptions
import java.time.Duration
import java.util.concurrent.TimeoutException

internal class BlizzardApiClientException(
    val operation: String,
    val url: String,
    val realmId: Int? = null,
    val summary: String,
    val exceptionType: String,
    cause: Throwable,
) : RuntimeException(summary, cause) {
    companion object {
        fun from(
            error: Throwable,
            operation: String,
            url: String,
            timeout: Duration,
            realmId: Int? = null,
        ): BlizzardApiClientException {
            if (error is BlizzardApiClientException) {
                return error
            }

            val rootCause = Exceptions.unwrap(error)
            return BlizzardApiClientException(
                operation = operation,
                url = url,
                realmId = realmId,
                summary = summarize(rootCause, timeout),
                exceptionType = rootCause::class.simpleName ?: rootCause.javaClass.simpleName,
                cause = rootCause,
            )
        }

        private fun summarize(
            error: Throwable,
            timeout: Duration,
        ): String =
            when (error) {
                is WebClientResponseException -> "${error.statusCode.value()} ${error.statusText}".trim()
                is TimeoutException -> "request timed out after ${timeout.toSeconds()}s"
                else -> sanitize(error.message ?: "request failed")
            }

        private fun sanitize(message: String): String =
            message
                .lineSequence()
                .firstOrNull()
                ?.removePrefix("JSON decoding error: ")
                ?.substringBefore("; nested exception is")
                ?.substringBefore(" (start marker")
                ?.substringBefore(" at [Source:")
                ?.trim()
                ?.ifBlank { "request failed" }
                ?: "request failed"
    }
}

internal fun Logger.logBlizzardHttpFailure(error: Throwable) {
    val clientError = error as? BlizzardApiClientException ?: return

    error(
        "Blizzard API {} failed{} for {}: {}: {}",
        clientError.operation,
        clientError.realmId?.let { " for realm $it" } ?: "",
        clientError.url,
        clientError.exceptionType,
        clientError.summary,
    )
    debug(
        "Blizzard API {} failure diagnostics for {}",
        clientError.operation,
        clientError.url,
        clientError.cause ?: clientError,
    )
}
