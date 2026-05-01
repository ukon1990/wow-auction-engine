package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
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
        val dayofMonth = date.dayOfMonth
        val minPriceColumn = "price%02d_min".format(dayofMonth)
        val maxPriceColumn = "price%02d_max".format(dayofMonth)
        val avgPriceColumn = "price%02d_avg".format(dayofMonth)
        val p25PriceColumn = "price%02d_p25".format(dayofMonth)
        val p75PriceColumn = "price%02d_p75".format(dayofMonth)
        val minQuantityColumn = "minQuantity%02d".format(dayofMonth)
        val avgQuantityColumn = "avgQuantity%02d".format(dayofMonth)
        val maxQuantityColumn = "maxQuantity%02d".format(dayofMonth)

        val sql =
            """
            INSERT INTO auction_stats_daily (
            SELECT
                item_id,
                bonus_key,
                modifier_key,
                pet_species_id,
                date,
                MIN(price) AS $minPriceColumn,
                AVG(price) AS $avgPriceColumn,
                PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY price)
                    OVER (PARTITION BY item_id, bonus_key, modifier_key, pet_species_id, date)
                    AS $p25PriceColumn,
                PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY price)
                    OVER (PARTITION BY item_id, bonus_key, modifier_key, pet_species_id, date)
                    AS $p75PriceColumn,
                MAX(price) AS $maxPriceColumn,
                MIN(quantity) AS $minQuantityColumn,
                AVG(quantity) AS $avgQuantityColumn,
                MAX(quantity) AS $maxQuantityColumn
            FROM v_auction_house_prices
            WHERE
                connected_realm_id = $connectedRealmId
                AND date = $date
            GROUP BY item_id, bonus_key, modifier_key, pet_species_id, date
            )
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
        return jdbcTemplate.update(sql)
    }
}
