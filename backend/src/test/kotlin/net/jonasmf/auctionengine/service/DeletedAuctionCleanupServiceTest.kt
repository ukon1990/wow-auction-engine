package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.repository.rds.AuctionHouseRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.sql.Date
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class DeletedAuctionCleanupServiceTest : IntegrationTestBase() {
    @Autowired
    lateinit var service: DeletedAuctionCleanupService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var auctionHouseRepository: AuctionHouseRepository

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerCleanupProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.scheduling.deleted-auction-cleanup.hourly-retention") { "P7D" }
            registry.add("app.scheduling.deleted-auction-cleanup.daily-retention") { "P30D" }
            registry.add("app.scheduling.deleted-auction-cleanup.price-retention") { "P3D" }
        }
    }

    @Nested
    inner class HourlyStatsCleanup {
        @Test
        fun `deletes old rows for eligible realm`() {
            val eligibleRealmId = 1
            seedConnectedRealm(eligibleRealmId, lastHistoryDeleteEvent = daysAgo(2))
            insertHourly(eligibleRealmId, LocalDate.now().minusDays(10))
            insertHourly(eligibleRealmId, LocalDate.now().minusDays(1))

            service.cleanupHourlyStats()

            assertThat(countHourlyRows(eligibleRealmId)).isEqualTo(1)
        }

        @Test
        fun `skips ineligible realm`() {
            val ineligibleRealmId = 2
            seedConnectedRealm(ineligibleRealmId, lastHistoryDeleteEvent = hoursAgo(1))
            insertHourly(ineligibleRealmId, LocalDate.now().minusDays(10))
            insertHourly(ineligibleRealmId, LocalDate.now().minusDays(1))

            service.cleanupHourlyStats()

            assertThat(countHourlyRows(ineligibleRealmId)).isEqualTo(2)
        }

        @Test
        fun `isolates connected realms`() {
            seedConnectedRealm(1, lastHistoryDeleteEvent = daysAgo(2))
            seedConnectedRealm(2, lastHistoryDeleteEvent = daysAgo(2))
            insertHourly(1, LocalDate.now().minusDays(10))
            insertHourly(1, LocalDate.now().minusDays(1))
            insertHourly(2, LocalDate.now().minusDays(10))
            insertHourly(2, LocalDate.now().minusDays(1))

            service.cleanupHourlyStats()

            assertThat(countHourlyRows(1)).isEqualTo(1)
            assertThat(countHourlyRows(2)).isEqualTo(1)
        }

        @Test
        fun `updates marker after successful cleanup`() {
            val eligibleRealmId = 1
            seedConnectedRealm(eligibleRealmId, lastHistoryDeleteEvent = daysAgo(2))
            insertHourly(eligibleRealmId, LocalDate.now().minusDays(10))

            service.cleanupHourlyStats()

            val markerAfter = readHistoryDeleteMarker(eligibleRealmId)
            assertThat(markerAfter).isNotNull()
            assertThat(markerAfter).isCloseTo(Instant.now().minus(Duration.ofDays(7)), within(1, ChronoUnit.MINUTES))
        }

        @Test
        fun `end-of-run with multiple due realms completes successfully`() {
            seedConnectedRealm(1, lastHistoryDeleteEvent = daysAgo(2))
            seedConnectedRealm(2, lastHistoryDeleteEvent = daysAgo(2))
            insertHourly(1, LocalDate.now().minusDays(10))
            insertHourly(2, LocalDate.now().minusDays(10))

            val results = service.cleanupHourlyStats()

            assertThat(results).hasSize(2)
            assertThat(results).allMatch { it.deletedRows > 0 }
            assertThat(countHourlyRows(1)).isZero()
            assertThat(countHourlyRows(2)).isZero()
        }
    }

    @Nested
    inner class DailyStatsCleanup {
        @Test
        fun `deletes old rows for eligible realm`() {
            val eligibleRealmId = 1
            seedConnectedRealm(eligibleRealmId, lastHistoryDeleteEventDaily = daysAgo(2))
            insertDaily(eligibleRealmId, LocalDate.now().minusDays(40))
            insertDaily(eligibleRealmId, LocalDate.now().minusDays(1))

            service.cleanupDailyStats()

            assertThat(countDailyRows(eligibleRealmId)).isEqualTo(1)
        }

        @Test
        fun `skips ineligible realm`() {
            val ineligibleRealmId = 2
            seedConnectedRealm(ineligibleRealmId, lastHistoryDeleteEventDaily = hoursAgo(1))
            insertDaily(ineligibleRealmId, LocalDate.now().minusDays(40))
            insertDaily(ineligibleRealmId, LocalDate.now().minusDays(1))

            service.cleanupDailyStats()

            assertThat(countDailyRows(ineligibleRealmId)).isEqualTo(2)
        }

        @Test
        fun `isolates connected realms`() {
            seedConnectedRealm(1, lastHistoryDeleteEventDaily = daysAgo(2))
            seedConnectedRealm(2, lastHistoryDeleteEventDaily = daysAgo(2))
            insertDaily(1, LocalDate.now().minusDays(40))
            insertDaily(1, LocalDate.now().minusDays(1))
            insertDaily(2, LocalDate.now().minusDays(40))
            insertDaily(2, LocalDate.now().minusDays(1))

            service.cleanupDailyStats()

            assertThat(countDailyRows(1)).isEqualTo(1)
            assertThat(countDailyRows(2)).isEqualTo(1)
        }

        @Test
        fun `updates marker after successful cleanup`() {
            val eligibleRealmId = 1
            seedConnectedRealm(eligibleRealmId, lastHistoryDeleteEventDaily = daysAgo(2))
            insertDaily(eligibleRealmId, LocalDate.now().minusDays(40))

            service.cleanupDailyStats()

            val markerAfter = readDailyHistoryDeleteMarker(eligibleRealmId)
            assertThat(markerAfter).isNotNull()
            assertThat(markerAfter).isCloseTo(Instant.now().minus(Duration.ofDays(30)), within(1, ChronoUnit.MINUTES))
        }
    }

    @Nested
    inner class PriceHistoryCleanup {
        @Test
        fun `deletes prices via old update history`() {
            val eligibleRealmId = 1
            seedConnectedRealm(eligibleRealmId, lastAuctionPriceDeleteEvent = daysAgo(2))
            val oldHistoryId = insertUpdateHistory(eligibleRealmId, daysAgo(5))
            val recentHistoryId = insertUpdateHistory(eligibleRealmId, hoursAgo(12))
            insertAuction(eligibleRealmId, "auction-old", oldHistoryId)
            insertAuction(eligibleRealmId, "auction-recent", recentHistoryId)
            insertAuctionPrice(1, "auction-old", daysAgo(5), oldHistoryId)
            insertAuctionPrice(2, "auction-recent", hoursAgo(12), recentHistoryId)

            service.cleanupPriceHistory()

            assertThat(countPriceRows(eligibleRealmId)).isEqualTo(1)
        }

        @Test
        fun `skips ineligible realm`() {
            val ineligibleRealmId = 2
            seedConnectedRealm(ineligibleRealmId, lastAuctionPriceDeleteEvent = hoursAgo(1))
            val oldHistoryId = insertUpdateHistory(ineligibleRealmId, daysAgo(5))
            insertAuction(ineligibleRealmId, "auction-old", oldHistoryId)
            insertAuctionPrice(1, "auction-old", daysAgo(5), oldHistoryId)

            service.cleanupPriceHistory()

            assertThat(countPriceRows(ineligibleRealmId)).isEqualTo(1)
        }

        @Test
        fun `updates marker after successful cleanup`() {
            val eligibleRealmId = 1
            seedConnectedRealm(eligibleRealmId, lastAuctionPriceDeleteEvent = daysAgo(2))
            val oldHistoryId = insertUpdateHistory(eligibleRealmId, daysAgo(5))
            insertAuction(eligibleRealmId, "auction-old", oldHistoryId)
            insertAuctionPrice(1, "auction-old", daysAgo(5), oldHistoryId)

            service.cleanupPriceHistory()

            val markerAfter = readPriceDeleteMarker(eligibleRealmId)
            assertThat(markerAfter).isNotNull()
            assertThat(markerAfter).isCloseTo(Instant.now().minus(Duration.ofDays(3)), within(1, ChronoUnit.MINUTES))
        }
    }

    private fun seedConnectedRealm(
        connectedRealmId: Int,
        lastHistoryDeleteEvent: Instant? = null,
        lastHistoryDeleteEventDaily: Instant? = null,
        lastAuctionPriceDeleteEvent: Instant? = null,
    ) {
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
        lastHistoryDeleteEvent?.let {
            jdbcTemplate.update(
                "UPDATE auction_house SET last_history_delete_event = ? WHERE connected_id = ?",
                Timestamp.from(it),
                connectedRealmId,
            )
        }
        lastHistoryDeleteEventDaily?.let {
            jdbcTemplate.update(
                "UPDATE auction_house SET last_history_delete_event_daily = ? WHERE connected_id = ?",
                Timestamp.from(it),
                connectedRealmId,
            )
        }
        lastAuctionPriceDeleteEvent?.let {
            jdbcTemplate.update(
                "UPDATE auction_house SET last_auction_price_delete_event = ? WHERE connected_id = ?",
                Timestamp.from(it),
                connectedRealmId,
            )
        }
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

    private var updateHistorySequence = 0L

    private fun insertUpdateHistory(
        connectedRealmId: Int,
        lastModified: Instant,
    ): Long {
        val lastModifiedDateTime = LocalDateTime.ofInstant(lastModified, ZoneOffset.UTC)
        val uniqueLastModified = lastModifiedDateTime.plusNanos(++updateHistorySequence * 1_000)
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO connected_realm_update_history (
                auction_count,
                last_modified,
                update_timestamp,
                completed_timestamp,
                connected_realm_id
            ) VALUES (1, ?, ?, ?, ?)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            uniqueLastModified,
            uniqueLastModified,
            uniqueLastModified,
            connectedRealmId,
        )!!
    }

    private fun insertAuction(
        connectedRealmId: Int,
        auctionId: String,
        updateHistoryId: Long,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO auction
                (id, connected_realm_id, item_id, quantity, modifier_key, bonus_key, update_history_id)
            VALUES (?, ?, 1, 1, '', '', ?)
            """.trimIndent(),
            auctionId,
            connectedRealmId,
            updateHistoryId,
        )
    }

    private fun insertAuctionPrice(
        id: Long,
        auctionId: String,
        lastModified: Instant,
        updateHistoryId: Long,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO auction_price
                (id, auction_id, buyout, bid, quantity, last_modified, update_history_id)
            VALUES (?, ?, 100, 100, 1, ?, ?)
            """.trimIndent(),
            id,
            auctionId,
            Timestamp.from(lastModified),
            updateHistoryId,
        )
    }

    private fun countHourlyRows(connectedRealmId: Int): Int = countStatsRows("auction_stats_hourly", connectedRealmId)

    private fun countDailyRows(connectedRealmId: Int): Int = countStatsRows("auction_stats_daily", connectedRealmId)

    private fun countStatsRows(
        table: String,
        connectedRealmId: Int,
    ): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM $table WHERE connected_realm_id = ?",
            Int::class.java,
            connectedRealmId,
        ) ?: 0

    private fun countPriceRows(connectedRealmId: Int): Int =
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

    private fun readHistoryDeleteMarker(connectedRealmId: Int): Instant? =
        auctionHouseRepository
            .findByConnectedId(connectedRealmId)
            .map { it.lastHistoryDeleteEvent }
            .orElse(null)

    private fun readDailyHistoryDeleteMarker(connectedRealmId: Int): Instant? =
        auctionHouseRepository
            .findByConnectedId(connectedRealmId)
            .map { it.lastHistoryDeleteEventDaily }
            .orElse(null)

    private fun readPriceDeleteMarker(connectedRealmId: Int): Instant? =
        auctionHouseRepository
            .findByConnectedId(connectedRealmId)
            .map { it.lastAuctionPriceDeleteEvent }
            .orElse(null)

    private fun daysAgo(days: Long): Instant = Instant.now().minus(Duration.ofDays(days))

    private fun hoursAgo(hours: Long): Instant = Instant.now().minus(Duration.ofHours(hours))
}

@TestPropertySource(properties = ["app.scheduling.deleted-auction-cleanup.enabled=false"])
class DeletedAuctionCleanupServiceWhenDisabledTest : IntegrationTestBase() {
    @Autowired
    lateinit var service: DeletedAuctionCleanupService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `skips cleanup when disabled`() {
        seedConnectedRealm(1, lastHistoryDeleteEvent = Instant.now().minus(Duration.ofDays(2)))
        insertHourly(1, LocalDate.now().minusDays(10))

        val results = service.cleanupHourlyStats()

        assertThat(results).hasSize(1)
        assertThat(results.single().connectedRealmId).isNull()
        assertThat(results.single().deletedRows).isZero()
        assertThat(countHourlyRows(1)).isEqualTo(1)
    }

    private fun seedConnectedRealm(
        connectedRealmId: Int,
        lastHistoryDeleteEvent: Instant,
    ) {
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
        jdbcTemplate.update(
            "UPDATE auction_house SET last_history_delete_event = ? WHERE connected_id = ?",
            Timestamp.from(lastHistoryDeleteEvent),
            connectedRealmId,
        )
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

    private fun countHourlyRows(connectedRealmId: Int): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auction_stats_hourly WHERE connected_realm_id = ?",
            Int::class.java,
            connectedRealmId,
        ) ?: 0
}
