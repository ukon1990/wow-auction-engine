package net.jonasmf.auctionengine.dbo.rds.admin

import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.realm.AuctionHouse
import net.jonasmf.auctionengine.domain.realm.Realm
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.toKotlinInstant
import net.jonasmf.auctionengine.domain.realm.Region as RegionDomain

data class AdminAuctinHouseRow(
    val pageTotalItems: Long,
    val auctionHouseId: Int? = null,
    val auctionHouseConnectedId: Int,
    val auctionHouseAutoUpdate: Boolean,
    val auctionHouseRegion: String,

    val auctionHouseLowestDelay: Long,
    val auctionHouseAvgDelay: Long,
    val auctionHouseHighestDelay: Long,

    val auctionHouseLastAuctionPriceDeleteEvent: LocalDateTime? = null,
    val auctionHouseLastHistoryDeleteEvent: LocalDateTime? = null,
    val auctionHouseLastHistoryDeleteEventDaily: LocalDateTime? = null,

    val auctionHouseLastModified: LocalDateTime? = null,
    val auctionHouseNextUpdate: LocalDateTime? = null,

    val realmId: Int,
    val realmSlug: String,
    val realmName: String,
    val realmLocale: Int,
    val realmCategory: String,
    val realmGameBuild: Int,
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
        locale = Locale.fromOrdinalInt(realmLocale),
        timezone = realmTimeZone,
        gameBuild = GameBuildVersion.RETAIL, // TODO:Create helper .valueOf(realmGameBuild),
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
        lastAuctionPriceDeleteEvent = kotlinTimeFromLocalDateTime(auctionHouseLastAuctionPriceDeleteEvent),
        lastHistoryDeleteEvent = kotlinTimeFromLocalDateTime(auctionHouseLastHistoryDeleteEvent),
        lastHistoryDeleteEventDaily = kotlinTimeFromLocalDateTime(auctionHouseLastAuctionPriceDeleteEvent),
        lastModified = kotlinTimeFromLocalDateTime(auctionHouseLastModified),
        nextUpdate = kotlinTimeFromLocalDateTime(auctionHouseNextUpdate),
        realms = rows.map { it.toRealmDomain() },
    )

private fun kotlinTimeFromLocalDateTime(time: LocalDateTime?): kotlin.time.Instant? =
    time?.toInstant(ZoneOffset.UTC)?.toKotlinInstant()
