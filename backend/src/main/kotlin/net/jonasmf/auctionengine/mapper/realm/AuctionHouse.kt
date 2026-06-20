package net.jonasmf.auctionengine.mapper.realm

import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse as AuctionHouseDbo
import net.jonasmf.auctionengine.domain.realm.AuctionHouse as AuctionHouseDomain
import net.jonasmf.auctionengine.domain.realm.Realm as RealmDomain

fun AuctionHouseDbo.toDomain() =
    AuctionHouseDomain(
        id = connectedId,
        connectedId = connectedId,
        region = region,
        autoUpdate = autoUpdate,
        avgDelay = avgDelay ?: 0L,
        gameBuild = gameBuild ?: 0,
        highestDelay = highestDelay,
        lastDailyPriceUpdate = lastDailyPriceUpdate?.toKotlinInstant(),
        lastHistoryDeleteEvent = lastHistoryDeleteEvent?.toKotlinInstant(),
        lastHistoryDeleteEventDaily = lastHistoryDeleteEventDaily?.toKotlinInstant(),
        lastModified = lastModified?.toKotlinInstant(),
        lastRequested = lastRequested?.toKotlinInstant(),
        lowestDelay = lowestDelay,
        nextUpdate = nextUpdate?.toKotlinInstant(),
        updateAttempts = updateAttempts,
    )

fun AuctionHouseDbo.toDomain(realms: List<RealmDomain>): AuctionHouseDomain =
    AuctionHouseDomain(
        id = connectedId,
        region = region,
        autoUpdate = autoUpdate,
        avgDelay = avgDelay,
        connectedId = connectedId,
        gameBuild = gameBuild,
        highestDelay = highestDelay,
        lastDailyPriceUpdate = lastDailyPriceUpdate?.toKotlinInstant(),
        lastHistoryDeleteEvent = lastHistoryDeleteEvent?.toKotlinInstant(),
        lastHistoryDeleteEventDaily = lastHistoryDeleteEventDaily?.toKotlinInstant(),
        lastModified = lastModified?.toKotlinInstant(),
        lastRequested = lastRequested?.toKotlinInstant(),
        lowestDelay = lowestDelay,
        nextUpdate = nextUpdate?.toKotlinInstant(),
        realms = realms,
        updateAttempts = updateAttempts,
    )

fun AuctionHouseDomain.toDbo() =
    AuctionHouseDbo(
        connectedId = connectedId.takeIf { it != 0 } ?: id ?: 0,
        region = region,
        autoUpdate = autoUpdate,
        avgDelay = avgDelay,
        gameBuild = gameBuild,
        highestDelay = highestDelay,
        lastDailyPriceUpdate = lastDailyPriceUpdate?.toJavaInstant(),
        lastHistoryDeleteEvent = lastHistoryDeleteEvent?.toJavaInstant(),
        lastHistoryDeleteEventDaily = lastHistoryDeleteEventDaily?.toJavaInstant(),
        lastModified = lastModified?.toJavaInstant(),
        lastRequested = lastRequested?.toJavaInstant(),
        lowestDelay = lowestDelay,
        nextUpdate = nextUpdate?.toJavaInstant(),
        updateAttempts = updateAttempts,
    )
