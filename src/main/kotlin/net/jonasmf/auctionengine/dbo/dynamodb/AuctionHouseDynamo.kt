package net.jonasmf.auctionengine.dbo.dynamodb

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey

@DynamoDbBean
data class AuctionHouseDynamo(
    @get:DynamoDbPartitionKey
    var id: Int? = null,
    var autoUpdate: Boolean = false,
    var avgDelay: Long = 0,
    var connectedId: Long = 0,
    var gameBuild: Long = 0,
    var highestDelay: Long = 0,
    var lastDailyPriceUpdate: Long = 0,
    var lastHistoryDeleteEvent: Long = 0,
    var lastHistoryDeleteEventDaily: Long = 0,
    var lastModified: Long = 0,
    var lastRequested: Long = 0,
    var lastStatsInsert: Long = 0,
    var lastTrendUpdateInitiation: Long = 0,
    var lowestDelay: Long = 0,
    var nextUpdate: Long = 0,
    var realms: List<RealmDynamo> = emptyList(),
    var realmSlugs: String = "",
    var region: String = "",
    var size: Double = 0.0,
    var stats: StatsDynamo =
        StatsDynamo(
            lastModified = 0L,
            url = "",
        ),
    var updateAttempts: Long = 0,
    var url: String = "",
)
