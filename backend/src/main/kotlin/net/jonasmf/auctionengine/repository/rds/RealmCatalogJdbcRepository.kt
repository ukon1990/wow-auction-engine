package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.service.CommodityRealms
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import net.jonasmf.auctionengine.constant.Locale as WowLocale

data class RealmCatalogRow(
    val regionId: Int,
    val name: String,
    val category: String,
    val slug: String,
    val locale: String,
    val timezone: String,
)

data class RealmDetailRow(
    val connectedRealmId: Int,
    val regionId: Int,
    val name: String,
    val category: String,
    val slug: String,
    val locale: String,
    val timezone: String,
    val lastDailyPriceUpdate: Instant?,
    val lastModified: Instant?,
    val nextUpdate: Instant?,
    val commodityConnectedRealmId: Int,
    val commodityLastDailyPriceUpdate: Instant?,
    val commodityLastModified: Instant?,
    val commodityNextUpdate: Instant?,
)

@Repository
class RealmCatalogJdbcRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    @Cacheable(cacheNames = ["realmCatalog"])
    fun findRealmCatalogRows(): List<RealmCatalogRow> =
        jdbcTemplate.query(
            """
            SELECT
                r.region_id AS regionId,
                r.name,
                r.category,
                r.slug,
                r.locale,
                r.timezone
            FROM connected_realm cr
            JOIN connected_realm_realms crr ON crr.connected_realm_id = cr.id
            JOIN realm r ON r.id = crr.realms_id
            WHERE cr.id >= 0
            ORDER BY r.region_id, r.name
            """.trimIndent(),
            { rs, rowNum -> mapRealmCatalogRow(rs, rowNum) },
        )

    fun findRealmDetailRow(
        region: Region,
        slug: String,
    ): RealmDetailRow? {
        val commodityId = CommodityRealms.idFor(region)
        return jdbcTemplate
            .query(
                """
                SELECT
                    cr.id AS connectedRealmId,
                    r.region_id AS regionId,
                    r.name,
                    r.category,
                    r.slug,
                    r.locale,
                    r.timezone,
                    ah.last_daily_price_update AS lastDailyPriceUpdate,
                    ah.last_modified AS lastModified,
                    ah.next_update AS nextUpdate,
                    commodity_cr.id AS commodityConnectedRealmId,
                    commodity_ah.last_daily_price_update AS commodityLastDailyPriceUpdate,
                    commodity_ah.last_modified AS commodityLastModified,
                    commodity_ah.next_update AS commodityNextUpdate
                FROM realm r
                JOIN connected_realm_realms crr ON crr.realms_id = r.id
                JOIN connected_realm cr ON cr.id = crr.connected_realm_id
                JOIN auction_house ah ON ah.id = cr.auction_house_id
                JOIN connected_realm commodity_cr ON commodity_cr.id = ?
                JOIN auction_house commodity_ah ON commodity_ah.id = commodity_cr.auction_house_id
                WHERE r.region_id = ?
                  AND r.slug = ?
                LIMIT 1
                """.trimIndent(),
                { rs, _ -> rs.toRealmDetailRow() },
                commodityId,
                region.toDbId(),
                slug.lowercase(),
            ).firstOrNull()
    }

    private fun mapRealmCatalogRow(
        rs: ResultSet,
        rowNum: Int,
    ): RealmCatalogRow =
        RealmCatalogRow(
            regionId = rs.getInt("regionId"),
            name = rs.getString("name"),
            category = rs.getString("category"),
            slug = rs.getString("slug"),
            locale = rs.getLocaleValue("locale"),
            timezone = rs.getString("timezone"),
        )

    private fun ResultSet.toRealmDetailRow(): RealmDetailRow =
        RealmDetailRow(
            connectedRealmId = getInt("connectedRealmId"),
            regionId = getInt("regionId"),
            name = getString("name"),
            category = getString("category"),
            slug = getString("slug"),
            locale = getLocaleValue("locale"),
            timezone = getString("timezone"),
            lastDailyPriceUpdate = getInstant("lastDailyPriceUpdate"),
            lastModified = getInstant("lastModified"),
            nextUpdate = getInstant("nextUpdate"),
            commodityConnectedRealmId = getInt("commodityConnectedRealmId"),
            commodityLastDailyPriceUpdate = getInstant("commodityLastDailyPriceUpdate"),
            commodityLastModified = getInstant("commodityLastModified"),
            commodityNextUpdate = getInstant("commodityNextUpdate"),
        )

    /**
     * `auction_house` stores `DATETIME` without timezone; treat the wall clock as UTC so snapshot
     * hours match `auction_stats_hourly` columns regardless of JVM default zone.
     */
    private fun ResultSet.getInstant(column: String): Instant? {
        val str = getString(column)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = if ('T' in str) str else str.replace(' ', 'T')
        val ldt = LocalDateTime.parse(normalized)
        return ldt.atOffset(ZoneOffset.UTC).toInstant()
    }

    private fun ResultSet.getLocaleValue(column: String): String = WowLocale.entries[getInt(column)].value

    private fun Region.toDbId(): Int =
        when (this) {
            Region.NorthAmerica -> 1
            Region.Europe -> 2
            Region.Korea -> 3
            Region.Taiwan -> 4
        }
}
