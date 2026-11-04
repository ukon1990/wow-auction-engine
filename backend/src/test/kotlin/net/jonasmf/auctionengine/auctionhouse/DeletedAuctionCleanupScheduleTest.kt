package net.jonasmf.auctionengine.auctionhouse

import net.jonasmf.auctionengine.schedules.immediateBackgroundWorkLauncher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.auctionhouse.DeletedAuctionCleanupService
import org.junit.jupiter.api.Test

class DeletedAuctionCleanupScheduleTest {
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
