package net.jonasmf.auctionengine.dbo.dynamodb

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable

@DynamoDBTable(tableName = "wah_auction_houses")
data class AuctionHouseDynamo(
    @DynamoDBHashKey(attributeName = "id")
    val id: Int,
    @DynamoDBAttribute(attributeName = "autoUpdate")
    val autoUpdate: Boolean = false,
    @DynamoDBAttribute(attributeName = "avgDelay")
    val avgDelay: Long = 0,
    @DynamoDBAttribute(attributeName = "connectedId")
    val connectedId: Long = 0,
    @DynamoDBAttribute(attributeName = "gameBuild")
    val gameBuild: Long = 0,
    @DynamoDBAttribute(attributeName = "highestDelay")
    val highestDelay: Long = 0,
    @DynamoDBAttribute(attributeName = "lastDailyPriceUpdate")
    val lastDailyPriceUpdate: Long = 0,
    @DynamoDBAttribute(attributeName = "lastHistoryDeleteEvent")
    val lastHistoryDeleteEvent: Long = 0,
    @DynamoDBAttribute(attributeName = "lastHistoryDeleteEventDaily")
    val lastHistoryDeleteEventDaily: Long = 0,
    @DynamoDBAttribute(attributeName = "lastModified")
    val lastModified: Long = 0,
    @DynamoDBAttribute(attributeName = "lastRequested")
    val lastRequested: Long = 0,
    @DynamoDBAttribute(attributeName = "lastStatsInsert")
    val lastStatsInsert: Long = 0,
    @DynamoDBAttribute(attributeName = "lastTrendUpdateInitiation")
    val lastTrendUpdateInitiation: Long = 0,
    @DynamoDBAttribute(attributeName = "lowestDelay")
    val lowestDelay: Long = 0,
    @DynamoDBAttribute(attributeName = "nextUpdate")
    val nextUpdate: Long = 0,
    @DynamoDBAttribute(attributeName = "realms")
    val realms: List<RealmDynamo> = emptyList(),
    @DynamoDBAttribute(attributeName = "realmSlugs")
    val realmSlugs: String = "",
    @DynamoDBAttribute(attributeName = "region")
    val region: String = "",
    @DynamoDBAttribute(attributeName = "size")
    val size: Double = 0.0,
    @DynamoDBAttribute(attributeName = "stats")
    val stats: StatsDynamo = StatsDynamo(
        lastModified = 0L,
        url = "",
    ),
    @DynamoDBAttribute(attributeName = "updateAttempts")
    val updateAttempts: Long = 0,
    @DynamoDBAttribute(attributeName = "url")
    val url: String = "",
)
