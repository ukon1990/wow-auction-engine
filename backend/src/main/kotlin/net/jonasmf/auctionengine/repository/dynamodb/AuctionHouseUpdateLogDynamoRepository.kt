package net.jonasmf.auctionengine.repository.dynamodb

import io.awspring.cloud.dynamodb.DynamoDbOperations
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseUpdateLogDynamo
import net.jonasmf.auctionengine.domain.AuctionHouseUpdateLog
import net.jonasmf.auctionengine.mapper.toDomain
import org.springframework.stereotype.Repository
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest
import kotlin.time.Instant
import kotlin.time.toJavaInstant

const val AUCTION_HOUSE_UPDATE_LOG_TABLE_NAME = "wah_auction_houses_update_log"

@Deprecated(
    message =
        "Retained as a fallback after issue #26 " +
            "because DynamoDB cost was not worth it for the auction-house scheduling path.",
)
interface AuctionHouseUpdateLogDynamoRepository {
    fun findByIdAndMostRecentLastModified(connectedRealmId: Int): List<AuctionHouseUpdateLog>

    fun findNewestEntryForConnectedRealm(connectedRealmId: Int): AuctionHouseUpdateLog?

    fun save(
        connectedId: Int,
        lastModified: Instant,
        size: Double,
        url: String,
    ): AuctionHouseUpdateLog
}

@Repository
@Deprecated(
    message =
        "Retained as a fallback after issue #26 " +
            "because DynamoDB cost was not worth it for the auction-house scheduling path.",
)
class AuctionHouseUpdateLogDynamoRepositoryImpl(
    private val dynamoDbOperations: DynamoDbOperations,
) : AuctionHouseUpdateLogDynamoRepository {
    /**
     * Returns a descended sorted list of the most recent 72 entries for the given connected realm id.
     */
    override fun findByIdAndMostRecentLastModified(connectedRealmId: Int): List<AuctionHouseUpdateLog> {
        val query =
            QueryEnhancedRequest
                .builder()
                .queryConditional(
                    QueryConditional
                        .keyEqualTo(
                            Key
                                .builder()
                                .partitionValue(connectedRealmId)
                                .build(),
                        ),
                ).scanIndexForward(false)
                // Would be about 72 hours ish depending on if the realm is actively updated or not
                .limit(72)
                .build()

        val pages =
            dynamoDbOperations.query<AuctionHouseUpdateLogDynamo>(
                query,
                AuctionHouseUpdateLogDynamo::class.java,
            )
        return pages.items().toList().map { it.toDomain() }
    }

    override fun findNewestEntryForConnectedRealm(connectedRealmId: Int): AuctionHouseUpdateLog? {
        val query =
            QueryEnhancedRequest
                .builder()
                .queryConditional(
                    QueryConditional
                        .keyEqualTo(
                            Key
                                .builder()
                                .partitionValue(connectedRealmId)
                                .build(),
                        ),
                ).scanIndexForward(false)
                .limit(1)
                .build()

        val pages =
            dynamoDbOperations.query<AuctionHouseUpdateLogDynamo>(
                query,
                AuctionHouseUpdateLogDynamo::class.java,
            )
        val items = pages.items().toList()
        return if (items.isNotEmpty()) {
            pages
                .items()
                .toList()
                .map { it.toDomain() }
                .first()
        } else {
            null
        }
    }

    override fun save(
        connectedId: Int,
        lastModified: Instant,
        size: Double,
        url: String,
    ): AuctionHouseUpdateLog {
        val previousLogEntry = this.findNewestEntryForConnectedRealm(connectedId)
        val timeSincePrevious =
            if (previousLogEntry == null) {
                0
            } else {
                lastModified.minus(previousLogEntry.lastModified).inWholeMilliseconds
            }

        return dynamoDbOperations
            .save(
                AuctionHouseUpdateLogDynamo(
                    id = connectedId,
                    lastModified = lastModified.toJavaInstant(),
                    timeSincePreviousDump = timeSincePrevious,
                    size = size,
                    url = url,
                ),
            ).toDomain()
    }
}
