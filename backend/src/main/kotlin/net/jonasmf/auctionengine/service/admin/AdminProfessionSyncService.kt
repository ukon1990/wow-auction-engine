package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.generated.model.AdminJob
import net.jonasmf.auctionengine.repository.rds.AdminJobRepository
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncGuard
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncLock
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncResult
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncService
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.CompletableFuture

@Service
class AdminProfessionSyncService(
    private val adminJobRepository: AdminJobRepository,
    private val professionRecipeSyncService: ProfessionRecipeSyncService,
    private val professionRecipeSyncGuard: ProfessionRecipeSyncGuard,
) {
    private val log = LoggerFactory.getLogger(AdminProfessionSyncService::class.java)

    fun syncProfessionRecipes(requestedBy: String?): AdminJob {
        val syncLock = professionRecipeSyncGuard.tryAcquire()
        if (syncLock == null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Profession/recipe sync is already running")
        }

        var job: AdminJob? = null
        try {
            job =
                adminJobRepository.createJob(
                    domain = AdminJobDomain.PROFESSION,
                    operation = AdminJobOperations.SYNC_PROFESSIONS,
                    requestedBy = requestedBy,
                )
            log.info("Accepted profession/recipe sync job id={} requestedBy={}", job.id, requestedBy)
            val mdcSnapshot = MDC.getCopyOfContextMap()
            CompletableFuture.runAsync {
                withJobMdc(mdcSnapshot, job.id) {
                    runSyncJob(job.id, syncLock)
                }
            }
            return job
        } catch (error: Throwable) {
            job?.let { adminJobRepository.failJob(it.id, "Profession/recipe sync could not be started") }
            professionRecipeSyncGuard.release(syncLock)
            throw error
        }
    }

    fun runSyncJob(
        jobId: Long,
        syncLock: ProfessionRecipeSyncLock,
    ) {
        val startTime = System.currentTimeMillis()
        log.info("Starting profession/recipe sync job id={}", jobId)
        try {
            val result = professionRecipeSyncService.syncConfiguredStaticDataRegion(syncLock::ensureActive)
            syncLock.ensureActive()
            adminJobRepository.completeJob(jobId, result.toSummaryMap())
            log.info(
                "Completed profession/recipe sync job id={} in {}ms region={} professions={} tiers={} recipes={} failures={}",
                jobId,
                System.currentTimeMillis() - startTime,
                result.region,
                result.professionsFetched,
                result.skillTiersFetched,
                result.recipesFetched,
                result.recipeFailures,
            )
        } catch (error: Throwable) {
            log.error("Failed profession/recipe sync job id={} in {}ms", jobId, System.currentTimeMillis() - startTime, error)
            adminJobRepository.failJob(jobId, "Profession/recipe sync failed")
        } finally {
            professionRecipeSyncGuard.release(syncLock)
        }
    }

}
private fun <T> withJobMdc(
    mdc: Map<String, String>?,
    jobId: Long,
    block: () -> T,
): T {
    val previous = MDC.getCopyOfContextMap()
    try {
        if (mdc != null) {
            MDC.setContextMap(mdc)
        } else {
            MDC.clear()
        }
        MDC.put("adminJobId", jobId.toString())
        return block()
    } finally {
        if (previous != null) {
            MDC.setContextMap(previous)
        } else {
            MDC.clear()
        }
    }
}

private fun ProfessionRecipeSyncResult.toSummaryMap(): Map<String, Any> =
    mapOf(
        "region" to region.name,
        "durationMs" to durationMs,
        "professionsFetched" to professionsFetched,
        "professionsProcessed" to professionsFetched,
        "skillTiersFetched" to skillTiersFetched,
        "recipeReferencesDiscovered" to recipeReferencesDiscovered,
        "recipesFetched" to recipesFetched,
        "recipeFailures" to recipeFailures,
        "modifiedCraftingCategoriesFetched" to modifiedCraftingCategoriesFetched,
        "modifiedCraftingSlotsFetched" to modifiedCraftingSlotsFetched,
        "professionsUpserted" to persistenceSummary.professionsUpserted,
        "skillTiersUpserted" to persistenceSummary.skillTiersUpserted,
        "categoriesReplaced" to persistenceSummary.categoriesReplaced,
        "recipesUpserted" to persistenceSummary.recipesUpserted,
        "reagentsReplaced" to persistenceSummary.reagentsReplaced,
        "recipeSlotsReplaced" to persistenceSummary.recipeSlotsReplaced,
        "modifiedCraftingCategoriesUpserted" to persistenceSummary.modifiedCraftingCategoriesUpserted,
        "modifiedCraftingSlotsUpserted" to persistenceSummary.modifiedCraftingSlotsUpserted,
        "slotCategoryLinksReplaced" to persistenceSummary.slotCategoryLinksReplaced,
    )
