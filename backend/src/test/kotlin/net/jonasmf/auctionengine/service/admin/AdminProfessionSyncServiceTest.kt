package net.jonasmf.auctionengine.service.admin

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.generated.model.AdminJob
import net.jonasmf.auctionengine.repository.rds.AdminJobRepository
import net.jonasmf.auctionengine.service.ProfessionRecipePersistenceSummary
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncGuard
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncLock
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncResult
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncService
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AdminProfessionSyncServiceTest {
    private val adminJobRepository = mockk<AdminJobRepository>(relaxed = true)
    private val professionRecipeSyncService = mockk<ProfessionRecipeSyncService>()
    private val guard = mockk<ProfessionRecipeSyncGuard>(relaxed = true)
    private val service = AdminProfessionSyncService(adminJobRepository, professionRecipeSyncService, guard)
    private val syncLock = mockk<ProfessionRecipeSyncLock>(relaxed = true)

    @Test
    fun `completes job with profession sync diagnostics`() {
        every { professionRecipeSyncService.syncConfiguredStaticDataRegion(any(), any()) } returns syncResult()
        service.runSyncJob(42, syncLock)

        verify {
            adminJobRepository.completeJob(
                42,
                match { summary ->
                    summary["region"] == "Europe" &&
                        summary["professionsFetched"] == 2 &&
                        summary["professionsProcessed"] == 2 &&
                        summary["recipeFailures"] == 1 &&
                        summary["reagentsReplaced"] == 13 &&
                        summary["durationMs"] == 987L
                },
            )
        }
        verify(exactly = 1) { guard.release(syncLock) }
        assertGuardWasReleased()
    }

    @Test
    fun `clears stale running job before starting a new sync`() {
        val staleJob = adminJob()
        every { guard.isLockHeld() } returns false
        every {
            adminJobRepository.findRunningJob(AdminJobDomain.PROFESSION, AdminJobOperations.SYNC_PROFESSIONS)
        } returns staleJob
        every { guard.tryAcquire() } returns syncLock
        every { adminJobRepository.createJob(any(), any(), any()) } returns adminJob()

        service.syncProfessionRecipes("admin")

        verify { adminJobRepository.failJob(staleJob.id, AdminJobOperations.STALE_PROFESSION_SYNC_MESSAGE) }
    }

    @Test
    fun `rejects duplicate manual start while sync is running`() {
        every { guard.isLockHeld() } returns true
        every { guard.tryAcquire() } returns null

        assertThatThrownBy { service.syncProfessionRecipes("admin") }
            .isInstanceOf(ResponseStatusException::class.java)
            .extracting { (it as ResponseStatusException).statusCode }
            .isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `marks job failed and releases guard after top-level failure`() {
        every { professionRecipeSyncService.syncConfiguredStaticDataRegion(any(), any()) } throws IllegalStateException("private upstream detail")
        service.runSyncJob(42, syncLock)

        verify { adminJobRepository.failJob(42, "Profession/recipe sync failed") }
        verify(exactly = 1) { guard.release(syncLock) }
        assertGuardWasReleased()
    }

    @Test
    fun `propagates request MDC to asynchronous sync job`() {
        val job = adminJob()
        val started = CountDownLatch(1)
        val requestId = AtomicReference<String?>()
        val jobId = AtomicReference<String?>()
        every { guard.tryAcquire() } returns syncLock
        every { adminJobRepository.createJob(any(), any(), any()) } returns job
        every { professionRecipeSyncService.syncConfiguredStaticDataRegion(any(), any()) } answers {
            requestId.set(MDC.get("requestId"))
            jobId.set(MDC.get("adminJobId"))
            started.countDown()
            syncResult()
        }
        MDC.put("requestId", "request-123")

        try {
            service.syncProfessionRecipes("admin")

            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(requestId.get()).isEqualTo("request-123")
            assertThat(jobId.get()).isEqualTo(job.id.toString())
        } finally {
            MDC.clear()
        }
    }

    private fun assertGuardWasReleased() {
        every { guard.tryAcquire() } returns syncLock
        every {
            adminJobRepository.createJob(
                AdminJobDomain.PROFESSION,
                AdminJobOperations.SYNC_PROFESSIONS,
                "admin",
            )
        } throws IllegalStateException("database unavailable")

        assertThatThrownBy { service.syncProfessionRecipes("admin") }
            .isInstanceOf(IllegalStateException::class.java)
        verify(atLeast = 1) { guard.release(syncLock) }
    }

    private fun syncResult() =
        ProfessionRecipeSyncResult(
            region = Region.Europe,
            professionsFetched = 2,
            skillTiersFetched = 3,
            recipeReferencesDiscovered = 17,
            recipesFetched = 16,
            recipeFailures = 1,
            modifiedCraftingCategoriesFetched = 4,
            modifiedCraftingSlotsFetched = 5,
            persistenceSummary =
                ProfessionRecipePersistenceSummary(
                    professionsUpserted = 2,
                    skillTiersUpserted = 2,
                    categoriesReplaced = 7,
                    recipesUpserted = 16,
                    reagentsReplaced = 13,
                    recipeSlotsReplaced = 10,
                    modifiedCraftingCategoriesUpserted = 4,
                    modifiedCraftingSlotsUpserted = 5,
                    slotCategoryLinksReplaced = 6,
                ),
            durationMs = 987,
        )

    private fun adminJob() =
        AdminJob(
            id = 42,
            domain = AdminJob.Domain.PROFESSION,
            operation = AdminJobOperations.SYNC_PROFESSIONS,
            status = AdminJob.Status.RUNNING,
            startedAt = OffsetDateTime.parse("2026-07-12T18:00:00Z"),
        )
}
