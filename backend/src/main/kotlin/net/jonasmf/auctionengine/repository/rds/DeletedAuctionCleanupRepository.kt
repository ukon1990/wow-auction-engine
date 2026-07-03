package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

@Repository
class DeletedAuctionCleanupRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun findNextHourlyCleanupRealm(cutoff: LocalDate): Int? =
        jdbcTemplate.queryForList(
            """
            SELECT connected_realm_id
            FROM auction_stats_hourly
            WHERE date < ?
            GROUP BY connected_realm_id
            ORDER BY connected_realm_id
            LIMIT 1
            """.trimIndent(),
            Int::class.java,
            Date.valueOf(cutoff),
        ).firstOrNull()

    fun findNextDailyCleanupRealm(cutoff: LocalDate): Int? =
        jdbcTemplate.queryForList(
            """
            SELECT connected_realm_id
            FROM auction_stats_daily
            WHERE date < ?
            GROUP BY connected_realm_id
            ORDER BY connected_realm_id
            LIMIT 1
            """.trimIndent(),
            Int::class.java,
            Date.valueOf(cutoff),
        ).firstOrNull()

    fun findNextPriceCleanupRealm(cutoff: Instant): Int? =
        jdbcTemplate.queryForList(
            """
            SELECT a.connected_realm_id
            FROM auction_price ap
            INNER JOIN auction a ON a.id = ap.auction_id
            WHERE ap.last_modified < ?
            GROUP BY a.connected_realm_id
            ORDER BY a.connected_realm_id
            LIMIT 1
            """.trimIndent(),
            Int::class.java,
            Timestamp.from(cutoff),
        ).firstOrNull()

    fun countHourlyCleanupCandidates(
        connectedRealmId: Int,
        cutoff: LocalDate,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auction_stats_hourly
            WHERE connected_realm_id = ?
              AND date < ?
            """.trimIndent(),
            Long::class.java,
            connectedRealmId,
            Date.valueOf(cutoff),
        ) ?: 0L

    fun countDailyCleanupCandidates(
        connectedRealmId: Int,
        cutoff: LocalDate,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auction_stats_daily
            WHERE connected_realm_id = ?
              AND date < ?
            """.trimIndent(),
            Long::class.java,
            connectedRealmId,
            Date.valueOf(cutoff),
        ) ?: 0L

    fun countPriceCleanupCandidates(
        connectedRealmId: Int,
        cutoff: Instant,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auction_price ap
            INNER JOIN auction a ON a.id = ap.auction_id
            WHERE a.connected_realm_id = ?
              AND ap.last_modified < ?
            """.trimIndent(),
            Long::class.java,
            connectedRealmId,
            Timestamp.from(cutoff),
        ) ?: 0L

    @Transactional
    fun deleteHourlyBatch(
        connectedRealmId: Int,
        cutoff: LocalDate,
        batchSize: Int,
    ): Int =
        jdbcTemplate.update(
            """
            DELETE FROM auction_stats_hourly
            WHERE connected_realm_id = ?
              AND date < ?
            LIMIT ?
            """.trimIndent(),
            connectedRealmId,
            Date.valueOf(cutoff),
            batchSize,
        )

    @Transactional
    fun deleteDailyBatch(
        connectedRealmId: Int,
        cutoff: LocalDate,
        batchSize: Int,
    ): Int =
        jdbcTemplate.update(
            """
            DELETE FROM auction_stats_daily
            WHERE connected_realm_id = ?
              AND date < ?
            LIMIT ?
            """.trimIndent(),
            connectedRealmId,
            Date.valueOf(cutoff),
            batchSize,
        )

    @Transactional
    fun deletePriceBatch(
        connectedRealmId: Int,
        cutoff: Instant,
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
                    INNER JOIN auction a ON a.id = ap.auction_id
                    WHERE a.connected_realm_id = ?
                      AND ap.last_modified < ?
                    LIMIT ?
                ) batch
            )
            """.trimIndent(),
            connectedRealmId,
            Timestamp.from(cutoff),
            batchSize,
        )

    fun optimizeTable(tableName: String) {
        require(tableName in OPTIMIZABLE_TABLES) { "Unsupported cleanup table: $tableName" }
        jdbcTemplate.execute("OPTIMIZE TABLE $tableName")
    }

    private companion object {
        val OPTIMIZABLE_TABLES = setOf("auction_stats_hourly", "auction_stats_daily", "auction_price")
    }
}
