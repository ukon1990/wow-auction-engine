package net.jonasmf.auctionengine.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.repository.rds.AuctionStatsDailyJDBCRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AuctionStatsDailyServiceTest {
    private val repository = mockk<AuctionStatsDailyJDBCRepository>()
    private val service = AuctionStatsDailyService(repository)

    @Test
    fun `updateForDate updates dates after last updated through end date`() {
        every { repository.upsertDailyPriceStatistics(1, LocalDate.of(2026, 1, 2)) } returns 2
        every { repository.upsertDailyPriceStatistics(1, LocalDate.of(2026, 1, 3)) } returns 3

        val result =
            service.updateForDate(
                connectedRealmId = 1,
                lastUpdated = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 1, 3),
            )

        assertEquals(listOf(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3)), result.processedDates)
        assertEquals(LocalDate.of(2026, 1, 3), result.lastProcessedDate)
        assertEquals(5, result.updatedRows)
    }

    @Test
    fun `updateForDate no-ops when end date is not after last updated`() {
        val result =
            service.updateForDate(
                connectedRealmId = 1,
                lastUpdated = LocalDate.of(2026, 1, 3),
                endDate = LocalDate.of(2026, 1, 3),
            )

        assertEquals(emptyList<LocalDate>(), result.processedDates)
        assertNull(result.lastProcessedDate)
        assertEquals(0, result.updatedRows)
        verify(exactly = 0) { repository.upsertDailyPriceStatistics(any(), any()) }
    }
}
