package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.OffsetDateTime

enum class AuctionCleanupTarget(
    val tableName: String,
) {
    HOURLY_STATS("auction_stats_hourly"),
    DAILY_STATS("auction_stats_daily"),
    AUCTION_PRICES("auction_price"),
}

@Repository
class AuctionCleanupJdbcRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun countHourlyStats(
        connectedRealmId: Int,
        deleteBefore: LocalDate,
    ): Int =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auction_stats_hourly
            WHERE connected_realm_id = ?
              AND date < ?
            """.trimIndent(),
            Int::class.java,
            connectedRealmId,
            Date.valueOf(deleteBefore),
        ) ?: 0

    fun deleteHourlyStats(
        connectedRealmId: Int,
        deleteBefore: LocalDate,
        batchSize: Int,
    ): Int =
        jdbcTemplate.update(
            """
            DELETE FROM auction_stats_hourly
            WHERE connected_realm_id = ?
              AND date < ?
            ORDER BY date
            LIMIT ?
            """.trimIndent(),
            connectedRealmId,
            Date.valueOf(deleteBefore),
            batchSize,
        )

    fun countDailyStats(
        connectedRealmId: Int,
        deleteBefore: LocalDate,
    ): Int =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auction_stats_daily
            WHERE connected_realm_id = ?
              AND date < ?
            """.trimIndent(),
            Int::class.java,
            connectedRealmId,
            Date.valueOf(deleteBefore),
        ) ?: 0

    fun deleteDailyStats(
        connectedRealmId: Int,
        deleteBefore: LocalDate,
        batchSize: Int,
    ): Int =
        jdbcTemplate.update(
            """
            DELETE FROM auction_stats_daily
            WHERE connected_realm_id = ?
              AND date < ?
            ORDER BY date
            LIMIT ?
            """.trimIndent(),
            connectedRealmId,
            Date.valueOf(deleteBefore),
            batchSize,
        )

    fun countAuctionPrices(
        connectedRealmId: Int,
        deleteBefore: OffsetDateTime,
    ): Int =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auction_price ap
            JOIN auction a ON a.id = ap.auction_id
            WHERE a.connected_realm_id = ?
              AND ap.last_modified < ?
            """.trimIndent(),
            Int::class.java,
            connectedRealmId,
            Timestamp.from(deleteBefore.toInstant()),
        ) ?: 0

    fun deleteAuctionPrices(
        connectedRealmId: Int,
        deleteBefore: OffsetDateTime,
        batchSize: Int,
    ): Int =
        jdbcTemplate.update(
            """
            DELETE FROM auction_price
            WHERE id IN (
                SELECT id
                FROM (
                    SELECT ap.id
                    FROM auction_price ap
                    JOIN auction a ON a.id = ap.auction_id
                    WHERE a.connected_realm_id = ?
                      AND ap.last_modified < ?
                    ORDER BY ap.last_modified
                    LIMIT ?
                ) cleanup_batch
            )
            """.trimIndent(),
            connectedRealmId,
            Timestamp.from(deleteBefore.toInstant()),
            batchSize,
        )

    fun optimize(target: AuctionCleanupTarget) {
        jdbcTemplate.execute("OPTIMIZE TABLE ${target.tableName}")
    }
}
