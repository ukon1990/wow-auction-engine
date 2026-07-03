package net.jonasmf.auctionengine.service

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.config.AuctionCleanupProperties
import net.jonasmf.auctionengine.domain.realm.AuctionHouse
import net.jonasmf.auctionengine.repository.rds.AuctionCleanupJdbcRepository
import net.jonasmf.auctionengine.repository.rds.AuctionCleanupTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AuctionCleanupServiceTest {
    private val auctionHouseService = mockk<AuctionHouseService>()
    private val repository = mockk<AuctionCleanupJdbcRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-07-03T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `hourly cleanup processes only first due realm and updates marker after delete`() {
        val service = service()
        every { auctionHouseService.getReadyForHourlyStatsCleanup(now()) } returns listOf(auctionHouse(1), auctionHouse(2))
        every { repository.countHourlyStats(1, LocalDate.of(2026, 6, 19)) } returns 3
        every { repository.deleteHourlyStats(1, LocalDate.of(2026, 6, 19), 500) } returns 3
        every { auctionHouseService.updateLastHistoryDeleted(1, now()) } returns 1

        val result = service.cleanupHourlyStats()

        assertEquals(AuctionCleanupTarget.HOURLY_STATS, result.target)
        assertEquals(1, result.connectedRealmId)
        assertEquals(3, result.candidateRows)
        assertEquals(3, result.deletedRows)
        assertFalse(result.optimized)
        verify(exactly = 0) { repository.countHourlyStats(2, any()) }
    }

    @Test
    fun `daily cleanup uses daily marker`() {
        val service = service()
        every { auctionHouseService.getReadyForDailyStatsCleanup(now()) } returns listOf(auctionHouse(10))
        every { repository.countDailyStats(10, LocalDate.of(2026, 3, 5)) } returns 4
        every { repository.deleteDailyStats(10, LocalDate.of(2026, 3, 5), 500) } returns 4
        every { auctionHouseService.updateLastDailyHistoryDeleted(10, now()) } returns 1

        val result = service.cleanupDailyStats()

        assertEquals(AuctionCleanupTarget.DAILY_STATS, result.target)
        assertEquals(10, result.connectedRealmId)
        assertEquals(4, result.deletedRows)
        verify(exactly = 1) { auctionHouseService.updateLastDailyHistoryDeleted(10, now()) }
        verify(exactly = 0) { auctionHouseService.updateLastHistoryDeleted(any(), any()) }
    }

    @Test
    fun `cleanup drains bounded batches for selected realm before marker update`() {
        val service = service()
        every { auctionHouseService.getReadyForHourlyStatsCleanup(now()) } returns listOf(auctionHouse(1))
        every { repository.countHourlyStats(1, LocalDate.of(2026, 6, 19)) } returns 750
        every { repository.deleteHourlyStats(1, LocalDate.of(2026, 6, 19), 500) } returnsMany listOf(500, 250)
        every { auctionHouseService.updateLastHistoryDeleted(1, now()) } returns 1

        val result = service.cleanupHourlyStats()

        assertEquals(750, result.deletedRows)
        verify(exactly = 2) { repository.deleteHourlyStats(1, LocalDate.of(2026, 6, 19), 500) }
        verify(exactly = 1) { auctionHouseService.updateLastHistoryDeleted(1, now()) }
    }

    @Test
    fun `dry run counts candidates without deleting marker updates or optimize`() {
        val service = service(properties = properties(dryRun = true))
        every { auctionHouseService.getReadyForHourlyStatsCleanup(now()) } returns listOf(auctionHouse(1))
        every { repository.countHourlyStats(1, LocalDate.of(2026, 6, 19)) } returns 7

        val result = service.cleanupHourlyStats()

        assertEquals(7, result.candidateRows)
        assertEquals(0, result.deletedRows)
        assertTrue(result.dryRun)
        verify(exactly = 0) { repository.deleteHourlyStats(any(), any(), any()) }
        verify(exactly = 0) { auctionHouseService.updateLastHistoryDeleted(any(), any()) }
        verify(exactly = 0) { repository.optimize(any()) }
    }

    @Test
    fun `optimize runs only when no due realms remain`() {
        val service = service()
        every { auctionHouseService.getReadyForHourlyStatsCleanup(now()) } returns emptyList()
        justRun { repository.optimize(AuctionCleanupTarget.HOURLY_STATS) }

        val result = service.cleanupHourlyStats()

        assertTrue(result.optimized)
        verify(exactly = 1) { repository.optimize(AuctionCleanupTarget.HOURLY_STATS) }
    }

    @Test
    fun `disabled cleanup does not query or optimize`() {
        val service = service(properties = properties(enabled = false))

        val result = service.cleanupHourlyStats()

        assertFalse(result.optimized)
        verify(exactly = 0) { auctionHouseService.getReadyForHourlyStatsCleanup(any()) }
        verify(exactly = 0) { repository.optimize(any()) }
    }

    @Test
    fun `deleted auction cleanup uses deleted auction retention and hourly marker`() {
        val service = service()
        val deleteBefore = OffsetDateTime.parse("2026-06-26T10:00:00Z")
        every { auctionHouseService.getReadyForHourlyStatsCleanup(now()) } returns listOf(auctionHouse(1))
        every { repository.countAuctionPrices(1, deleteBefore) } returns 5
        every { repository.deleteAuctionPrices(1, deleteBefore, 500) } returns 5
        every { auctionHouseService.updateLastHistoryDeleted(1, now()) } returns 1

        val result = service.cleanupDeletedAuctions()

        assertEquals(AuctionCleanupTarget.AUCTION_PRICES, result.target)
        assertEquals(5, result.deletedRows)
    }

    private fun service(properties: AuctionCleanupProperties = properties()) =
        AuctionCleanupService(auctionHouseService, repository, properties, clock)

    private fun properties(
        enabled: Boolean = true,
        dryRun: Boolean = false,
    ) = AuctionCleanupProperties(
        enabled = enabled,
        dryRun = dryRun,
        batchSize = 500,
        hourlyRetention = Duration.ofDays(14),
        dailyRetention = Duration.ofDays(120),
        deletedAuctionRetention = Duration.ofDays(7),
    )

    private fun auctionHouse(connectedRealmId: Int) =
        AuctionHouse(
            id = connectedRealmId,
            connectedId = connectedRealmId,
        )

    private fun now() = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
}
