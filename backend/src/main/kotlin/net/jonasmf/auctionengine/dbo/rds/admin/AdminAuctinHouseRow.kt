package net.jonasmf.auctionengine.dbo.rds.admin

import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.realm.AuctionHouse
import net.jonasmf.auctionengine.domain.realm.Realm
import java.time.Instant
import kotlin.time.toKotlinInstant
import net.jonasmf.auctionengine.domain.realm.Region as RegionDomain

data class AdminAuctinHouseRow(
    val pageTotalItems: Long,
    val auctionHouseId: Int,
    val auctionHouseConnectedId: Int,
    val auctionHouseAutoUpdate: Boolean,
    val auctionHouseRegion: String,

    val auctionHouseLowestDelay: Long,
    val auctionHouseAvgDelay: Long,
    val auctionHouseHighestDelay: Long,

    val auctionHouseLastAuctionPriceDeleteEvent: Instant,
    val auctionHouseLastHistoryDeleteEvent: Instant,
    val auctionHouseLastHistoryDeleteEventDaily: Instant,

    val auctionHouseLastModified: Instant,
    val auctionHouseNextUpdate: Instant,

    val realmId: Int,
    val realmSlug: String,
    val realmName: String,
    val realmLocale: String,
    val realmCategory: String,
    val realmGameBuild: String,
    val realmTimeZone: String,
)

fun AdminAuctinHouseRow.toRealmDomain(): Realm =
    Realm(
        id = realmId,
        region =
            RegionDomain(
                id = null,
                name = auctionHouseRegion,
                type = Region.fromString(auctionHouseRegion),
            ),
        name = realmName,
        category = realmCategory,
        locale = Locale.fromCompactString(realmLocale),
        timezone = realmTimeZone,
        gameBuild = GameBuildVersion.valueOf(realmGameBuild),
        slug = realmSlug,
    )

fun AdminAuctinHouseRow.toAuctionHouseDomain(rows: List<AdminAuctinHouseRow>): AuctionHouse =
    AuctionHouse(
        id = auctionHouseId,
        connectedId = auctionHouseConnectedId,
        autoUpdate = auctionHouseAutoUpdate,
        region = Region.fromString(auctionHouseRegion),
        lowestDelay = auctionHouseLowestDelay,
        avgDelay = auctionHouseAvgDelay,
        highestDelay = auctionHouseHighestDelay,
        lastAuctionPriceDeleteEvent = auctionHouseLastAuctionPriceDeleteEvent.toKotlinInstant(),
        lastHistoryDeleteEvent = auctionHouseLastHistoryDeleteEvent.toKotlinInstant(),
        lastHistoryDeleteEventDaily = auctionHouseLastAuctionPriceDeleteEvent.toKotlinInstant(),
        lastModified = auctionHouseLastModified.toKotlinInstant(),
        nextUpdate = auctionHouseNextUpdate.toKotlinInstant(),
        realms = rows.map { it.toRealmDomain() },
    )
