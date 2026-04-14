package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.item.Item
import net.jonasmf.auctionengine.dto.item.ItemDTO
import net.jonasmf.auctionengine.mapper.toDomain
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration

private const val ITEM_API_RETRY_ATTEMPTS = 3L
private val ITEM_API_RETRY_BACKOFF: Duration = Duration.ofSeconds(2)

const val ITEM_BASE_PATH = "/data/wow/item"

@Component
class ItemApiClient(
    private val blizzardApiSupport: BlizzardApiSupport,
) {
    private val logger: Logger = LoggerFactory.getLogger(ItemApiClient::class.java)

    fun getById(id: Int): Item = getById(id, blizzardApiSupport.defaultRegion())

    fun getById(
        id: Int,
        region: Region,
    ): Item {
        val uri =
            blizzardApiSupport.buildRegionalUri(
                region = region,
                path = "$ITEM_BASE_PATH/$id",
                namespace = blizzardApiSupport.staticNamespaceForRegion(region).value,
            )
        val item =
            blizzardApiSupport
                .webClient()
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono(ItemDTO::class.java)
                .onErrorMap { error ->
                    BlizzardApiClientException.from(
                        error = error,
                        operation = "fetch item",
                        url = uri,
                        timeout = ITEM_API_RETRY_BACKOFF,
                    )
                }.retryTransientBlizzardFailures(
                    maxRetries = ITEM_API_RETRY_ATTEMPTS,
                    backoff = ITEM_API_RETRY_BACKOFF,
                ).doOnError { error ->
                    logger.logBlizzardHttpFailure(error)
                }.block()!!
        return item.toDomain()
    }
}
