package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForList
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.Instant
import java.time.LocalDate

@Repository
class DeletedAuctionCleanupRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun findNextHourlyCleanupRealms(): List<Int?> =
        jdbcTemplate
            .queryForList<Int>(
                """
                SELECT connected_id
                FROM auction_house
                WHERE last_history_delete_event IS NULL OR last_history_delete_event < DATE_ADD(NOW(), INTERVAL -1 DAY)
                GROUP BY last_history_delete_event
                ORDER BY last_history_delete_event
                """.trimIndent(),
            )

    fun findNextDailyCleanupRealms(): List<Int?> =
        jdbcTemplate
            .queryForList<Int>(
                """
                SELECT connected_id
                FROM auction_house
                WHERE last_history_delete_event_daily IS NULL OR last_history_delete_event_daily < DATE_ADD(NOW(), INTERVAL -1 DAY)
                GROUP BY last_history_delete_event_daily
                ORDER BY last_history_delete_event_daily
                """.trimIndent(),
            )

    fun findNextPriceCleanupRealms(): List<Int?> =
        jdbcTemplate
            .queryForList<Int>(
                """
                SELECT connected_id
                FROM auction_house
                WHERE last_auction_price_delete_event IS NULL OR last_auction_price_delete_event < DATE_ADD(NOW(), INTERVAL -1 DAY)
                GROUP BY last_auction_price_delete_event
                ORDER BY last_auction_price_delete_event
                """.trimIndent(),
            )

    @Transactional
    fun deleteHourlyBeforeOrEqualToCutoff(
        connectedRealmId: Int,
        cutoff: Instant,
    ): Int =
        jdbcTemplate.update(
            """
            DELETE FROM auction_stats_hourly
            WHERE connected_realm_id = ?
              AND date < ?
            """.trimIndent(),
            connectedRealmId,
            cutoff,
        )

    @Transactional
    fun deleteDailyBeforeOrEqualToCutoff(
        connectedRealmId: Int,
        cutoff: Instant,
    ): Int =
        jdbcTemplate.update(
            """
            DELETE FROM auction_stats_daily
            WHERE connected_realm_id = ?
              AND date < ?
            """.trimIndent(),
            connectedRealmId,
            cutoff,
        )

    @Transactional
    fun deletePriceForRealmBeforeOrEqualToCutoff(
        connectedRealmId: Int,
        cutoff: Instant,
    ): Int {
        val logIds =
            jdbcTemplate.queryForList<Int>(
                """
                SELECT id
                FROM connected_realm_update_history
                WHERE connected_realm_id = ?
                    AND last_modified <= ?
                """.trimIndent(),
                connectedRealmId,
                cutoff,
            )
        val deletedCount =
            jdbcTemplate.update(
                """
                DELETE FROM auction_price
                WHERE update_history_id IN ?
                """.trimIndent(),
                logIds,
            )
        return deletedCount
    }

    fun optimizeTable(tableName: String) {
        require(tableName in OPTIMIZABLE_TABLES) { "Unsupported cleanup table: $tableName" }
        jdbcTemplate.execute("OPTIMIZE TABLE $tableName")
    }

    private companion object {
        val OPTIMIZABLE_TABLES = setOf("auction_stats_hourly", "auction_stats_daily", "auction_price")
    }
}
