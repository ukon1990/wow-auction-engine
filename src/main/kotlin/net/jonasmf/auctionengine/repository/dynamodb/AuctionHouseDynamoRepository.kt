package net.jonasmf.auctionengine.repository.dynamodb

import io.awspring.cloud.dynamodb.DynamoDbOperations
import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import org.springframework.stereotype.Repository
import software.amazon.awssdk.enhanced.dynamodb.Key
import java.util.Optional

const val AUCTION_HOUSE_TABLE_NAME = "wah_auction_houses"

interface AuctionHouseDynamoRepository {
    fun findById(id: Int?): Optional<AuctionHouseDynamo>

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

    override fun save(auctionHouse: AuctionHouseDynamo): AuctionHouseDynamo {
        requireNotNull(auctionHouse.id) { "AuctionHouseDynamo.id must not be null when saving to DynamoDB" }
        return dynamoDbOperations.save(auctionHouse)
    }
}
