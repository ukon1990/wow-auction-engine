package net.jonasmf.auctionengine.service.admin

import net.jonasmf.auctionengine.generated.model.AdminExpansion
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRange
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRangeRequest
import net.jonasmf.auctionengine.generated.model.AdminItemJob
import net.jonasmf.auctionengine.repository.rds.AdminExpansionRepository
import net.jonasmf.auctionengine.service.ItemSyncResult
import net.jonasmf.auctionengine.service.ItemSyncService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

private const val APPLY_EXPANSION_RANGES_JOB = "apply-expansion-ranges"
private const val FETCH_EXPANSION_RANGE_ITEMS_JOB = "fetch-expansion-range-items"

@Service
class AdminExpansionService(
    private val adminExpansionRepository: AdminExpansionRepository,
    private val itemSyncService: ItemSyncService,
) {
    private val applyRunning = AtomicBoolean(false)
    private val fetchMissingRunning = AtomicBoolean(false)

    fun listExpansions(): List<AdminExpansion> = adminExpansionRepository.listExpansions()

    fun listRanges(): List<AdminExpansionItemRange> = adminExpansionRepository.listRanges()

    fun createRange(request: AdminExpansionItemRangeRequest): AdminExpansionItemRange {
        validateRangeRequest(request, idToIgnore = null)
        return adminExpansionRepository.createRange(request)
    }

    fun updateRange(
        id: Long,
        request: AdminExpansionItemRangeRequest,
    ): AdminExpansionItemRange {
        if (adminExpansionRepository.findRange(id) == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Expansion item range not found: $id")
        }
        validateRangeRequest(request, idToIgnore = id)
        return adminExpansionRepository.updateRange(id, request)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Expansion item range not found: $id")
    }

    fun deleteRange(id: Long) {
        if (!adminExpansionRepository.deleteRange(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Expansion item range not found: $id")
        }
    }

    fun applyExpansionRanges(requestedBy: String?): AdminItemJob {
        if (!applyRunning.compareAndSet(false, true)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Expansion range apply job is already running")
        }
        val job = adminExpansionRepository.createJob(APPLY_EXPANSION_RANGES_JOB, requestedBy)
        CompletableFuture.runAsync { runApplyJob(job.id) }
        return job
    }

    fun fetchMissingExpansionRangeItems(requestedBy: String?): AdminItemJob {
        if (!fetchMissingRunning.compareAndSet(false, true)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Expansion range item fetch job is already running")
        }
        val job = adminExpansionRepository.createJob(FETCH_EXPANSION_RANGE_ITEMS_JOB, requestedBy)
        CompletableFuture.runAsync { runFetchMissingJob(job.id) }
        return job
    }

    fun getJob(id: Long): AdminItemJob =
        adminExpansionRepository.findJob(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Admin item job not found: $id")

    fun runApplyJob(jobId: Long) {
        try {
            val summary = adminExpansionRepository.applyEnabledRanges()
            adminExpansionRepository.completeJob(
                jobId,
                mapOf(
                    "matchedItemCount" to summary.matchedItemCount,
                    "updatedItemCount" to summary.updatedItemCount,
                    "conflictItemCount" to summary.conflictItemCount,
                ),
            )
        } catch (error: Throwable) {
            adminExpansionRepository.failJob(jobId, error)
        } finally {
            applyRunning.set(false)
        }
    }

    fun runFetchMissingJob(jobId: Long) {
        try {
            val result = itemSyncService.syncMissingItemsFromEnabledExpansionRanges()
            adminExpansionRepository.completeJob(jobId, result.toSummaryMap())
        } catch (error: Throwable) {
            adminExpansionRepository.failJob(jobId, error)
        } finally {
            fetchMissingRunning.set(false)
        }
    }

    private fun validateRangeRequest(
        request: AdminExpansionItemRangeRequest,
        idToIgnore: Long?,
    ) {
        if (request.startItemId > request.endItemId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "startItemId must be less than or equal to endItemId")
        }
        if (!adminExpansionRepository.expansionExists(request.expansionId)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Expansion not found: ${request.expansionId}")
        }
        if (adminExpansionRepository.hasOverlappingEnabledRange(idToIgnore, request)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Enabled range overlaps an enabled range for another expansion",
            )
        }
    }
}

private fun ItemSyncResult.toSummaryMap(): Map<String, Any?> =
    mapOf(
        "region" to region.name,
        "auctionSourceCount" to auctionSourceCount,
        "recipeCraftedSourceCount" to recipeCraftedSourceCount,
        "recipeReagentSourceCount" to recipeReagentSourceCount,
        "candidateItemCount" to candidateItemCount,
        "existingItemCount" to existingItemCount,
        "missingItemCount" to missingItemCount,
        "skippedByBackoffCount" to skippedByBackoffCount,
        "skippedManualDisabledCount" to skippedManualDisabledCount,
        "fetchedItemCount" to fetchedItemCount,
        "itemFetchFailures" to itemFetchFailures,
        "persistedItemCount" to persistedItemCount,
        "durationMs" to durationMs,
    )
