package net.jonasmf.auctionengine.repository.dynamodb

import io.awspring.cloud.dynamodb.DynamoDbOperations
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.domain.AuctionHouse
import net.jonasmf.auctionengine.mapper.toDbo
import net.jonasmf.auctionengine.mapper.toDomain
import org.springframework.stereotype.Repository
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest
import java.time.Instant
import java.util.Optional

const val AUCTION_HOUSE_TABLE_NAME = "wah_auction_houses"

@Deprecated(
    message =
        "Retained as a fallback after issue #26 " +
            "because DynamoDB cost was not worth it for the auction-house scheduling path.",
)
interface AuctionHouseDynamoRepository {
    fun findById(id: Int?): Optional<AuctionHouse>

    fun findAllByRegion(region: Region): List<AuctionHouseDynamo>

    fun findReadyForUpdateByRegion(region: Region): List<AuctionHouseDynamo>

    fun save(auctionHouse: AuctionHouse): AuctionHouseDynamo
}

@Repository
@Deprecated(
    message =
        "Retained as a fallback after issue #26 " +
            "because DynamoDB cost was not worth it for the auction-house scheduling path.",
)
class AuctionHouseDynamoRepositoryIml(
    private val dynamoDbOperations: DynamoDbOperations,
    private val logRepository: AuctionHouseUpdateLogDynamoRepository,
) : AuctionHouseDynamoRepository {
    override fun findById(id: Int?): Optional<AuctionHouse> {
        if (id == null) {
            return Optional.empty()
        }

        val entity =
            dynamoDbOperations.load(
                Key
                    .builder()
                    .partitionValue(id)
                    .build(),
                AuctionHouseDynamo::class.java,
            )

        return Optional.ofNullable(entity?.toDomain())
    }

    override fun findAllByRegion(region: Region): List<AuctionHouseDynamo> {
        val queryParams =
            QueryEnhancedRequest
                .builder()
                .queryConditional(
                    QueryConditional
                        .keyEqualTo(
                            Key
                                .builder()
                                .partitionValue(region.name)
                                .build(),
                        ),
                ).build()

        return queryByRegionIndex(queryParams)
    }

    /**
     * Checks for the next connected realms to update.
     * Returns the 50 oldest updates for the given region, sorted by nextUpdate ascending.
     */
    override fun findReadyForUpdateByRegion(region: Region): List<AuctionHouseDynamo> {
        val queryParams =
            QueryEnhancedRequest
                .builder()
                .queryConditional(
                    QueryConditional
                        .sortLessThanOrEqualTo(
                            Key
                                .builder()
                                .partitionValue(region.name)
                                .sortValue(Instant.now().toEpochMilli())
                                .build(),
                        ),
                ).limit(50)
                .build()

        return queryByRegionIndex(queryParams)
    }

    private fun queryByRegionIndex(queryParams: QueryEnhancedRequest): List<AuctionHouseDynamo> {
        val pages =
            dynamoDbOperations.query<AuctionHouseDynamo>(
                queryParams,
                AuctionHouseDynamo::class.java,
                "region-index",
            )

        return pages
            .items()
            .asSequence()
            .take(50)
            .toList()
    }

    /**
     * Updates the auction house and appends to the log
     */
    override fun save(auctionHouse: AuctionHouse): AuctionHouseDynamo {
        requireNotNull(auctionHouse.id) { "AuctionHouseDynamo.id must not be null when saving to DynamoDB" }
        requireNotNull(value = auctionHouse.lastModified) {
            "AuctionHouseDynamo.lastModified cannot be null when saving to DynamoDB"
        }
        requireNotNull(auctionHouse.url) { "AuctionHouseDynamo.url cannot be null when saving to DynamoDB" }

        val savedResult = dynamoDbOperations.save(auctionHouse.toDbo())

        logRepository.save(
            auctionHouse.id!!,
            auctionHouse.lastModified!!,
            auctionHouse.size,
            savedResult.url,
        )
        return savedResult
    }
}
