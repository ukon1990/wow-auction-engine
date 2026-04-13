package net.jonasmf.auctionengine.integration.blizzard

import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

internal fun <T> Mono<T>.retryTransientBlizzardFailures(
    maxRetries: Long,
    backoff: Duration,
): Mono<T> =
    retryWhen(
        Retry
            .backoff(maxRetries, backoff)
            .filter(::isRetryableBlizzardFailure),
    )

private fun isRetryableBlizzardFailure(error: Throwable): Boolean {
    val clientError = error as? BlizzardApiClientException ?: return false
    return clientError.summary.startsWith("429") ||
        clientError.summary.startsWith("5") ||
        clientError.summary.startsWith("request timed out")
}
