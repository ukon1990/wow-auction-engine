package net.jonasmf.auctionengine.repository.dynamodb

import io.awspring.cloud.dynamodb.DynamoDbOperations
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import org.springframework.stereotype.Repository
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.util.*

const val AUCTION_HOUSE_TABLE_NAME = "wah_auction_houses"

interface AuctionHouseDynamoRepository {
    fun findById(id: Int?): Optional<AuctionHouseDynamo>

    fun findAllByRegion(region: Region): List<AuctionHouseDynamo>

    fun save(auctionHouse: AuctionHouseDynamo): AuctionHouseDynamo
}

@Repository
class DynamoDbAuctionHouseRepository(
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

        val query = QueryEnhancedRequest
            .builder()
            .filterExpression(
                Expression
                    .builder()
                    .expression("region = :region")
                    .putExpressionValue(":region", AttributeValue.builder().s(region.name).build())
                    .build(),
            )
            .build()

        val entity =
            dynamoDbOperations.query<AuctionHouseDynamo>(
                query,
                AuctionHouseDynamo::class.java,
            )

        return entity ?: emptyList<AuctionHouseDynamo>()
    }

    override fun save(auctionHouse: AuctionHouseDynamo): AuctionHouseDynamo {
        requireNotNull(auctionHouse.id) { "AuctionHouseDynamo.id must not be null when saving to DynamoDB" }
        return dynamoDbOperations.save(auctionHouse)
    }
}
