package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dto.auction.AuctionDataResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import kotlin.io.path.createTempFile

private const val AUCTION_DEFAULT_LOCALE = "en_US"
private const val FAR_FUTURE_IF_MODIFIED_SINCE = "Sat, 14 Mar 3000 20:07:10 GMT"
private val AUCTION_METADATA_TIMEOUT: Duration = Duration.ofSeconds(30)
private val AUCTION_DOWNLOAD_TIMEOUT: Duration = Duration.ofMinutes(3)

data class DownloadedAuctionPayload(
    val path: Path,
    val contentLength: Long?,
)

@Component
class BlizzardAuctionApiClient(
    private val blizzardApiSupport: BlizzardApiSupport,
) {
    private val logger: Logger = LoggerFactory.getLogger(BlizzardAuctionApiClient::class.java)

    fun getLatestAuctionDump(
        id: Int,
        region: Region,
        gameBuild: GameBuildVersion = GameBuildVersion.RETAIL,
    ): Mono<AuctionDataResponse> {
        val path =
            if (id < 0) {
                "auctions/commodities"
            } else {
                "connected-realm/$id/auctions${if (gameBuild == GameBuildVersion.CLASSIC) "/index" else ""}"
            }
        val namespace = blizzardApiSupport.dynamicNamespaceForRegion(region)
        val url =
            blizzardApiSupport.buildRegionalUri(
                region = region,
                path = path,
                namespace = namespace.value,
                locale = AUCTION_DEFAULT_LOCALE,
            )

        logger.debug("Fetching latest auction dump for id={}, region={}, path={}", id, region, path)

        return blizzardApiSupport
            .webClient()
            .get()
            .uri(url)
            .header(HttpHeaders.IF_MODIFIED_SINCE, FAR_FUTURE_IF_MODIFIED_SINCE)
            .retrieve()
            .toBodilessEntity()
            .timeout(AUCTION_METADATA_TIMEOUT)
            .onErrorMap { error ->
                BlizzardApiClientException.from(
                    error = error,
                    operation = "fetch latest auction dump metadata",
                    url = url,
                    realmId = id,
                    timeout = AUCTION_METADATA_TIMEOUT,
                )
            }.doOnError { error ->
                logger.logBlizzardHttpFailure(error)
            }.map { response ->
                AuctionDataResponse(
                    lastModified = response.headers.lastModified,
                    url = url,
                    gameBuild = gameBuild,
                )
            }.doOnNext {
                logger.info("Fetched latest auction dump metadata for id={} with lastModified={}", id, it.lastModified)
            }.subscribeOn(Schedulers.boundedElastic())
    }

    fun downloadAuctionData(url: String): Mono<DownloadedAuctionPayload> =
        Mono
            .fromCallable { createTempFile(prefix = "auction-payload-", suffix = ".json") }
            .flatMap { tempFile ->
                blizzardApiSupport
                    .webClient()
                    .get()
                    .uri(url)
                    .exchangeToMono { response ->
                        if (response.statusCode().isError) {
                            response.createException().flatMap { Mono.error(it) }
                        } else {
                            val contentLength =
                                response
                                    .headers()
                                    .contentLength()
                                    .takeIf { it.isPresent }
                                    ?.asLong
                            logger.info(
                                "Starting auction payload download from {} with contentLength={}B into {}",
                                url,
                                contentLength ?: "unknown",
                                tempFile,
                            )
                            DataBufferUtils
                                .write(
                                    response.bodyToFlux(DataBuffer::class.java),
                                    tempFile,
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING,
                                ).thenReturn(
                                    DownloadedAuctionPayload(
                                        path = tempFile,
                                        contentLength = contentLength,
                                    ),
                                )
                        }
                    }.timeout(AUCTION_DOWNLOAD_TIMEOUT)
                    .onErrorResume { error ->
                        tempFile.toFile().delete()
                        Mono.error(error)
                    }
            }.doOnNext { payload ->
                logger.info(
                    "Completed auction payload download from {} into {} with contentLength={}B",
                    url,
                    payload.path,
                    payload.contentLength ?: "unknown",
                )
            }.onErrorMap { error ->
                BlizzardApiClientException.from(
                    error = error,
                    operation = "download auction payload",
                    url = url,
                    timeout = AUCTION_DOWNLOAD_TIMEOUT,
                )
            }.doOnError { error ->
                logger.logBlizzardHttpFailure(error)
            }
}
