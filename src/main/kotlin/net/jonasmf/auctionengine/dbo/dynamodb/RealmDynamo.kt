package net.jonasmf.auctionengine.dbo.dynamodb

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument

@DynamoDBDocument
data class RealmDynamo(
    @DynamoDBAttribute(attributeName = "id")
    val id: Long,
    @DynamoDBAttribute(attributeName = "locale")
    val locale: String,
    @DynamoDBAttribute(attributeName = "name")
    val name: String,
    @DynamoDBAttribute(attributeName = "slug")
    val slug: String,
    @DynamoDBAttribute(attributeName = "timezone")
    val timezone: String,
)
