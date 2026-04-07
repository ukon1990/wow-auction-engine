package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dto.auction.AuctionData
import net.jonasmf.auctionengine.dto.auction.AuctionDataResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

private const val AUCTION_DEFAULT_LOCALE = "en_US"
private const val FAR_FUTURE_IF_MODIFIED_SINCE = "Sat, 14 Mar 3000 20:07:10 GMT"
private val AUCTION_METADATA_TIMEOUT: Duration = Duration.ofSeconds(30)
private val AUCTION_DOWNLOAD_TIMEOUT: Duration = Duration.ofMinutes(3)

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
            .toEntity(String::class.java)
            .timeout(AUCTION_METADATA_TIMEOUT)
            .map { response ->
                AuctionDataResponse(
                    lastModified = response.headers.lastModified,
                    url = url,
                    gameBuild = gameBuild,
                )
            }.doOnNext {
                logger.info("Fetched latest auction dump metadata for id={} with lastModified={}", id, it.lastModified)
            }.doOnError { error ->
                logger.error("Failed to fetch latest auction dump metadata from {}: {}", url, error.message, error)
            }.subscribeOn(Schedulers.boundedElastic())
    }

    fun downloadAuctionData(url: String): Mono<AuctionData> =
        blizzardApiSupport
            .webClient()
            .get()
            .uri(url)
            .retrieve()
            .toEntity(AuctionData::class.java)
            .timeout(AUCTION_DOWNLOAD_TIMEOUT)
            .map { response ->
                val contentLength = response.headers.contentLength
                logger.info(
                    "Starting auction payload download from {} with contentLength={}B",
                    url,
                    if (contentLength >= 0) contentLength else "unknown",
                )
                val body = checkNotNull(response.body) { "Auction download response body was empty" }
                logger.info(
                    "Completed auction payload download from {} with {} auctions and contentLength={}B",
                    url,
                    body.auctions.size,
                    if (contentLength >= 0) contentLength else "unknown",
                )
                body
            }.doOnError { error ->
                logger.error("Failed to fetch auction data from {}: {}", url, error.message, error)
            }
}
