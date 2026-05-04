package net.jonasmf.auctionengine.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.Realm
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
import net.jonasmf.auctionengine.repository.rds.AuctionMarketRange
import net.jonasmf.auctionengine.repository.rds.AuctionMarketSearchRepository
import net.jonasmf.auctionengine.repository.rds.AuctionMarketSearchRequest
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmRepository
import net.jonasmf.auctionengine.utility.resolveZone
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

private data class MarketContext(
    val selectedConnectedRealm: ConnectedRealm,
    val selectedRealm: Realm,
    val communityConnectedRealm: ConnectedRealm,
    val locale: Locale,
    val localeColumnSuffix: String,
    val selectedSnapshot: MarketSnapshot,
    val communitySnapshot: MarketSnapshot,
)

private data class MarketSnapshot(
    val connectedRealmId: Int,
    val date: LocalDate,
    val hour: Int,
    val timestamp: OffsetDateTime,
)

private data class FiltersRepositoryRows(
    val range: AuctionMarketRange,
    val qualityOptions: List<AuctionMarketFilterOption>,
    val itemClassOptions: List<AuctionMarketFilterOption>,
    val itemSubclassOptions: List<AuctionMarketFilterOption>,
)

@Service
class AuctionMarketSearchService(
    private val connectedRealmRepository: ConnectedRealmRepository,
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
        val context = resolveContext(regionCode, realmSlug, localeOverride)
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
                communityConnectedRealmId = context.communitySnapshot.connectedRealmId,
                communityDate = context.communitySnapshot.date,
                communityHour = context.communitySnapshot.hour,
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
                            selectedRealm =
                                context.selectedSnapshot.toMetrics(
                                    price = row.selectedPrice,
                                    quantity = row.selectedQuantity,
                                ),
                            community =
                                context.communitySnapshot.toMetrics(
                                    price = row.communityPrice,
                                    quantity = row.communityQuantity,
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
        val context = resolveContext(regionCode, realmSlug, localeOverride)
        val resolveContextMs = elapsedMs(resolveContextStartNanos)
        val request =
            AuctionMarketSearchRequest(
                selectedConnectedRealmId = context.selectedSnapshot.connectedRealmId,
                selectedDate = context.selectedSnapshot.date,
                selectedHour = context.selectedSnapshot.hour,
                communityConnectedRealmId = context.communitySnapshot.connectedRealmId,
                communityDate = context.communitySnapshot.date,
                communityHour = context.communitySnapshot.hour,
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
                    val rangeDef =
                        async {
                            withAuctionMdc(mdcSnapshot) {
                                auctionMarketSearchRepository.priceAndQuantityRange(request)
                            }
                        }
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
                        range = rangeDef.await(),
                        qualityOptions = qualityDef.await(),
                        itemClassOptions = itemClassDef.await(),
                        itemSubclassOptions = itemSubclassDef.await(),
                    )
                }
            }
        val range = filterRows.range
        val qualityOptions = filterRows.qualityOptions
        val itemClassOptions = filterRows.itemClassOptions
        val itemSubclassOptions = filterRows.itemSubclassOptions
        val parallelFiltersMs = elapsedMs(parallelStartNanos)

        val response =
            AuctionMarketFilterResponse(
                filters =
                    listOf(
                        AuctionMarketFilter(
                            id = "query",
                            label = "Search",
                            type = AuctionMarketFilter.Type.TEXT,
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
                        AuctionMarketFilter(
                            id = "price",
                            label = "Price",
                            type = AuctionMarketFilter.Type.RANGE,
                            min = range.minPrice,
                            max = range.maxPrice,
                        ),
                        AuctionMarketFilter(
                            id = "quantity",
                            label = "Quantity",
                            type = AuctionMarketFilter.Type.RANGE,
                            min = range.minQuantity,
                            max = range.maxQuantity,
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
        return response
    }

    private fun resolveContext(
        regionCode: String,
        realmSlug: String,
        localeOverride: String?,
    ): MarketContext {
        val region =
            runCatching { Region.fromString(regionCode) }
                .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.message, it) }
        val (selectedConnectedRealm, selectedRealm) =
            connectedRealmRepository
                .findAllByRegion(region)
                .firstNotNullOfOrNull { connectedRealm ->
                    connectedRealm.realms
                        .firstOrNull { it.slug.equals(realmSlug, ignoreCase = true) }
                        ?.let { connectedRealm to it }
                } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm not found: $regionCode/$realmSlug")
        val communityConnectedRealm =
            connectedRealmRepository
                .findById(CommunityRealms.idFor(region))
                .orElseThrow {
                    ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Community realm not found for $regionCode",
                    )
                }
        val locale = localeOverride?.takeIf { it.isNotBlank() }?.parseLocale() ?: selectedRealm.locale

        return MarketContext(
            selectedConnectedRealm = selectedConnectedRealm,
            selectedRealm = selectedRealm,
            communityConnectedRealm = communityConnectedRealm,
            locale = locale,
            localeColumnSuffix = locale.columnSuffix,
            selectedSnapshot = selectedConnectedRealm.snapshotFor(selectedRealm.timezone),
            communitySnapshot = communityConnectedRealm.snapshotFor(null),
        )
    }

    private fun ConnectedRealm.snapshotFor(preferredTimezone: String?): MarketSnapshot {
        val lastModified =
            auctionHouse.lastModified
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Auction house ${auctionHouse.connectedId} has no last modified timestamp",
                )
        val zone = preferredTimezone?.toZoneIdOrNull() ?: resolveZone()
        val local = lastModified.atZone(zone)
        return MarketSnapshot(
            connectedRealmId = id,
            date = local.toLocalDate(),
            hour = local.hour,
            timestamp = local.toOffsetDateTime(),
        )
    }

    private fun String.toZoneIdOrNull(): ZoneId? = runCatching { ZoneId.of(this) }.getOrNull()

    private fun String.parseLocale(): Locale =
        runCatching { Locale.getAllValues().getValue(this) }
            .recoverCatching { Locale.fromCompactString(this) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported locale: $this", it) }

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
        quantity: Long?,
    ): AuctionMarketMetrics =
        AuctionMarketMetrics(
            connectedRealmId = connectedRealmId,
            timestamp = timestamp,
            date = date,
            hourOfDay = hour,
            price = price,
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

    private val Locale.columnSuffix: String
        get() = value.lowercase()

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    private fun requestId(): String = MDC.get("requestId") ?: "-"

    private suspend fun <T> withAuctionMdc(mdc: Map<String, String>?, block: () -> T): T =
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
        private val allowedSorts =
            setOf(
                "itemName",
                "quality",
                "itemClass",
                "itemSubclass",
                "selectedPrice",
                "communityPrice",
                "selectedQuantity",
                "communityQuantity",
            )
    }
}
