package net.jonasmf.auctionengine.dbo.dynamodb

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument

@DynamoDBDocument
data class StatsDynamo(
    @DynamoDBAttribute(attributeName = "lastModified")
    val lastModified: Long,
    @DynamoDBAttribute(attributeName = "url")
    val url: String,
)
