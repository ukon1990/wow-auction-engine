package net.jonasmf.auctionengine.service.admin

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.generated.model.AdminJob
import net.jonasmf.auctionengine.repository.rds.AdminExpansionRepository
import net.jonasmf.auctionengine.repository.rds.AdminJobRepository
import net.jonasmf.auctionengine.service.ItemSyncService
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class AdminExpansionServiceTest {
    private val adminExpansionRepository = mockk<AdminExpansionRepository>()
    private val adminJobRepository = mockk<AdminJobRepository>(relaxed = true)
    private val itemSyncService = mockk<ItemSyncService>()
    private val rejectingExecutor = Executor { throw RejectedExecutionException("shutting down") }
    private val service =
        AdminExpansionService(
            adminExpansionRepository,
            adminJobRepository,
            itemSyncService,
            rejectingExecutor,
        )

    @Test
    fun `apply job clears guard and fails job when dispatch is rejected`() {
        every { adminJobRepository.createJob(any(), any(), any()) } returns adminJob()

        repeat(2) {
            assertThatThrownBy { service.applyExpansionRanges("admin") }
                .isInstanceOf(RejectedExecutionException::class.java)
        }

        verify(exactly = 2) {
            adminJobRepository.createJob(
                AdminJobDomain.ITEM,
                AdminJobOperations.APPLY_EXPANSION_RANGES,
                "admin",
            )
        }
        verify(exactly = 2) { adminJobRepository.failJob(42, any<RejectedExecutionException>()) }
    }

    @Test
    fun `fetch job clears guard and fails job when dispatch is rejected`() {
        every { adminJobRepository.createJob(any(), any(), any()) } returns adminJob()

        repeat(2) {
            assertThatThrownBy { service.fetchMissingExpansionRangeItems("admin") }
                .isInstanceOf(RejectedExecutionException::class.java)
        }

        verify(exactly = 2) {
            adminJobRepository.createJob(
                AdminJobDomain.ITEM,
                AdminJobOperations.FETCH_EXPANSION_RANGE_ITEMS,
                "admin",
            )
        }
        verify(exactly = 2) { adminJobRepository.failJob(42, any<RejectedExecutionException>()) }
    }

    private fun adminJob() =
        AdminJob(
            id = 42,
            domain = AdminJob.Domain.ITEM,
            operation = AdminJobOperations.APPLY_EXPANSION_RANGES,
            status = AdminJob.Status.RUNNING,
            startedAt = OffsetDateTime.parse("2026-07-15T12:00:00Z"),
        )
}
