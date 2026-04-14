package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.item.Item
import net.jonasmf.auctionengine.integration.blizzard.ItemApiClient
import net.jonasmf.auctionengine.repository.rds.ItemJdbcRepository
import net.jonasmf.auctionengine.repository.rds.ItemPersistenceSummary
import net.jonasmf.auctionengine.repository.rds.RecipeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Clock
import java.time.LocalDate

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
    val persistenceSummary: ItemPersistenceSummary,
    val durationMs: Long,
)

@Service
class ItemSyncService(
    private val properties: BlizzardApiProperties,
    private val itemApiClient: ItemApiClient,
    private val itemJdbcRepository: ItemJdbcRepository,
    private val recipeRepository: RecipeRepository,
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

        val auctionSourceIds = itemJdbcRepository.findDistinctAuctionItemIdsForDate(today)
        val recipeCraftedIds =
            runCatching { recipeRepository.findDistinctCraftedItemIds() }
                .onFailure { error ->
                    log.warn(
                        "Recipe crafted item source unavailable for region {}: {}",
                        region,
                        error.message ?: "unknown",
                    )
                }.getOrDefault(emptyList())
        val recipeReagentIds =
            runCatching { recipeRepository.findDistinctReagentItemIds() }
                .onFailure { error ->
                    log.warn(
                        "Recipe reagent item source unavailable for region {}: {}",
                        region,
                        error.message ?: "unknown",
                    )
                }.getOrDefault(emptyList())

        val candidateIds = (auctionSourceIds + recipeCraftedIds + recipeReagentIds).distinct().sorted()
        val existingIds = itemJdbcRepository.findExistingItemIds(candidateIds)
        val missingIds = candidateIds.filterNot(existingIds::contains)

        log.info(
            "Discovered item sync sources region={} auction={} crafted={} reagents={} candidates={} existing={} missing={}",
            region,
            auctionSourceIds.size,
            recipeCraftedIds.size,
            recipeReagentIds.size,
            candidateIds.size,
            existingIds.size,
            missingIds.size,
        )

        val fetchOutcomes =
            Flux
                .fromIterable(missingIds)
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

        fetchOutcomes.filter { it.error != null }.forEach { failure ->
            log.warn(
                "Skipping item {} for region {} after fetch failure: {}",
                failure.itemId,
                region,
                failure.error?.message ?: failure.error?.javaClass?.simpleName ?: "unknown error",
            )
        }

        val fetchedItems = fetchOutcomes.mapNotNull(ItemFetchOutcome::item)
        val persistenceSummary = itemBulkSyncService.syncItems(fetchedItems)

        return ItemSyncResult(
            region = region,
            auctionSourceCount = auctionSourceIds.size,
            recipeCraftedSourceCount = recipeCraftedIds.size,
            recipeReagentSourceCount = recipeReagentIds.size,
            candidateItemCount = candidateIds.size,
            existingItemCount = existingIds.size,
            missingItemCount = missingIds.size,
            fetchedItemCount = fetchedItems.size,
            itemFetchFailures = fetchOutcomes.count { it.error != null },
            persistenceSummary = persistenceSummary,
            durationMs = System.currentTimeMillis() - startTime,
        ).also { result ->
            log.info(
                "Finished item sync for region {} in {}ms auction={} crafted={} reagents={} candidates={} existing={} missing={} fetched={} failed={} persistedItems={}",
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
                result.persistenceSummary.itemsUpserted,
            )
        }
    }
}
