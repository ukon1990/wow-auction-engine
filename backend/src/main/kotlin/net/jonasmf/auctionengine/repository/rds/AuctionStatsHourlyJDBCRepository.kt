package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate

data class HourlyStatsUpsertRow(
    val connectedRealmId: Int,
    val itemId: Int,
    val date: LocalDate,
    val petSpeciesId: Int?,
    val modifierKey: String,
    val bonusKey: String,
    val price: Long?,
    val quantity: Int?,
)

@Repository
class AuctionStatsHourlyJDBCRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val chunkSize = 5_000

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
                CAST(last_seen AS DATE) AS date,
                item_id,
                bonus_key,
                modifier_key,
                pet_species_id,
                connected_realm_id,
                buyout AS $priceColumn,
                quantity AS $quantityColumn
            FROM auction
            WHERE update_history_id $connectedRealmUpdateHistoryId
            ON DUPLICATE KEY UPDATE
                $priceColumn = VALUES($priceColumn),
                $quantityColumn = VALUES($quantityColumn)
            """.trimIndent()

        return jdbcTemplate.update(sql)
    }
}
