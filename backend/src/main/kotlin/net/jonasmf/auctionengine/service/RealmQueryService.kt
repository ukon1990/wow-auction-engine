package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.generated.model.AuctionHouseStatus
import net.jonasmf.auctionengine.generated.model.RealmDetail
import net.jonasmf.auctionengine.repository.rds.RealmCatalogJdbcRepository
import net.jonasmf.auctionengine.repository.rds.RealmCatalogRow
import net.jonasmf.auctionengine.repository.rds.RealmDetailRow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import net.jonasmf.auctionengine.generated.model.Realm as RealmDto

data class RealmCatalogResult(
    val realms: List<RealmDto>,
    val queryDuration: Duration,
    val mapSortDuration: Duration,
) {
    fun serverTimingHeader(totalDuration: Duration): String =
        buildString {
            append("query;dur=")
            append(queryDuration.inWholeMilliseconds)
            append(", map;dur=")
            append(mapSortDuration.inWholeMilliseconds)
            append(", total;dur=")
            append(totalDuration.inWholeMilliseconds)
        }
}

@Service
class RealmQueryService(
    private val realmCatalogJdbcRepository: RealmCatalogJdbcRepository,
) {
    private val logger = LoggerFactory.getLogger(RealmQueryService::class.java)

    fun listAllRealms(): RealmCatalogResult {
        val queryStart = System.nanoTime()
        val rows = realmCatalogJdbcRepository.findRealmCatalogRows()
        val queryDuration = (System.nanoTime() - queryStart).toDuration(DurationUnit.NANOSECONDS)

        val mapStart = System.nanoTime()
        val realms =
            rows
                .asSequence()
                .map { it.toDto() }
                .sortedWith(compareBy({ it.region.value }, { it.name }))
                .toList()
        val mapSortDuration = (System.nanoTime() - mapStart).toDuration(DurationUnit.NANOSECONDS)
        val totalDuration = queryDuration + mapSortDuration

        logger.info(
            "Loaded {} realms in {}ms (query={}ms mapSort={}ms)",
            realms.size,
            totalDuration.inWholeMilliseconds,
            queryDuration.inWholeMilliseconds,
            mapSortDuration.inWholeMilliseconds,
        )

        return RealmCatalogResult(
            realms = realms,
            queryDuration = queryDuration,
            mapSortDuration = mapSortDuration,
        )
    }

    fun getRealmDetail(
        regionCode: String,
        slug: String,
    ): RealmDetail? {
        val region = runCatching { Region.fromString(regionCode) }.getOrNull() ?: return null
        val row =
            realmCatalogJdbcRepository.findRealmDetailRow(region, slug)
                ?: return null

        return RealmDetail(
            realm = row.toRealmDto(),
            auctionHouse = row.toAuctionHouseStatus(),
            commodity = row.toCommodityAuctionHouseStatus(),
        )
    }

    private fun RealmCatalogRow.toDto(): RealmDto =
        RealmDto(
            region = regionId.toRegion().toDtoEnum(),
            name = name,
            category = category,
            slug = slug,
            locale = locale,
            timezone = timezone,
        )

    private fun RealmDetailRow.toRealmDto(): RealmDto =
        RealmDto(
            region = regionId.toRegion().toDtoEnum(),
            name = name,
            category = category,
            slug = slug,
            locale = locale,
            timezone = timezone,
        )

    private fun RealmDetailRow.toAuctionHouseStatus(): AuctionHouseStatus =
        AuctionHouseStatus(
            connectedRealmId = connectedRealmId,
            lastDailyPriceUpdate = lastDailyPriceUpdate.toOffsetDateTime(),
            lastModified = lastModified.toOffsetDateTime(),
            nextUpdate = nextUpdate.toOffsetDateTime(),
        )

    private fun RealmDetailRow.toCommodityAuctionHouseStatus(): AuctionHouseStatus =
        AuctionHouseStatus(
            connectedRealmId = commodityConnectedRealmId,
            lastDailyPriceUpdate = commodityLastDailyPriceUpdate.toOffsetDateTime(),
            lastModified = commodityLastModified.toOffsetDateTime(),
            nextUpdate = commodityNextUpdate.toOffsetDateTime(),
        )

    private fun Region.toDtoEnum(): RealmDto.Region =
        when (this) {
            Region.NorthAmerica -> RealmDto.Region.US
            Region.Europe -> RealmDto.Region.EU
            Region.Korea -> RealmDto.Region.KR
            Region.Taiwan -> RealmDto.Region.TW
        }

    private fun Int.toRegion(): Region =
        when (this) {
            1 -> Region.NorthAmerica
            2 -> Region.Europe
            3 -> Region.Korea
            4 -> Region.Taiwan
            else -> error("Unknown region id: $this")
        }

    private fun Instant?.toOffsetDateTime(): OffsetDateTime? = this?.atOffset(ZoneOffset.UTC)
}
