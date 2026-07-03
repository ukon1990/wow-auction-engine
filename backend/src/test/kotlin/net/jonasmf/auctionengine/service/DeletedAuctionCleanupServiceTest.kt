package net.jonasmf.auctionengine.service

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import net.jonasmf.auctionengine.config.DeletedAuctionCleanupProperties
import net.jonasmf.auctionengine.repository.rds.AuctionHouseRepository
import net.jonasmf.auctionengine.repository.rds.DeletedAuctionCleanupRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DeletedAuctionCleanupServiceTest {
    private val repository = mockk<DeletedAuctionCleanupRepository>()
    private val auctionHouseRepository = mockk<AuctionHouseRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-07-02T10:15:30Z"), ZoneOffset.UTC)

    @Test
    fun `hourly cleanup processes one realm in bounded batches and updates marker after success`() {
        val service = service(batchSize = 100)
        val cutoff = LocalDate.parse("2026-06-18")
        every { repository.findNextHourlyCleanupRealm(cutoff) } returnsMany listOf(1084, 1099)
        every { repository.countHourlyCleanupCandidates(1084, cutoff) } returns 150
        every { repository.deleteHourlyBatch(1084, cutoff, 100) } returnsMany listOf(100, 50)
        every { auctionHouseRepository.updateLastHistoryDeleteEvent(1084, any()) } returns 1

        val result = service.cleanupHourlyStats()

        assertThat(result.type).isEqualTo(DeletedAuctionCleanupType.HOURLY_STATS)
        assertThat(result.connectedRealmId).isEqualTo(1084)
        assertThat(result.candidateCount).isEqualTo(150)
        assertThat(result.deletedRows).isEqualTo(150)
        assertThat(result.batchCount).isEqualTo(2)
        verify(exactly = 2) { repository.deleteHourlyBatch(1084, cutoff, 100) }
        verify(exactly = 2) { repository.findNextHourlyCleanupRealm(cutoff) }
        verify(exactly = 1) { auctionHouseRepository.updateLastHistoryDeleteEvent(1084, Instant.parse("2026-06-18T10:15:30Z")) }
        verify(exactly = 0) { repository.optimizeTable(any()) }
    }

    @Test
    fun `hourly cleanup optimizes after the last realm is drained`() {
        val service = service(batchSize = 100, optimizeEnabled = true)
        val cutoff = LocalDate.parse("2026-06-18")
        every { repository.findNextHourlyCleanupRealm(cutoff) } returnsMany listOf(1084, null)
        every { repository.countHourlyCleanupCandidates(1084, cutoff) } returns 50
        every { repository.deleteHourlyBatch(1084, cutoff, 100) } returns 50
        every { auctionHouseRepository.updateLastHistoryDeleteEvent(1084, any()) } returns 1
        every { repository.optimizeTable("auction_stats_hourly") } just runs

        val result = service.cleanupHourlyStats()

        assertThat(result.optimized).isTrue()
        verify(exactly = 1) { repository.optimizeTable("auction_stats_hourly") }
    }

    @Test
    fun `dry-run reports candidates without deleting updating marker or optimizing`() {
        val service = service(dryRun = true, optimizeEnabled = true)
        val cutoff = LocalDate.parse("2026-03-04")
        every { repository.findNextDailyCleanupRealm(cutoff) } returns 1084
        every { repository.countDailyCleanupCandidates(1084, cutoff) } returns 42

        val result = service.cleanupDailyStats()

        assertThat(result.type).isEqualTo(DeletedAuctionCleanupType.DAILY_STATS)
        assertThat(result.connectedRealmId).isEqualTo(1084)
        assertThat(result.candidateCount).isEqualTo(42)
        assertThat(result.deletedRows).isZero()
        assertThat(result.dryRun).isTrue()
        verify(exactly = 0) { repository.deleteDailyBatch(any(), any(), any()) }
        verify(exactly = 0) { auctionHouseRepository.updateLastHistoryDeleteEventDaily(any(), any()) }
        verify(exactly = 0) { repository.optimizeTable(any()) }
    }

    @Test
    fun `optimize runs when price cleanup queue is empty`() {
        val service = service(optimizeEnabled = true)
        val cutoff = Instant.parse("2026-06-25T10:15:30Z")
        every { repository.findNextPriceCleanupRealm(cutoff) } returns null
        every { repository.optimizeTable("auction_price") } just runs

        val result = service.cleanupPriceHistory()

        assertThat(result.connectedRealmId).isNull()
        assertThat(result.optimized).isTrue()
        verify(exactly = 1) { repository.optimizeTable("auction_price") }
        verify(exactly = 0) { repository.deletePriceBatch(any(), any(), any()) }
    }

    @Test
    fun `failed cleanup leaves marker untouched for retry`() {
        val service = service(batchSize = 100)
        val cutoff = LocalDate.parse("2026-06-18")
        every { repository.findNextHourlyCleanupRealm(cutoff) } returns 1084
        every { repository.countHourlyCleanupCandidates(1084, cutoff) } returns 150
        every { repository.deleteHourlyBatch(1084, cutoff, 100) } throws IllegalStateException("deadlock")

        val result = service.cleanupHourlyStats()

        assertThat(result.connectedRealmId).isEqualTo(1084)
        assertThat(result.deletedRows).isZero()
        verify(exactly = 0) { auctionHouseRepository.updateLastHistoryDeleteEvent(any(), any()) }
        verify(exactly = 0) { repository.optimizeTable(any()) }
    }

    private fun service(
        batchSize: Int = 10_000,
        dryRun: Boolean = false,
        optimizeEnabled: Boolean = false,
    ) = DeletedAuctionCleanupService(
        properties =
            DeletedAuctionCleanupProperties(
                hourlyRetention = Duration.ofDays(14),
                dailyRetention = Duration.ofDays(120),
                priceRetention = Duration.ofDays(7),
                batchSize = batchSize,
                dryRun = dryRun,
                optimizeEnabled = optimizeEnabled,
            ),
        cleanupRepository = repository,
        auctionHouseRepository = auctionHouseRepository,
        clock = clock,
    )
}
