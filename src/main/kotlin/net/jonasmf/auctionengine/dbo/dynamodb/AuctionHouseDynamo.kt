package net.jonasmf.auctionengine.dbo.dynamodb

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable

@DynamoDBTable(tableName = "wah_auction_houses")
data class AuctionHouseDynamo(
    @DynamoDBHashKey(attributeName = "id")
    val id: Long,
    @DynamoDBAttribute(attributeName = "autoUpdate")
    val autoUpdate: Boolean,
    @DynamoDBAttribute(attributeName = "avgDelay")
    val avgDelay: Long,
    @DynamoDBAttribute(attributeName = "connectedId")
    val connectedId: Long,
    @DynamoDBAttribute(attributeName = "gameBuild")
    val gameBuild: Long,
    @DynamoDBAttribute(attributeName = "highestDelay")
    val highestDelay: Long,
    @DynamoDBAttribute(attributeName = "lastDailyPriceUpdate")
    val lastDailyPriceUpdate: Long,
    @DynamoDBAttribute(attributeName = "lastHistoryDeleteEvent")
    val lastHistoryDeleteEvent: Long,
    @DynamoDBAttribute(attributeName = "lastHistoryDeleteEventDaily")
    val lastHistoryDeleteEventDaily: Long,
    @DynamoDBAttribute(attributeName = "lastModified")
    val lastModified: Long,
    @DynamoDBAttribute(attributeName = "lastRequested")
    val lastRequested: Long,
    @DynamoDBAttribute(attributeName = "lastStatsInsert")
    val lastStatsInsert: Long,
    @DynamoDBAttribute(attributeName = "lastTrendUpdateInitiation")
    val lastTrendUpdateInitiation: Long,
    @DynamoDBAttribute(attributeName = "lowestDelay")
    val lowestDelay: Long,
    @DynamoDBAttribute(attributeName = "nextUpdate")
    val nextUpdate: Long,
    @DynamoDBAttribute(attributeName = "realms")
    val realms: List<RealmDynamo>,
    @DynamoDBAttribute(attributeName = "realmSlugs")
    val realmSlugs: String,
    @DynamoDBAttribute(attributeName = "region")
    val region: String,
    @DynamoDBAttribute(attributeName = "size")
    val size: Double,
    @DynamoDBAttribute(attributeName = "stats")
    val stats: StatsDynamo,
    @DynamoDBAttribute(attributeName = "updateAttempts")
    val updateAttempts: Long,
    @DynamoDBAttribute(attributeName = "url")
    val url: String,
)
