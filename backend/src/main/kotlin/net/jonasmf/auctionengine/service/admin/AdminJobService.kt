package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.generated.model.AdminJob
import net.jonasmf.auctionengine.repository.rds.AdminJobRepository
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncGuard
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

object AdminJobDomain {
    const val ITEM = "item"
    const val RECIPE = "recipe"
    const val PROFESSION = "profession"
    const val MEDIA = "media"
    const val SYSTEM = "system"
}

object AdminJobOperations {
    const val APPLY_EXPANSION_RANGES = "apply-expansion-ranges"
    const val FETCH_EXPANSION_RANGE_ITEMS = "fetch-expansion-range-items"
    const val SYNC_PROFESSIONS = "sync-professions"
    const val STALE_PROFESSION_SYNC_MESSAGE = "Profession/recipe sync interrupted before completion"
}

@Service
class AdminJobService(
    private val adminJobRepository: AdminJobRepository,
    private val professionRecipeSyncGuard: ProfessionRecipeSyncGuard,
) {
    private val log = LoggerFactory.getLogger(AdminJobService::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun reconcileStaleProfessionSyncJobsOnStartup() {
        reconcileStaleProfessionSyncJobs()
    }

    fun getJob(id: Long): AdminJob =
        adminJobRepository.findJob(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Admin job not found: $id")

    fun getActiveProfessionSyncJob(): AdminJob {
        val runningJob =
            adminJobRepository.findRunningJob(AdminJobDomain.PROFESSION, AdminJobOperations.SYNC_PROFESSIONS)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No profession/recipe sync job is running")

        if (!professionRecipeSyncGuard.isLockHeld()) {
            adminJobRepository.failJob(runningJob.id, AdminJobOperations.STALE_PROFESSION_SYNC_MESSAGE)
            log.warn(
                "Marked stale profession/recipe sync job id={} as failed because the advisory lock is not held",
                runningJob.id,
            )
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No profession/recipe sync job is running")
        }

        return runningJob
    }

    fun reconcileStaleProfessionSyncJobs() {
        if (professionRecipeSyncGuard.isLockHeld()) return

        adminJobRepository
            .findRunningJob(AdminJobDomain.PROFESSION, AdminJobOperations.SYNC_PROFESSIONS)
            ?.let { job ->
                adminJobRepository.failJob(job.id, AdminJobOperations.STALE_PROFESSION_SYNC_MESSAGE)
                log.warn(
                    "Marked stale profession/recipe sync job id={} as failed because the advisory lock is not held",
                    job.id,
                )
            }
    }
}

