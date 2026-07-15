package net.jonasmf.auctionengine.schedules

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.service.DeletedAuctionCleanupService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DeletedAuctionCleanupScheduleTest {
    @Test
    fun `hourly cleanup skips concurrent run and clears its guard`() {
        val cleanupService = mockk<DeletedAuctionCleanupService>()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        every { cleanupService.cleanupHourlyStats() } answers {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            emptyList()
        }
        val schedule = DeletedAuctionCleanupSchedule(cleanupService, immediateBackgroundWorkLauncher())

        try {
            val future = executor.submit<Unit> { schedule.deleteOldHourlyHistoryOnSchedule() }
            assertTrue(started.await(5, TimeUnit.SECONDS))

            schedule.deleteOldHourlyHistoryOnSchedule()
            verify(exactly = 1) { cleanupService.cleanupHourlyStats() }

            release.countDown()
            future.get(5, TimeUnit.SECONDS)
            schedule.deleteOldHourlyHistoryOnSchedule()

            verify(exactly = 2) { cleanupService.cleanupHourlyStats() }
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `daily cleanup skips concurrent run`() {
        val cleanupService = mockk<DeletedAuctionCleanupService>()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        every { cleanupService.cleanupDailyStats() } answers {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            emptyList()
        }
        val schedule = DeletedAuctionCleanupSchedule(cleanupService, immediateBackgroundWorkLauncher())

        try {
            val future = executor.submit<Unit> { schedule.deleteOldDailyHistoryOnSchedule() }
            assertTrue(started.await(5, TimeUnit.SECONDS))

            schedule.deleteOldDailyHistoryOnSchedule()

            verify(exactly = 1) { cleanupService.cleanupDailyStats() }
            release.countDown()
            future.get(5, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `price cleanup skips concurrent run`() {
        val cleanupService = mockk<DeletedAuctionCleanupService>()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        every { cleanupService.cleanupPriceHistory() } answers {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            emptyList()
        }
        val schedule = DeletedAuctionCleanupSchedule(cleanupService, immediateBackgroundWorkLauncher())

        try {
            val future = executor.submit<Unit> { schedule.deleteOldPriceHistoryOnSchedule() }
            assertTrue(started.await(5, TimeUnit.SECONDS))

            schedule.deleteOldPriceHistoryOnSchedule()

            verify(exactly = 1) { cleanupService.cleanupPriceHistory() }
            release.countDown()
            future.get(5, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `startup cleanup launches each cleanup type independently`() {
        val cleanupService = mockk<DeletedAuctionCleanupService>()
        every { cleanupService.cleanupHourlyStats() } returns emptyList()
        every { cleanupService.cleanupDailyStats() } returns emptyList()
        every { cleanupService.cleanupPriceHistory() } returns emptyList()
        val schedule = DeletedAuctionCleanupSchedule(cleanupService, immediateBackgroundWorkLauncher())

        schedule.cleanupAfterStartup()

        verify(exactly = 1) { cleanupService.cleanupHourlyStats() }
        verify(exactly = 1) { cleanupService.cleanupDailyStats() }
        verify(exactly = 1) { cleanupService.cleanupPriceHistory() }
    }
}
