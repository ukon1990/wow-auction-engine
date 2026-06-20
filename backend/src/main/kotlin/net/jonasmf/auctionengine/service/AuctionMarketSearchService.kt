package net.jonasmf.auctionengine.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.generated.model.AuctionListingKey
import net.jonasmf.auctionengine.generated.model.AuctionMarketFilter
import net.jonasmf.auctionengine.generated.model.AuctionMarketFilterOption
import net.jonasmf.auctionengine.generated.model.AuctionMarketFilterResponse
import net.jonasmf.auctionengine.generated.model.AuctionMarketItem
import net.jonasmf.auctionengine.generated.model.AuctionMarketMetrics
import net.jonasmf.auctionengine.generated.model.AuctionMarketNamedId
import net.jonasmf.auctionengine.generated.model.AuctionMarketRecipe
import net.jonasmf.auctionengine.generated.model.AuctionMarketSearchPage
import net.jonasmf.auctionengine.generated.model.AuctionMarketSearchRow
import net.jonasmf.auctionengine.generated.model.AuctionMarketSort
import net.jonasmf.auctionengine.generated.model.PageMetadata
import net.jonasmf.auctionengine.repository.rds.AuctionMarketSearchRepository
import net.jonasmf.auctionengine.repository.rds.AuctionMarketSearchRequest
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

private data class FiltersRepositoryRows(
    val qualityOptions: List<AuctionMarketFilterOption>,
    val itemClassOptions: List<AuctionMarketFilterOption>,
    val itemSubclassOptions: List<AuctionMarketFilterOption>,
)

private data class FiltersCacheKey(
    val regionCode: String,
    val realmSlug: String,
    val locale: Locale,
    val selectedConnectedRealmId: Int,
    val selectedDate: LocalDate,
    val selectedHour: Int,
    val commodityConnectedRealmId: Int,
    val commodityDate: LocalDate,
    val commodityHour: Int,
)

private data class CachedFiltersResponse(
    val response: AuctionMarketFilterResponse,
    val expiresAt: Instant,
)

@Service
class AuctionMarketSearchService(
    private val auctionMarketContextService: AuctionMarketContextService,
    private val auctionMarketSearchRepository: AuctionMarketSearchRepository,
) {
    private val log = LoggerFactory.getLogger(AuctionMarketSearchService::class.java)

    fun search(
        regionCode: String,
        realmSlug: String,
        localeOverride: String?,
        page: Int,
        pageSize: Int,
        sortBy: String,
        sortDirection: String,
        query: String?,
        qualityIds: List<Int>?,
        itemClassIds: List<Int>?,
        itemSubclassIds: List<Int>?,
        recipeOnly: Boolean?,
        minPrice: Long?,
        maxPrice: Long?,
        minQuantity: Long?,
        maxQuantity: Long?,
    ): AuctionMarketSearchPage {
        validateRange("price", minPrice, maxPrice)
        validateRange("quantity", minQuantity, maxQuantity)

        val totalStartNanos = System.nanoTime()
        val resolveContextStartNanos = System.nanoTime()
        val context = auctionMarketContextService.resolve(regionCode, realmSlug, localeOverride)
        val resolveContextMs = elapsedMs(resolveContextStartNanos)
        val normalizedPage = page.coerceAtLeast(0)
        val normalizedPageSize = pageSize.coerceIn(1, 100)
        val normalizedSortBy = allowedSorts.firstOrNull { it == sortBy } ?: "itemName"
        val normalizedSortDirection = if (sortDirection.equals("desc", ignoreCase = true)) "desc" else "asc"
        val request =
            AuctionMarketSearchRequest(
                selectedConnectedRealmId = context.selectedSnapshot.connectedRealmId,
                selectedDate = context.selectedSnapshot.date,
                selectedHour = context.selectedSnapshot.hour,
                commodityConnectedRealmId = context.commoditySnapshot.connectedRealmId,
                commodityDate = context.commoditySnapshot.date,
                commodityHour = context.commoditySnapshot.hour,
                localeColumnSuffix = context.localeColumnSuffix,
                page = normalizedPage,
                pageSize = normalizedPageSize,
                sortBy = normalizedSortBy,
                sortDirection = normalizedSortDirection,
                query = query,
                qualityIds = qualityIds.orEmpty().distinct(),
                itemClassIds = itemClassIds.orEmpty().distinct(),
                itemSubclassIds = itemSubclassIds.orEmpty().distinct(),
                recipeOnly = recipeOnly,
                minPrice = minPrice,
                maxPrice = maxPrice,
                minQuantity = minQuantity,
                maxQuantity = maxQuantity,
            )
        val repositoryStartNanos = System.nanoTime()
        val result = auctionMarketSearchRepository.search(request)
        val repositoryMs = elapsedMs(repositoryStartNanos)
        val totalPages =
            if (result.totalItems == 0L) {
                0
            } else {
                ((result.totalItems + normalizedPageSize - 1) / normalizedPageSize).toInt()
            }

        val mappingStartNanos = System.nanoTime()
        val response =
            AuctionMarketSearchPage(
                items =
                    result.rows.map { row ->
                        val preferredScope =
                            preferredScopeFor(
                                row.selectedPrice,
                                row.selectedQuantity,
                                row.commodityPrice,
                                row.commodityQuantity,
                            )
                        AuctionMarketSearchRow(
                            item =
                                AuctionMarketItem(
                                    id = row.itemId,
                                    name = row.itemName,
                                    mediaUrl = row.itemMediaUrl,
                                    quality =
                                        row.qualityId?.let {
                                            AuctionMarketNamedId(
                                                it,
                                                row.qualityName.orEmpty(),
                                                row.qualityType,
                                            )
                                        },
                                    itemClass =
                                        row.itemClassId?.let {
                                            AuctionMarketNamedId(
                                                it,
                                                row.itemClassName.orEmpty(),
                                            )
                                        },
                                    itemSubclass =
                                        row.itemSubclassId?.let {
                                            AuctionMarketNamedId(
                                                it,
                                                row.itemSubclassName.orEmpty(),
                                            )
                                        },
                                    recipe =
                                        row.recipeId?.let {
                                            AuctionMarketRecipe(
                                                id = it,
                                                name = row.recipeName.orEmpty(),
                                                mediaUrl = row.recipeMediaUrl,
                                            )
                                        },
                                ),
                            preferredScope = preferredScope,
                            listingPrice = row.selectedPrice ?: row.commodityPrice,
                            listingQuantity = row.selectedQuantity ?: row.commodityQuantity,
                            commodityOnly = preferredScope == "commodity",
                            listingKey =
                                AuctionListingKey(
                                    bonusKey = row.selectedBonusKey,
                                    modifierKey = row.selectedModifierKey,
                                    petSpeciesId = row.selectedPetSpeciesId,
                                ),
                            selectedRealm =
                                context.selectedSnapshot.toMetrics(
                                    price = row.selectedPrice,
                                    p25Price = row.selectedP25Price,
                                    p75Price = row.selectedP75Price,
                                    quantity = row.selectedQuantity,
                                ),
                            commodity =
                                context.commoditySnapshot.toMetrics(
                                    price = row.commodityPrice,
                                    p25Price = row.commodityP25Price,
                                    p75Price = row.commodityP75Price,
                                    quantity = row.commodityQuantity,
                                ),
                        )
                    },
                page =
                    PageMetadata(
                        page = normalizedPage,
                        pageSize = normalizedPageSize,
                        totalItems = result.totalItems,
                        totalPages = totalPages,
                    ),
                sort =
                    AuctionMarketSort(
                        sortBy = normalizedSortBy,
                        sortDirection = AuctionMarketSort.SortDirection.forValue(normalizedSortDirection),
                    ),
            )
        val mappingMs = elapsedMs(mappingStartNanos)
        log.info(
            "Auction market search service completed in {}ms (requestId={} resolveContext={}ms repository={}ms mapping={}ms region={} realmSlug={} page={} pageSize={} totalItems={} returnedRows={})",
            elapsedMs(totalStartNanos),
            requestId(),
            resolveContextMs,
            repositoryMs,
            mappingMs,
            regionCode,
            realmSlug,
            normalizedPage,
            normalizedPageSize,
            result.totalItems,
            result.rows.size,
        )
        return response
    }

    fun filters(
        regionCode: String,
        realmSlug: String,
        localeOverride: String?,
    ): AuctionMarketFilterResponse {
        val totalStartNanos = System.nanoTime()
        val resolveContextStartNanos = System.nanoTime()
        val context = auctionMarketContextService.resolve(regionCode, realmSlug, localeOverride)
        val resolveContextMs = elapsedMs(resolveContextStartNanos)
        val cacheKey = buildFiltersCacheKey(regionCode, realmSlug, context)
        val now = Instant.now()
        filtersCache[cacheKey]?.takeIf { now.isBefore(it.expiresAt) }?.let { cached ->
            log.info(
                "Auction market filters service cache hit in {}ms (requestId={} region={} realmSlug={} locale={})",
                elapsedMs(totalStartNanos),
                requestId(),
                regionCode,
                realmSlug,
                context.locale.value,
            )
            return cached.response
        }
        filtersCache.entries.removeIf { now.isAfter(it.value.expiresAt) }
        val request =
            AuctionMarketSearchRequest(
                selectedConnectedRealmId = context.selectedSnapshot.connectedRealmId,
                selectedDate = context.selectedSnapshot.date,
                selectedHour = context.selectedSnapshot.hour,
                commodityConnectedRealmId = context.commoditySnapshot.connectedRealmId,
                commodityDate = context.commoditySnapshot.date,
                commodityHour = context.commoditySnapshot.hour,
                localeColumnSuffix = context.localeColumnSuffix,
                page = 0,
                pageSize = 1,
                sortBy = "itemName",
                sortDirection = "asc",
                query = null,
                qualityIds = emptyList(),
                itemClassIds = emptyList(),
                itemSubclassIds = emptyList(),
                recipeOnly = null,
                minPrice = null,
                maxPrice = null,
                minQuantity = null,
                maxQuantity = null,
            )
        val mdcSnapshot = MDC.getCopyOfContextMap()
        val parallelStartNanos = System.nanoTime()
        val filterRows =
            runBlocking {
                coroutineScope {
                    val qualityDef =
                        async {
                            withAuctionMdc(mdcSnapshot) {
                                auctionMarketSearchRepository.qualityOptions(request).toDtoOptions()
                            }
                        }
                    val itemClassDef =
                        async {
                            withAuctionMdc(mdcSnapshot) {
                                auctionMarketSearchRepository.itemClassOptions(request).toDtoOptions()
                            }
                        }
                    val itemSubclassDef =
                        async {
                            withAuctionMdc(mdcSnapshot) {
                                auctionMarketSearchRepository.itemSubclassOptions(request).toDtoOptions()
                            }
                        }
                    FiltersRepositoryRows(
                        qualityOptions = qualityDef.await(),
                        itemClassOptions = itemClassDef.await(),
                        itemSubclassOptions = itemSubclassDef.await(),
                    )
                }
            }
        val qualityOptions = filterRows.qualityOptions
        val itemClassOptions = filterRows.itemClassOptions
        val itemSubclassOptions = filterRows.itemSubclassOptions
        val parallelFiltersMs = elapsedMs(parallelStartNanos)

        val response =
            AuctionMarketFilterResponse(
                filters =
                    listOf(
                        AuctionMarketFilter(
                            id = "price",
                            label = "Price",
                            type = AuctionMarketFilter.Type.RANGE,
                            min = null,
                            max = null,
                        ),
                        AuctionMarketFilter(
                            id = "quantity",
                            label = "Quantity",
                            type = AuctionMarketFilter.Type.RANGE,
                            min = null,
                            max = null,
                        ),
                        AuctionMarketFilter(
                            id = "qualityIds",
                            label = "Quality",
                            type = AuctionMarketFilter.Type.MULTI_SELECT,
                            options = qualityOptions,
                        ),
                        AuctionMarketFilter(
                            id = "itemClassIds",
                            label = "Item Class",
                            type = AuctionMarketFilter.Type.MULTI_SELECT,
                            options = itemClassOptions,
                        ),
                        AuctionMarketFilter(
                            id = "itemSubclassIds",
                            label = "Item Subclass",
                            type = AuctionMarketFilter.Type.MULTI_SELECT,
                            options = itemSubclassOptions,
                        ),
                        AuctionMarketFilter(
                            id = "recipeOnly",
                            label = "Has Recipe",
                            type = AuctionMarketFilter.Type.BOOLEAN,
                        ),
                    ),
            )
        log.info(
            "Auction market filters service completed in {}ms (requestId={} resolveContext={}ms parallelFilters={}ms region={} realmSlug={} qualityOptionsCount={} itemClassOptionsCount={} itemSubclassOptionsCount={})",
            elapsedMs(totalStartNanos),
            requestId(),
            resolveContextMs,
            parallelFiltersMs,
            regionCode,
            realmSlug,
            qualityOptions.size,
            itemClassOptions.size,
            itemSubclassOptions.size,
        )
        filtersCache[cacheKey] = CachedFiltersResponse(response, now.plus(filtersCacheTtl))
        return response
    }

    private fun validateRange(
        label: String,
        min: Long?,
        max: Long?,
    ) {
        if (min != null && max != null && min > max) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid $label range: min must be less than or equal to max",
            )
        }
    }

    private fun MarketSnapshot.toMetrics(
        price: Long?,
        p25Price: Long?,
        p75Price: Long?,
        quantity: Long?,
    ): AuctionMarketMetrics =
        AuctionMarketMetrics(
            connectedRealmId = connectedRealmId,
            timestamp = timestamp,
            date = date,
            hourOfDay = hour,
            price = price,
            p25Price = p25Price,
            p75Price = p75Price,
            quantity = quantity,
        )

    private fun List<net.jonasmf.auctionengine.repository.rds.AuctionMarketFilterOptionRow>.toDtoOptions():
        List<AuctionMarketFilterOption> =
        map {
            AuctionMarketFilterOption(
                id = it.id,
                label = it.label,
                parentId = it.parentId,
            )
        }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    private fun requestId(): String = MDC.get("requestId") ?: "-"

    private fun preferredScopeFor(
        selectedPrice: Long?,
        selectedQuantity: Long?,
        commodityPrice: Long?,
        commodityQuantity: Long?,
    ): String {
        val hasSelected = selectedPrice != null || selectedQuantity != null
        val hasCommodity = commodityPrice != null || commodityQuantity != null
        return if (!hasSelected && hasCommodity) "commodity" else "realm"
    }

    private fun buildFiltersCacheKey(
        regionCode: String,
        realmSlug: String,
        context: MarketContext,
    ): FiltersCacheKey =
        FiltersCacheKey(
            regionCode = regionCode.lowercase(),
            realmSlug = realmSlug.lowercase(),
            locale = context.locale,
            selectedConnectedRealmId = context.selectedSnapshot.connectedRealmId,
            selectedDate = context.selectedSnapshot.date,
            selectedHour = context.selectedSnapshot.hour,
            commodityConnectedRealmId = context.commoditySnapshot.connectedRealmId,
            commodityDate = context.commoditySnapshot.date,
            commodityHour = context.commoditySnapshot.hour,
        )

    private suspend fun <T> withAuctionMdc(
        mdc: Map<String, String>?,
        block: () -> T,
    ): T =
        withContext(Dispatchers.IO) {
            val previous = MDC.getCopyOfContextMap()
            try {
                if (mdc != null) {
                    MDC.setContextMap(mdc)
                } else {
                    MDC.clear()
                }
                block()
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous)
                } else {
                    MDC.clear()
                }
            }
        }

    companion object {
        private val filtersCacheTtl: Duration = Duration.ofSeconds(30)
        private val allowedSorts =
            setOf(
                "itemName",
                "quality",
                "itemClass",
                "itemSubclass",
                "selectedPrice",
                "commodityPrice",
                "selectedQuantity",
                "commodityQuantity",
            )
    }

    private val filtersCache: MutableMap<FiltersCacheKey, CachedFiltersResponse> = ConcurrentHashMap()
}
