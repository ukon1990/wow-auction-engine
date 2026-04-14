package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.item.Item
import net.jonasmf.auctionengine.integration.blizzard.ItemApiClient
import net.jonasmf.auctionengine.repository.rds.ItemJdbcRepository
import net.jonasmf.auctionengine.repository.rds.ItemPersistenceSummary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Clock
import java.time.LocalDate

private const val ITEM_FETCH_BATCH_SIZE = 100
private const val ITEM_FETCH_CONCURRENCY = 20

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
        val missingIds = discovery.missingItemIds

        log.info(
            "Discovered item sync sources region={} auction={} crafted={} reagents={} candidates={} existing={} missing={}",
            region,
            discovery.auctionSourceCount,
            discovery.recipeCraftedSourceCount,
            discovery.recipeReagentSourceCount,
            discovery.candidateItemCount,
            discovery.existingItemCount,
            missingIds.size,
        )

        val missingIdBatches = missingIds.chunked(ITEM_FETCH_BATCH_SIZE)
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
                missingIds.size,
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
                missingIds.size,
                batchSucceeded,
                batchFailed,
            )

            batchOutcomes.filter { it.error != null }.forEach { failure ->
                log.warn(
                    "Skipping item {} for region {} after fetch failure: {}",
                    failure.itemId,
                    region,
                    failure.error?.message ?: failure.error?.javaClass?.simpleName ?: "unknown error",
                )
            }

            val batchItems = batchOutcomes.mapNotNull(ItemFetchOutcome::item)
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
            auctionSourceCount = discovery.auctionSourceCount,
            recipeCraftedSourceCount = discovery.recipeCraftedSourceCount,
            recipeReagentSourceCount = discovery.recipeReagentSourceCount,
            candidateItemCount = discovery.candidateItemCount,
            existingItemCount = discovery.existingItemCount,
            missingItemCount = missingIds.size,
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
                "Finished item sync for region {} in {}ms auction={} crafted={} reagents={} candidates={} existing={} missing={} fetched={} failed={} persistedItems={} attemptedItemUpserts={}",
                result.region,
                result.durationMs,
                result.auctionSourceCount,
                result.recipeCraftedSourceCount,
                result.recipeReagentSourceCount,
                result.candidateItemCount,
                result.existingItemCount,
                result.missingItemCount,
                result.fetchedItemCount,
                result.itemFetchFailures,
                result.persistedItemCount,
                result.persistenceSummary.itemsUpserted,
            )
        }
    }
}
