package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class AuctionStatsHourlyJDBCRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    @Transactional
    fun updateHourlyStats(
        hour: Int,
        connectedRealmUpdateHistoryId: Long,
    ): Int {
        require(hour in 0..23) { "Hour must be between 0 and 23" }

        val priceColumn = "price%02d".format(hour)
        val quantityColumn = "quantity%02d".format(hour)

        val sql =
            """
            INSERT INTO auction_stats_hourly
                (
                    date,
                    item_id,
                    bonus_key,
                    modifier_key,
                    pet_species_id,
                    connected_realm_id,
                    $priceColumn,
                    $quantityColumn
                )
            SELECT
                CAST(last_seen AS DATE) AS date,
                item_id,
                COALESCE(bonus_key, ''),
                COALESCE(modifier_key, ''),
                COALESCE(pet_species_id, -1),
                connected_realm_id,
                buyout AS $priceColumn,
                quantity AS $quantityColumn
            FROM auction
            WHERE update_history_id = ?
            ON DUPLICATE KEY UPDATE
                $priceColumn = VALUES($priceColumn),
                $quantityColumn = VALUES($quantityColumn)
            """.trimIndent()

        return jdbcTemplate.update(sql, connectedRealmUpdateHistoryId)
    }
}
