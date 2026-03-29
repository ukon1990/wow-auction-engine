package net.jonasmf.auctionengine.mapper

import net.jonasmf.auctionengine.dbo.dynamodb.AuctionHouseDynamo
import net.jonasmf.auctionengine.dbo.dynamodb.converters.toKotlin
import net.jonasmf.auctionengine.domain.AuctionHouse
import kotlin.time.toJavaInstant

fun AuctionHouse.toDbo() = AuctionHouseDynamo(
    id = id,
    region = region,
    autoUpdate = autoUpdate,
    avgDelay = avgDelay,
    connectedId = connectedId,
    gameBuild = gameBuild,
    highestDelay = highestDelay,
    lastDailyPriceUpdate = lastDailyPriceUpdate?.toJavaInstant(),
    lastHistoryDeleteEvent = lastHistoryDeleteEvent?.toJavaInstant(),
    lastHistoryDeleteEventDaily = lastHistoryDeleteEventDaily?.toJavaInstant(),
    lastModified = lastModified?.toJavaInstant(),
    lastRequested = lastRequested?.toJavaInstant(),
    lastStatsInsert = lastStatsInsert?.toJavaInstant(),
    lastTrendUpdateInitiation = lastTrendUpdateInitiation?.toJavaInstant(),
    lowestDelay = lowestDelay,
    nextUpdate = nextUpdate?.toJavaInstant(),
    realms = realms,
    realmSlugs = realmSlugs,
    size = size,
    stats = stats,
    updateAttempts = updateAttempts,
    url = url,
)

fun AuctionHouseDynamo.toDomain() = AuctionHouse(
    id = id,
    region = region,
    autoUpdate = autoUpdate,
    connectedId = connectedId,
    gameBuild = gameBuild,
    highestDelay = highestDelay,
    lastDailyPriceUpdate = lastDailyPriceUpdate?.toKotlin(),
    lastHistoryDeleteEvent = lastHistoryDeleteEvent?.toKotlin(),
    lastModified = lastModified?.toKotlin(),
    lastRequested = lastRequested?.toKotlin(),
    lastStatsInsert = lastStatsInsert?.toKotlin(),
    lastTrendUpdateInitiation = lastTrendUpdateInitiation?.toKotlin(),
    lowestDelay = lowestDelay,
    nextUpdate = nextUpdate?.toKotlin(),
    realms = realms,
    realmSlugs = realmSlugs,
    size = size,
    updateAttempts = updateAttempts,
    url = url,
    stats = stats,
)
