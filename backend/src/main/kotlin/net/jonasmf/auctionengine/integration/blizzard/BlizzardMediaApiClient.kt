package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.media.BlizzardMediaDTO
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import java.time.Duration

private const val MEDIA_API_RETRY_ATTEMPTS = 3L
private val MEDIA_API_RETRY_BACKOFF: Duration = Duration.ofSeconds(2)

const val MEDIA_BASE_PATH = "/data/wow/media"

data class DownloadedMediaAsset(
    val bytes: ByteArray,
    val contentType: String,
)

@Component
class BlizzardMediaApiClient(
    private val blizzardApiSupport: BlizzardApiSupport,
) {
    private val logger = LoggerFactory.getLogger(BlizzardMediaApiClient::class.java)

    fun getMedia(
        type: String,
        id: Int,
        region: Region,
        sourceHref: String? = null,
    ): BlizzardMediaDTO {
        val uri =
            sourceHref ?: blizzardApiSupport.buildRegionalUri(
                region = region,
                path = "$MEDIA_BASE_PATH/$type/$id",
                namespace = blizzardApiSupport.staticNamespaceForRegion(region).value,
            )
        return blizzardApiSupport
            .webClient()
            .get()
            .uri(uri)
            .retrieve()
            .bodyToMono(BlizzardMediaDTO::class.java)
            .onErrorMap { error ->
                BlizzardApiClientException.from(
                    error = error,
                    operation = "fetch media",
                    url = uri,
                    timeout = MEDIA_API_RETRY_BACKOFF,
                )
            }.retryTransientBlizzardFailures(
                maxRetries = MEDIA_API_RETRY_ATTEMPTS,
                backoff = MEDIA_API_RETRY_BACKOFF,
            ).doOnError { error ->
                logger.logBlizzardHttpFailure(error)
            }.block()!!
    }

    fun downloadAsset(assetUrl: String): DownloadedMediaAsset {
        val response =
            blizzardApiSupport
                .webClient()
                .get()
                .uri(assetUrl)
                .retrieve()
                .toEntity(ByteArray::class.java)
                .onErrorMap { error ->
                    BlizzardApiClientException.from(
                        error = error,
                        operation = "download media asset",
                        url = assetUrl,
                        timeout = MEDIA_API_RETRY_BACKOFF,
                    )
                }.retryTransientBlizzardFailures(
                    maxRetries = MEDIA_API_RETRY_ATTEMPTS,
                    backoff = MEDIA_API_RETRY_BACKOFF,
                ).doOnError { error ->
                    logger.logBlizzardHttpFailure(error)
                }.block()!!

        val bytes = requireNotNull(response.body) { "Media asset body missing for $assetUrl" }
        val contentType = response.headers.getFirst(HttpHeaders.CONTENT_TYPE)?.substringBefore(';') ?: "image/png"
        return DownloadedMediaAsset(bytes = bytes, contentType = contentType)
    }
}
