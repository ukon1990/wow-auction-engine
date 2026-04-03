package net.jonasmf.auctionengine.dbo.dynamodb

import net.jonasmf.auctionengine.dbo.dynamodb.converters.KotlinInstantAsLongAttributeConverter
import net.jonasmf.auctionengine.repository.dynamodb.AUCTION_HOUSE_UPDATE_LOG_TABLE_NAME
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import java.time.Instant

@DynamoDbBean
data class AuctionHouseUpdateLogDynamo(
    @get:DynamoDbPartitionKey
    var id: Int = 0,

    @get:DynamoDbSortKey
    @get:DynamoDbConvertedBy(KotlinInstantAsLongAttributeConverter::class)
    var lastModified: Instant = Instant.now(),

    var size: Double = 0.0,
    var timeSincePreviousDump: Long = 0L,
    var url: String = "",
) {
    companion object {
        fun createTableRequest(): CreateTableRequest =
            CreateTableRequest
                .builder()
                .tableName(AUCTION_HOUSE_UPDATE_LOG_TABLE_NAME)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                    AttributeDefinition
                        .builder()
                        .attributeName("id")
                        .attributeType(ScalarAttributeType.N)
                        .build(),
                    AttributeDefinition
                        .builder()
                        .attributeName("lastModified")
                        .attributeType(ScalarAttributeType.N)
                        .build(),
                    AttributeDefinition
                        .builder()
                        .attributeName("size")
                        .attributeType(ScalarAttributeType.N)
                        .build(),
                    AttributeDefinition
                        .builder()
                        .attributeName("url")
                        .attributeType(ScalarAttributeType.S)
                        .build(),

                ).keySchema(
                    KeySchemaElement
                        .builder()
                        .attributeName("id")
                        .keyType(KeyType.HASH)
                        .build(),
                    KeySchemaElement
                        .builder()
                        .attributeName("lastModified")
                        .keyType(KeyType.RANGE)
                        .build(),
                ).globalSecondaryIndexes(
                    GlobalSecondaryIndex
                        .builder()
                        .keySchema(
                            KeySchemaElement
                                .builder()
                                .attributeName("id")
                                .keyType(KeyType.HASH)
                                .build(),
                            KeySchemaElement
                                .builder()
                                .attributeName("lastModified")
                                .keyType(KeyType.RANGE)
                                .build(),
                        ).projection { it.projectionType("ALL") }
                        .build(),
                ).build()
    }
}
