package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.item.Item
import net.jonasmf.auctionengine.integration.blizzard.ItemApiClient
import net.jonasmf.auctionengine.repository.rds.ItemFetchFailureState
import net.jonasmf.auctionengine.repository.rds.ItemJdbcRepository
import net.jonasmf.auctionengine.repository.rds.ItemPersistenceSummary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

private const val ITEM_FETCH_BATCH_SIZE = 100
private const val ITEM_FETCH_CONCURRENCY = 20
private const val ITEM_FETCH_BACKOFF_DISABLE_FAILURE_COUNT = 10
private val ITEM_FETCH_BASE_BACKOFF: Duration = Duration.ofHours(1)
private val ITEM_FETCH_MAX_BACKOFF: Duration = Duration.ofDays(7)

private data class ItemFetchOutcome(
    val itemId: Int,
    val item: Item? = null,
    val error: Throwable? = null,
)

data class ItemSyncResult(
    val region: Region,
    val auctionSourceCount: Int,
    val recipeCraftedSourceCount: Int,
    val recipeReagentSourceCount: Int,
    val candidateItemCount: Int,
    val existingItemCount: Int,
    val missingItemCount: Int,
    val skippedByBackoffCount: Int,
    val skippedManualDisabledCount: Int,
    val fetchedItemCount: Int,
    val itemFetchFailures: Int,
    val persistedItemCount: Int,
    val persistenceSummary: ItemPersistenceSummary,
    val durationMs: Long,
)

@Service
class ItemSyncService(
    private val properties: BlizzardApiProperties,
    private val itemApiClient: ItemApiClient,
    private val itemJdbcRepository: ItemJdbcRepository,
    private val itemBulkSyncService: ItemBulkSyncService,
    private val blizzardMediaService: BlizzardMediaService,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val log = LoggerFactory.getLogger(ItemSyncService::class.java)

    fun syncAllConfiguredRegions(): List<ItemSyncResult> = listOf(syncConfiguredStaticDataRegion())

    fun syncConfiguredStaticDataRegion(): ItemSyncResult = syncRegion(properties.staticDataRegion)

    fun syncRegion(region: Region): ItemSyncResult {
        val startTime = System.currentTimeMillis()
        val today = LocalDate.now(clock)
        log.info("Starting item sync for region {} date={}", region, today)

        val discovery = itemJdbcRepository.findMissingItemIdsForDate(today)
        return syncDiscoveredItems(
            region = region,
            startTime = startTime,
            auctionSourceCount = discovery.auctionSourceCount,
            recipeCraftedSourceCount = discovery.recipeCraftedSourceCount,
            recipeReagentSourceCount = discovery.recipeReagentSourceCount,
            candidateItemCount = discovery.candidateItemCount,
            existingItemCount = discovery.existingItemCount,
            missingIds = discovery.missingItemIds,
            sourceDescription = "auction/recipe",
        )
    }

    fun syncMissingItemsFromEnabledExpansionRanges(region: Region = properties.staticDataRegion): ItemSyncResult {
        val startTime = System.currentTimeMillis()
        val discovery = itemJdbcRepository.findMissingItemIdsForEnabledExpansionRanges()
        log.info(
            "Starting expansion range item sync for region {} candidates={} existing={} missing={}",
            region,
            discovery.candidateItemCount,
            discovery.existingItemCount,
            discovery.missingItemIds.size,
        )
        return syncDiscoveredItems(
            region = region,
            startTime = startTime,
            auctionSourceCount = 0,
            recipeCraftedSourceCount = 0,
            recipeReagentSourceCount = 0,
            candidateItemCount = discovery.candidateItemCount,
            existingItemCount = discovery.existingItemCount,
            missingIds = discovery.missingItemIds,
            sourceDescription = "expansion-range",
        )
    }

    private fun syncDiscoveredItems(
        region: Region,
        startTime: Long,
        auctionSourceCount: Int,
        recipeCraftedSourceCount: Int,
        recipeReagentSourceCount: Int,
        candidateItemCount: Int,
        existingItemCount: Int,
        missingIds: List<Int>,
        sourceDescription: String,
    ): ItemSyncResult {
        val now = OffsetDateTime.now(clock)
        val eligibility = itemJdbcRepository.classifyItemRetryEligibility(missingIds, now)
        val retryableMissingIds = eligibility.retryableIds
        val existingFailureStates =
            itemJdbcRepository
                .findItemFetchFailureStates(retryableMissingIds)
                .toMutableMap()

        log.info(
            "Discovered item sync sources region={} source={} auction={} crafted={} reagents={} candidates={} existing={} missing={} retryable={} cooldownSkipped={} manualDisabledSkipped={}",
            region,
            sourceDescription,
            auctionSourceCount,
            recipeCraftedSourceCount,
            recipeReagentSourceCount,
            candidateItemCount,
            existingItemCount,
            missingIds.size,
            retryableMissingIds.size,
            eligibility.cooldownSkippedIds.size,
            eligibility.manualDisabledIds.size,
        )

        val missingIdBatches = retryableMissingIds.chunked(ITEM_FETCH_BATCH_SIZE)
        val allFetchedIds = mutableListOf<Int>()
        var totalFetchFailures = 0
        var totalFetchedItems = 0
        var totalLocalesUpserted = 0
        var totalItemQualitiesUpserted = 0
        var totalInventoryTypesUpserted = 0
        var totalItemBindingsUpserted = 0
        var totalItemClassesUpserted = 0
        var totalItemSubclassesUpserted = 0
        var totalItemAppearanceReferencesUpserted = 0
        var totalItemsUpserted = 0
        var totalItemAppearanceLinksUpserted = 0

        missingIdBatches.forEachIndexed { index, batchIds ->
            val batchNumber = index + 1
            log.info(
                "Starting item fetch batch region={} batch={}/{} batchSize={} completedBefore={}/{}",
                region,
                batchNumber,
                missingIdBatches.size,
                batchIds.size,
                index * ITEM_FETCH_BATCH_SIZE,
                retryableMissingIds.size,
            )

            val batchOutcomes =
                Flux
                    .fromIterable(batchIds)
                    .flatMap({ itemId ->
                        Mono
                            .fromCallable { itemApiClient.getById(itemId, region) }
                            .map<ItemFetchOutcome> { item -> ItemFetchOutcome(itemId = itemId, item = item) }
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume { error -> Mono.just(ItemFetchOutcome(itemId = itemId, error = error)) }
                    }, ITEM_FETCH_CONCURRENCY)
                    .collectList()
                    .block()
                    .orEmpty()

            val batchSucceeded = batchOutcomes.count { it.error == null }
            val batchFailed = batchOutcomes.size - batchSucceeded
            val completedAfterBatch = index * ITEM_FETCH_BATCH_SIZE + batchOutcomes.size
            log.info(
                "Completed item fetch batch region={} batch={}/{} completed={}/{} succeeded={} failed={}",
                region,
                batchNumber,
                missingIdBatches.size,
                completedAfterBatch,
                retryableMissingIds.size,
                batchSucceeded,
                batchFailed,
            )

            batchOutcomes.filter { it.error != null }.forEach { failure ->
                val failedAt = OffsetDateTime.now(clock)
                val previousFailureCount = existingFailureStates[failure.itemId]?.failureCount ?: 0
                val currentFailureCount = previousFailureCount + 1
                val manualDisabled = currentFailureCount >= ITEM_FETCH_BACKOFF_DISABLE_FAILURE_COUNT
                val parsedError = parseError(failure.error)
                val nextRetryAt = if (manualDisabled) null else failedAt.plus(backoffForFailureCount(currentFailureCount))
                itemJdbcRepository.upsertItemFetchFailureState(
                    itemId = failure.itemId,
                    failureCount = currentFailureCount,
                    lastErrorStatus = parsedError.first,
                    lastErrorMessage = parsedError.second,
                    lastFailedAt = failedAt,
                    nextRetryAt = nextRetryAt,
                    manualDisabled = manualDisabled,
                )
                existingFailureStates[failure.itemId] =
                    ItemFetchFailureState(
                        itemId = failure.itemId,
                        failureCount = currentFailureCount,
                        lastErrorStatus = parsedError.first,
                        lastErrorMessage = parsedError.second,
                        lastFailedAt = failedAt,
                        nextRetryAt = nextRetryAt,
                        manualDisabled = manualDisabled,
                    )
                log.warn(
                    "Skipping item {} for region {} after fetch failure count={} manualDisabled={} nextRetryAt={} status={} message={}",
                    failure.itemId,
                    region,
                    currentFailureCount,
                    manualDisabled,
                    nextRetryAt,
                    parsedError.first ?: "-",
                    parsedError.second ?: "unknown error",
                )
            }

            val batchItems =
                batchOutcomes
                    .mapNotNull(ItemFetchOutcome::item)
                    .map { item -> blizzardMediaService.resolveItem(region, item) }
            if (batchItems.isNotEmpty()) {
                log.info(
                    "Persisting item batch region={} batch={}/{} itemCount={}",
                    region,
                    batchNumber,
                    missingIdBatches.size,
                    batchItems.size,
                )
                val batchPersistenceSummary = itemBulkSyncService.syncItems(batchItems)
                totalLocalesUpserted += batchPersistenceSummary.localesUpserted
                totalItemQualitiesUpserted += batchPersistenceSummary.itemQualitiesUpserted
                totalInventoryTypesUpserted += batchPersistenceSummary.inventoryTypesUpserted
                totalItemBindingsUpserted += batchPersistenceSummary.itemBindingsUpserted
                totalItemClassesUpserted += batchPersistenceSummary.itemClassesUpserted
                totalItemSubclassesUpserted += batchPersistenceSummary.itemSubclassesUpserted
                totalItemAppearanceReferencesUpserted += batchPersistenceSummary.itemAppearanceReferencesUpserted
                totalItemsUpserted += batchPersistenceSummary.itemsUpserted
                totalItemAppearanceLinksUpserted += batchPersistenceSummary.itemAppearanceLinksUpserted
                totalFetchedItems += batchItems.size
                allFetchedIds += batchItems.map(Item::id)
                itemJdbcRepository.clearItemFetchFailureStates(batchItems.map(Item::id))
                log.info(
                    "Persisted item batch region={} batch={}/{} itemCount={} attemptedItemUpserts={}",
                    region,
                    batchNumber,
                    missingIdBatches.size,
                    batchItems.size,
                    batchPersistenceSummary.itemsUpserted,
                )
            }

            totalFetchFailures += batchFailed
        }

        val persistenceSummary =
            ItemPersistenceSummary(
                localesUpserted = totalLocalesUpserted,
                itemQualitiesUpserted = totalItemQualitiesUpserted,
                inventoryTypesUpserted = totalInventoryTypesUpserted,
                itemBindingsUpserted = totalItemBindingsUpserted,
                itemClassesUpserted = totalItemClassesUpserted,
                itemSubclassesUpserted = totalItemSubclassesUpserted,
                itemAppearanceReferencesUpserted = totalItemAppearanceReferencesUpserted,
                itemsUpserted = totalItemsUpserted,
                itemAppearanceLinksUpserted = totalItemAppearanceLinksUpserted,
            )
        val persistedItemCount = itemJdbcRepository.findExistingItemIds(allFetchedIds).size

        return ItemSyncResult(
            region = region,
            auctionSourceCount = auctionSourceCount,
            recipeCraftedSourceCount = recipeCraftedSourceCount,
            recipeReagentSourceCount = recipeReagentSourceCount,
            candidateItemCount = candidateItemCount,
            existingItemCount = existingItemCount,
            missingItemCount = missingIds.size,
            skippedByBackoffCount = eligibility.cooldownSkippedIds.size,
            skippedManualDisabledCount = eligibility.manualDisabledIds.size,
            fetchedItemCount = totalFetchedItems,
            itemFetchFailures = totalFetchFailures,
            persistedItemCount = persistedItemCount,
            persistenceSummary = persistenceSummary,
            durationMs = System.currentTimeMillis() - startTime,
        ).also { result ->
            if (result.persistedItemCount != result.fetchedItemCount) {
                log.warn(
                    "Item sync persistence mismatch for region {} fetched={} persisted={} attemptedUpserts={}",
                    result.region,
                    result.fetchedItemCount,
                    result.persistedItemCount,
                    result.persistenceSummary.itemsUpserted,
                )
            }
            log.info(
                "Finished item sync for region {} in {}ms auction={} crafted={} reagents={} candidates={} existing={} missing={} retryable={} cooldownSkipped={} manualDisabledSkipped={} fetched={} failed={} persistedItems={} attemptedItemUpserts={}",
                result.region,
                result.durationMs,
                result.auctionSourceCount,
                result.recipeCraftedSourceCount,
                result.recipeReagentSourceCount,
                result.candidateItemCount,
                result.existingItemCount,
                result.missingItemCount,
                result.missingItemCount - result.skippedByBackoffCount - result.skippedManualDisabledCount,
                result.skippedByBackoffCount,
                result.skippedManualDisabledCount,
                result.fetchedItemCount,
                result.itemFetchFailures,
                result.persistedItemCount,
                result.persistenceSummary.itemsUpserted,
            )
        }
    }

    private fun backoffForFailureCount(failureCount: Int): Duration {
        val shift = (failureCount - 1).coerceAtLeast(0)
        val exponential = if (shift >= 62) Long.MAX_VALUE else 1L shl shift
        val backoff = ITEM_FETCH_BASE_BACKOFF.multipliedBy(exponential)
        return if (backoff > ITEM_FETCH_MAX_BACKOFF) ITEM_FETCH_MAX_BACKOFF else backoff
    }

    private fun parseError(error: Throwable?): Pair<String?, String?> {
        val message = error?.message?.lineSequence()?.firstOrNull()?.trim()?.take(512)
        val status = message?.let { STATUS_REGEX.find(it)?.groupValues?.get(1) }
        return status to message
    }

    private companion object {
        val STATUS_REGEX = Regex("""^(\d{3})\b""")
    }
}
