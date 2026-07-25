package net.jonasmf.auctionengine.mapper.realm

import java.time.ZoneOffset
import java.util.TimeZone
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse as Dbo
import net.jonasmf.auctionengine.domain.realm.AuctionHouse as Domain
import net.jonasmf.auctionengine.domain.realm.Realm as RealmDomain
import net.jonasmf.auctionengine.generated.model.AuctionHouse as Dto

fun Dbo.toDomain() =
    Domain(
        id = connectedId,
        connectedId = connectedId,
        region = region,
        autoUpdate = autoUpdate,
        avgDelay = avgDelay,
        gameBuild = gameBuild,
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

fun Dbo.toDomain(realms: List<RealmDomain>): Domain =
    Domain(
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

fun Domain.toDbo() =
    Dbo(
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

fun Domain.toDto(): Dto {
    val firstRealm = realms.firstOrNull()
    val zoneId = TimeZone.getTimeZone(firstRealm?.timezone ?: "UTC").toZoneId()
    val offset = ZoneOffset.of(zoneId.id)

    return Dto(
        id = id,
        connectedRealmId = connectedId,
        region = region.toString(),
        realms = realms.map { it.toDto() },
        avgDelay = avgDelay,
        highestDelay = highestDelay,
        lastDailyPriceUpdate = lastDailyPriceUpdate?.toJavaInstant()?.atOffset(offset),
        lastHistoryDeleteEvent = lastHistoryDeleteEvent?.toJavaInstant()?.atOffset(offset),
        lastHistoryDeleteEventDaily = lastHistoryDeleteEventDaily?.toJavaInstant()?.atOffset(offset),
        lastModified = lastModified?.toJavaInstant()?.atOffset(offset),
        lowestDelay = lowestDelay,
        nextUpdate = nextUpdate?.toJavaInstant()?.atOffset(offset),
    )
}
