package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.OffsetDateTime

class AuctionCleanupJdbcRepositoryTest : IntegrationTestBase() {
    @Autowired
    lateinit var repository: AuctionCleanupJdbcRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `hourly cleanup deletes only rows older than ttl for selected connected realm`() {
        insertHourly(1, LocalDate.of(2026, 1, 1))
        insertHourly(1, LocalDate.of(2026, 1, 2))
        insertHourly(1, LocalDate.of(2026, 1, 3))
        insertHourly(2, LocalDate.of(2026, 1, 1))

        assertEquals(2, repository.countHourlyStats(1, LocalDate.of(2026, 1, 3)))
        assertEquals(1, repository.deleteHourlyStats(1, LocalDate.of(2026, 1, 3), 1))

        assertEquals(2, countRows("auction_stats_hourly", 1))
        assertEquals(1, countRows("auction_stats_hourly", 2))
    }

    @Test
    fun `daily cleanup deletes only rows older than ttl for selected connected realm`() {
        insertDaily(1, LocalDate.of(2026, 1, 1))
        insertDaily(1, LocalDate.of(2026, 1, 3))
        insertDaily(2, LocalDate.of(2026, 1, 1))

        assertEquals(1, repository.countDailyStats(1, LocalDate.of(2026, 1, 3)))
        assertEquals(1, repository.deleteDailyStats(1, LocalDate.of(2026, 1, 3), 50))

        assertEquals(1, countRows("auction_stats_daily", 1))
        assertEquals(1, countRows("auction_stats_daily", 2))
    }

    @Test
    fun `auction price cleanup keeps current rows and other connected realms`() {
        insertAuctionDependencies(1)
        insertAuctionDependencies(2)
        insertAuction(1, "auction-1")
        insertAuction(2, "auction-2")
        insertAuctionPrice(1, "auction-1", OffsetDateTime.parse("2026-01-01T00:00:00Z"))
        insertAuctionPrice(2, "auction-1", OffsetDateTime.parse("2026-01-03T00:00:00Z"))
        insertAuctionPrice(3, "auction-2", OffsetDateTime.parse("2026-01-01T00:00:00Z"))

        assertEquals(1, repository.countAuctionPrices(1, OffsetDateTime.parse("2026-01-02T00:00:00Z")))
        assertEquals(1, repository.deleteAuctionPrices(1, OffsetDateTime.parse("2026-01-02T00:00:00Z"), 50))

        assertEquals(1, countAuctionPriceRows(1))
        assertEquals(1, countAuctionPriceRows(2))
    }

    private fun insertHourly(
        connectedRealmId: Int,
        date: LocalDate,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly
                (connected_realm_id, date, item_id, pet_species_id, modifier_key, bonus_key, price00, quantity00)
            VALUES (?, ?, ?, -1, '', '', 100, 1)
            """.trimIndent(),
            connectedRealmId,
            Date.valueOf(date),
            date.dayOfYear,
        )
    }

    private fun insertDaily(
        connectedRealmId: Int,
        date: LocalDate,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_daily
                (connected_realm_id, date, item_id, pet_species_id, modifier_key, bonus_key, min01, avg01, max01)
            VALUES (?, ?, ?, -1, '', '', 100, 100, 100)
            """.trimIndent(),
            connectedRealmId,
            Date.valueOf(date),
            date.dayOfYear,
        )
    }

    private fun insertAuctionDependencies(connectedRealmId: Int) {
        jdbcTemplate.update(
            """
            INSERT INTO auction_house
                (id, auto_update, avg_delay, connected_id, game_build, highest_delay, lowest_delay, update_attempts)
            VALUES (?, false, 0, ?, 0, 0, 0, 0)
            """.trimIndent(),
            connectedRealmId,
            connectedRealmId,
        )
        jdbcTemplate.update(
            "INSERT INTO connected_realm (id, auction_house_id) VALUES (?, ?)",
            connectedRealmId,
            connectedRealmId,
        )
    }

    private fun insertAuction(
        connectedRealmId: Int,
        auctionId: String,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO auction
                (id, connected_realm_id, item_id, quantity, modifier_key, bonus_key)
            VALUES (?, ?, 1, 1, '', '')
            """.trimIndent(),
            auctionId,
            connectedRealmId,
        )
    }

    private fun insertAuctionPrice(
        id: Long,
        auctionId: String,
        lastModified: OffsetDateTime,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO auction_price
                (id, auction_id, buyout, bid, quantity, last_modified)
            VALUES (?, ?, 100, 100, 1, ?)
            """.trimIndent(),
            id,
            auctionId,
            Timestamp.from(lastModified.toInstant()),
        )
    }

    private fun countRows(
        table: String,
        connectedRealmId: Int,
    ): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM $table WHERE connected_realm_id = ?",
            Int::class.java,
            connectedRealmId,
        ) ?: 0

    private fun countAuctionPriceRows(connectedRealmId: Int): Int =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auction_price ap
            JOIN auction a ON a.id = ap.auction_id
            WHERE a.connected_realm_id = ?
            """.trimIndent(),
            Int::class.java,
            connectedRealmId,
        ) ?: 0
}
