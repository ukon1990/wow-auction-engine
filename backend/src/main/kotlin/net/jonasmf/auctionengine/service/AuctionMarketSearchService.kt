package net.jonasmf.auctionengine.service

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
import net.jonasmf.auctionengine.repository.rds.AuctionMarketSearchRepository
import net.jonasmf.auctionengine.repository.rds.AuctionMarketSearchRequest
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmRepository
import net.jonasmf.auctionengine.utility.resolveZone
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

@Service
class AuctionMarketSearchService(
    private val connectedRealmRepository: ConnectedRealmRepository,
    private val auctionMarketSearchRepository: AuctionMarketSearchRepository,
) {
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

        val context = resolveContext(regionCode, realmSlug, localeOverride)
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
        val result = auctionMarketSearchRepository.search(request)
        val totalPages =
            if (result.totalItems == 0L) {
                0
            } else {
                ((result.totalItems + normalizedPageSize - 1) / normalizedPageSize).toInt()
            }

        return AuctionMarketSearchPage(
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
    }

    fun filters(
        regionCode: String,
        realmSlug: String,
        localeOverride: String?,
    ): AuctionMarketFilterResponse {
        val context = resolveContext(regionCode, realmSlug, localeOverride)
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
        val range = auctionMarketSearchRepository.priceAndQuantityRange(request)
        return AuctionMarketFilterResponse(
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
                        options = auctionMarketSearchRepository.qualityOptions(request).toDtoOptions(),
                    ),
                    AuctionMarketFilter(
                        id = "itemClassIds",
                        label = "Item Class",
                        type = AuctionMarketFilter.Type.MULTI_SELECT,
                        options = auctionMarketSearchRepository.itemClassOptions(request).toDtoOptions(),
                    ),
                    AuctionMarketFilter(
                        id = "itemSubclassIds",
                        label = "Item Subclass",
                        type = AuctionMarketFilter.Type.MULTI_SELECT,
                        options = auctionMarketSearchRepository.itemSubclassOptions(request).toDtoOptions(),
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
