package net.jonasmf.auctionengine.dbo.dynamodb

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.repository.dynamodb.AUCTION_HOUSE_TABLE_NAME
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import kotlin.time.Instant

@DynamoDbBean
data class AuctionHouseDynamo(
    @get:DynamoDbPartitionKey
    var id: Int? = null,
    @get:DynamoDbSecondaryPartitionKey(indexNames = ["region-index"])
    var region: Region = Region.Europe,
    var autoUpdate: Boolean = false,
    var avgDelay: Long = 0,
    var connectedId: Int = 0,
    var gameBuild: Int = 0,
    var highestDelay: Long = 0,
    var lastDailyPriceUpdate: Long = 0,
    var lastHistoryDeleteEvent: Long = 0,
    var lastHistoryDeleteEventDaily: Long = 0,
    var lastModified: Instant = Instant.fromEpochMilliseconds(0),
    var lastRequested: Instant = Instant.fromEpochMilliseconds(0),
    var lastStatsInsert: Instant = Instant.fromEpochMilliseconds(0),
    var lastTrendUpdateInitiation: Instant = Instant.fromEpochMilliseconds(0),
    var lowestDelay: Instant = Instant.fromEpochMilliseconds(0),
    @get:DynamoDbSecondarySortKey(indexNames = ["region-index"])
    var nextUpdate: Instant = Instant.fromEpochMilliseconds(0),
    var realms: List<RealmDynamo> = emptyList(),
    var realmSlugs: String = "",
    var size: Double = 0.0,
    var stats: StatsDynamo =
        StatsDynamo(
            lastModified = 0L,
            url = "",
        ),
    var updateAttempts: Int = 0,
    var url: String = "",
) {
    companion object {
        fun createTableRequest(): CreateTableRequest =
            CreateTableRequest
                .builder()
                .tableName(AUCTION_HOUSE_TABLE_NAME)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                    AttributeDefinition
                        .builder()
                        .attributeName("id")
                        .attributeType(ScalarAttributeType.N)
                        .build(),
                    AttributeDefinition
                        .builder()
                        .attributeName("region")
                        .attributeType(ScalarAttributeType.S)
                        .build(),
                    AttributeDefinition
                        .builder()
                        .attributeName("nextUpdate")
                        .attributeType(ScalarAttributeType.N)
                        .build(),
                ).keySchema(
                    KeySchemaElement
                        .builder()
                        .attributeName("id")
                        .keyType(KeyType.HASH)
                        .build(),
                ).globalSecondaryIndexes(
                    GlobalSecondaryIndex.builder()
                        .indexName("region-index")
                        .keySchema(
                            KeySchemaElement
                                .builder()
                                .attributeName("region")
                                .keyType(KeyType.HASH)
                                .build(),
                            KeySchemaElement
                                .builder()
                                .attributeName("nextUpdate")
                                .keyType(KeyType.RANGE)
                                .build(),
                        )
                        .projection { it.projectionType("ALL") }
                        .build(),
                ).build()

    }
}
