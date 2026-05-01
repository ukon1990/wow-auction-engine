package net.jonasmf.auctionengine.utility

import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.domain.AuctionHouse
import java.time.ZoneId
import java.time.ZoneOffset

val defaultAuctionHouseZone: ZoneId = ZoneOffset.UTC

fun AuctionHouse.resolveZone(defaultZone: ZoneId = defaultAuctionHouseZone): ZoneId =
    realms.firstOrNull()?.timezone.toZoneOrDefault(defaultZone)

fun ConnectedRealm.resolveZone(defaultZone: ZoneId = defaultAuctionHouseZone): ZoneId =
    realms.firstOrNull()?.timezone.toZoneOrDefault(defaultZone)

fun String?.toZoneOrDefault(defaultZone: ZoneId = defaultAuctionHouseZone): ZoneId {
    if (isNullOrBlank()) {
        return defaultZone
    }
    return runCatching { ZoneId.of(this) }.getOrDefault(defaultZone)
}
