package net.jonasmf.auctionengine.mapper.realm

import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.TimeZone
import kotlin.time.Instant
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

    return Dto(
        id = id,
        connectedRealmId = connectedId,
        region = region.toString(),
        realms = realms.map { it.toDto() },
        avgDelay = avgDelay,
        highestDelay = highestDelay,
        lastDailyPriceUpdate = lastDailyPriceUpdate?.toOffsetDateTime(zoneId),
        lastHistoryDeleteEvent = lastHistoryDeleteEvent?.toOffsetDateTime(zoneId),
        lastHistoryDeleteEventDaily = lastHistoryDeleteEventDaily?.toOffsetDateTime(zoneId),
        lastModified = lastModified?.toOffsetDateTime(zoneId),
        lowestDelay = lowestDelay,
        nextUpdate = nextUpdate?.toOffsetDateTime(zoneId),
    )
}

private fun Instant.toOffsetDateTime(zoneId: ZoneId): OffsetDateTime =
    toJavaInstant()
        .atZone(zoneId)
        .toOffsetDateTime()
