package net.jonasmf.auctionengine.dbo.dynamodb

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean

@DynamoDbBean
data class StatsDynamo(
    var lastModified: Long = 0,
    var url: String = "",
)
