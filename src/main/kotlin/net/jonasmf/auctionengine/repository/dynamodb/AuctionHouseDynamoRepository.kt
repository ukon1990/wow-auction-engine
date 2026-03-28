package net.jonasmf.auctionengine.repository.dynamodb

import io.awspring.cloud.dynamodb.DynamoDbOperations
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import org.springframework.stereotype.Repository
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest
import java.time.ZonedDateTime
import java.util.Optional

const val AUCTION_HOUSE_TABLE_NAME = "wah_auction_houses"

interface AuctionHouseDynamoRepository {
    fun findById(id: Int?): Optional<AuctionHouseDynamo>

    fun findAllByRegion(region: Region): List<AuctionHouseDynamo>

    fun findReadyForUpdateByRegion(region: Region): List<AuctionHouseDynamo>

    fun save(auctionHouse: AuctionHouseDynamo): AuctionHouseDynamo
}

@Repository
class AuctionHouseDynamoRepositoryIml(
    private val dynamoDbOperations: DynamoDbOperations,
) : AuctionHouseDynamoRepository {
    override fun findById(id: Int?): Optional<AuctionHouseDynamo> {
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

        return Optional.ofNullable(entity)
    }

    override fun findAllByRegion(region: Region): List<AuctionHouseDynamo> {
        val queryParams = QueryEnhancedRequest
            .builder()
            .queryConditional(
                QueryConditional
                    .keyEqualTo(
                        Key.builder()
                            .partitionValue(region.name)
                            .build(),
                    ),
            )
            .build()

        return queryByRegionIndex(queryParams)
    }

    /**
     * Checks for the next connected realms to update.
     * Returns the 50 oldest updates for the given region, sorted by nextUpdate ascending.
     */
    override fun findReadyForUpdateByRegion(region: Region): List<AuctionHouseDynamo> {
        val queryParams = QueryEnhancedRequest
            .builder()
            .queryConditional(
                QueryConditional
                    .sortLessThanOrEqualTo(
                        Key.builder()
                            .partitionValue(region.name)
                            .sortValue(ZonedDateTime.now().toEpochSecond())
                            .build(),
                    ),
            )
            .limit(50)
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

        return pages.items().toList()
    }

    override fun save(auctionHouse: AuctionHouseDynamo): AuctionHouseDynamo {
        requireNotNull(auctionHouse.id) { "AuctionHouseDynamo.id must not be null when saving to DynamoDB" }
        return dynamoDbOperations.save(auctionHouse)
    }
}
