package net.jonasmf.auctionengine.dbo.dynamodb

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean

@DynamoDbBean
data class RealmDynamo(
    var id: Long = 0,
    var locale: String = "",
    var name: String = "",
    var slug: String = "",
    var timezone: String = "",
)
