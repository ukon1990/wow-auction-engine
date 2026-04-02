package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseUpdateLogDynamo
import net.jonasmf.auctionengine.dbo.dynamodb.converters.toKotlin
import net.jonasmf.auctionengine.domain.AuctionHouseUpdateLog
import kotlin.time.toJavaInstant

fun AuctionHouseUpdateLogDynamo.toDomain() = AuctionHouseUpdateLog(
    id = id,
    lastModified = lastModified.toKotlin(),
    size = size,
    timeSincePreviousDump = timeSincePreviousDump,
    url = url
)

fun AuctionHouseUpdateLog.toDBO() = AuctionHouseUpdateLogDynamo(
    id = id,
    lastModified = lastModified.toJavaInstant(),
    size = size,
    timeSincePreviousDump = timeSincePreviousDump,
    url = url
)
