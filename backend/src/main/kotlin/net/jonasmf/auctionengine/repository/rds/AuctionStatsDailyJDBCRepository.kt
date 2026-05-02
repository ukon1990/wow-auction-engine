package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate

@Repository
class AuctionStatsDailyJDBCRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    @Transactional
    fun upsertDailyPriceStatistics(
        connectedRealmId: Int,
        date: LocalDate,
    ): Int {
        val dayOfMonth = date.dayOfMonth
        val minPriceColumn = "min%02d".format(dayOfMonth)
        val avgPriceColumn = "avg%02d".format(dayOfMonth)
        val p25PriceColumn = "price_percentile_25_%02d".format(dayOfMonth)
        val p75PriceColumn = "price_percentile_75_%02d".format(dayOfMonth)
        val maxPriceColumn = "max%02d".format(dayOfMonth)
        val minQuantityColumn = "min_quantity%02d".format(dayOfMonth)
        val avgQuantityColumn = "avg_quantity%02d".format(dayOfMonth)
        val maxQuantityColumn = "max_quantity%02d".format(dayOfMonth)

        val sql =
            """
            INSERT INTO auction_stats_daily (
                connected_realm_id,
                item_id,
                bonus_key,
                modifier_key,
                pet_species_id,
                date,
                $minPriceColumn,
                $avgPriceColumn,
                $p25PriceColumn,
                $p75PriceColumn,
                $maxPriceColumn,
                $minQuantityColumn,
                $avgQuantityColumn,
                $maxQuantityColumn
            )
            WITH priced AS (
                SELECT
                    connected_realm_id,
                    item_id,
                    bonus_key,
                    modifier_key,
                    pet_species_id,
                    date,
                    price,
                    quantity,
                    PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY price)
                        OVER (PARTITION BY connected_realm_id, item_id, bonus_key, modifier_key, pet_species_id, date)
                        AS p25_price,
                    PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY price)
                        OVER (PARTITION BY connected_realm_id, item_id, bonus_key, modifier_key, pet_species_id, date)
                        AS p75_price
                FROM v_auction_house_prices
                WHERE connected_realm_id = ?
                  AND date = ?
            )
            SELECT
                connected_realm_id,
                item_id,
                bonus_key,
                modifier_key,
                pet_species_id,
                date,
                MIN(price) AS $minPriceColumn,
                AVG(price) AS $avgPriceColumn,
                MIN(p25_price) AS $p25PriceColumn,
                MIN(p75_price) AS $p75PriceColumn,
                MAX(price) AS $maxPriceColumn,
                MIN(quantity) AS $minQuantityColumn,
                AVG(quantity) AS $avgQuantityColumn,
                MAX(quantity) AS $maxQuantityColumn
            FROM priced
            GROUP BY connected_realm_id, item_id, bonus_key, modifier_key, pet_species_id, date
            ON DUPLICATE KEY UPDATE
                $minPriceColumn = VALUES($minPriceColumn),
                $avgPriceColumn = VALUES($avgPriceColumn),
                $p25PriceColumn = VALUES($p25PriceColumn),
                $p75PriceColumn = VALUES($p75PriceColumn),
                $maxPriceColumn = VALUES($maxPriceColumn),
                $minQuantityColumn = VALUES($minQuantityColumn),
                $avgQuantityColumn = VALUES($avgQuantityColumn),
                $maxQuantityColumn = VALUES($maxQuantityColumn)
            """.trimIndent()
        return jdbcTemplate.update(sql, connectedRealmId, Date.valueOf(date))
    }
}
