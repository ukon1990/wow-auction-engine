package net.jonasmf.auctionengine.service.admin

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.generated.model.AdminJob
import net.jonasmf.auctionengine.repository.rds.AdminJobRepository
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncGuard
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime

class AdminJobServiceTest {
    private val adminJobRepository = mockk<AdminJobRepository>(relaxed = true)
    private val guard = mockk<ProfessionRecipeSyncGuard>()
    private val service = AdminJobService(adminJobRepository, guard)

    @Test
    fun `marks stale profession sync job as failed when advisory lock is not held`() {
        val staleJob = runningProfessionSyncJob()
        every { guard.isLockHeld() } returns false
        every {
            adminJobRepository.findRunningJob(AdminJobDomain.PROFESSION, AdminJobOperations.SYNC_PROFESSIONS)
        } returns staleJob

        assertThatThrownBy { service.getActiveProfessionSyncJob() }
            .isInstanceOf(ResponseStatusException::class.java)
            .extracting { (it as ResponseStatusException).statusCode }
            .isEqualTo(HttpStatus.NOT_FOUND)

        verify {
            adminJobRepository.failJob(staleJob.id, AdminJobOperations.STALE_PROFESSION_SYNC_MESSAGE)
        }
    }

    @Test
    fun `returns active profession sync job while advisory lock is held`() {
        val runningJob = runningProfessionSyncJob()
        every { guard.isLockHeld() } returns true
        every {
            adminJobRepository.findRunningJob(AdminJobDomain.PROFESSION, AdminJobOperations.SYNC_PROFESSIONS)
        } returns runningJob

        val job = service.getActiveProfessionSyncJob()

        org.assertj.core.api.Assertions.assertThat(job).isEqualTo(runningJob)
        verify(exactly = 0) { adminJobRepository.failJob(any<Long>(), any<String>()) }
    }

    private fun runningProfessionSyncJob() =
        AdminJob(
            id = 2,
            domain = AdminJob.Domain.PROFESSION,
            operation = AdminJobOperations.SYNC_PROFESSIONS,
            status = AdminJob.Status.RUNNING,
            startedAt = OffsetDateTime.parse("2026-07-14T13:20:31Z"),
        )
}
