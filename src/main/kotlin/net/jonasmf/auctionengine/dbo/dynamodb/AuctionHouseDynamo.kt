package net.jonasmf.auctionengine.dbo.dynamodb

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.dynamodb.converters.KotlinInstantAsLongAttributeConverter
import net.jonasmf.auctionengine.dbo.dynamodb.converters.toKotlin
import net.jonasmf.auctionengine.domain.AuctionHouse
import net.jonasmf.auctionengine.repository.dynamodb.AUCTION_HOUSE_TABLE_NAME
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy
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
import java.time.Instant

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
    @get:DynamoDbConvertedBy(KotlinInstantAsLongAttributeConverter::class)
    var lastDailyPriceUpdate: Instant? = null,
    @get:DynamoDbConvertedBy(KotlinInstantAsLongAttributeConverter::class)
    var lastHistoryDeleteEvent: Instant? = null,
    @get:DynamoDbConvertedBy(KotlinInstantAsLongAttributeConverter::class)
    var lastHistoryDeleteEventDaily: Instant? = null,
    @get:DynamoDbConvertedBy(KotlinInstantAsLongAttributeConverter::class)
    var lastModified: Instant? = null,
    @get:DynamoDbConvertedBy(KotlinInstantAsLongAttributeConverter::class)
    var lastRequested: Instant? = null,
    @get:DynamoDbConvertedBy(KotlinInstantAsLongAttributeConverter::class)
    var lastStatsInsert: Instant? = null,
    @get:DynamoDbConvertedBy(KotlinInstantAsLongAttributeConverter::class)
    var lastTrendUpdateInitiation: Instant? = null,
    var lowestDelay: Int = 0,
    @get:DynamoDbSecondarySortKey(indexNames = ["region-index"])
    @get:DynamoDbConvertedBy(KotlinInstantAsLongAttributeConverter::class)
    var nextUpdate: Instant? = null,
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
