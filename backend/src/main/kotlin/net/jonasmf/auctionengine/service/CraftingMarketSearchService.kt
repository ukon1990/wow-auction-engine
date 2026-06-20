package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.generated.model.AuctionListingKey
import net.jonasmf.auctionengine.generated.model.AuctionMarketFilter
import net.jonasmf.auctionengine.generated.model.AuctionMarketFilterOption
import net.jonasmf.auctionengine.generated.model.AuctionMarketFilterResponse
import net.jonasmf.auctionengine.generated.model.AuctionMarketItem
import net.jonasmf.auctionengine.generated.model.AuctionMarketNamedId
import net.jonasmf.auctionengine.generated.model.AuctionMarketRecipe
import net.jonasmf.auctionengine.generated.model.AuctionMarketSort
import net.jonasmf.auctionengine.generated.model.CraftingMarketSearchPage
import net.jonasmf.auctionengine.generated.model.CraftingMarketSearchRow
import net.jonasmf.auctionengine.generated.model.PageMetadata
import net.jonasmf.auctionengine.repository.rds.CraftingMarketSearchRepository
import net.jonasmf.auctionengine.repository.rds.CraftingMarketSearchRequest
import net.jonasmf.auctionengine.repository.rds.CraftingMarketSqlRow
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class CraftingMarketSearchService(
    private val auctionMarketContextService: AuctionMarketContextService,
    private val craftingMarketSearchRepository: CraftingMarketSearchRepository,
) {
    private val allowedSorts =
        setOf(
            "itemName",
            "recipeName",
            "professionName",
            "reagentCost",
            "outputPrice",
            "profit",
            "roiPercent",
            "outputPriceChangePercent",
            "profitChangePercent",
            "listingQuantity",
        )

    fun search(
        regionCode: String,
        realmSlug: String,
        localeOverride: String?,
        page: Int,
        pageSize: Int,
        sortBy: String,
        sortDirection: String,
        query: String?,
        professionIds: List<Int>?,
        minProfit: Long?,
        maxProfit: Long?,
        minRoiPercent: Double?,
        maxRoiPercent: Double?,
        minReagentCost: Long?,
        maxReagentCost: Long?,
        minOutputPrice: Long?,
        maxOutputPrice: Long?,
        minOutputPriceChangePercent: Double?,
        maxOutputPriceChangePercent: Double?,
        requireCompleteReagentPricing: Boolean,
    ): CraftingMarketSearchPage {
        validateLongRange("profit", minProfit, maxProfit)
        validateLongRange("reagentCost", minReagentCost, maxReagentCost)
        validateLongRange("outputPrice", minOutputPrice, maxOutputPrice)
        validateDoubleRange("roiPercent", minRoiPercent, maxRoiPercent)
        validateDoubleRange("outputPriceChangePercent", minOutputPriceChangePercent, maxOutputPriceChangePercent)

        val context = auctionMarketContextService.resolve(regionCode, realmSlug, localeOverride)
        val normalizedPage = page.coerceAtLeast(0)
        val normalizedPageSize = pageSize.coerceIn(1, 100)
        val normalizedSortBy = allowedSorts.firstOrNull { it == sortBy } ?: "itemName"
        val normalizedSortDirection = if (sortDirection.equals("desc", ignoreCase = true)) "desc" else "asc"
        val previousDate = context.selectedSnapshot.date.minusDays(1)
        val commodityPreviousDate = context.commoditySnapshot.date.minusDays(1)
        val request =
            CraftingMarketSearchRequest(
                selectedConnectedRealmId = context.selectedSnapshot.connectedRealmId,
                selectedDate = context.selectedSnapshot.date,
                selectedHour = context.selectedSnapshot.hour,
                commodityConnectedRealmId = context.commoditySnapshot.connectedRealmId,
                commodityDate = context.commoditySnapshot.date,
                commodityHour = context.commoditySnapshot.hour,
                previousDate = previousDate,
                commodityPreviousDate = commodityPreviousDate,
                localeColumnSuffix = context.localeColumnSuffix,
                page = normalizedPage,
                pageSize = normalizedPageSize,
                sortBy = normalizedSortBy,
                sortDirection = normalizedSortDirection,
                query = query,
                professionIds = professionIds.orEmpty().distinct(),
                minProfit = minProfit,
                maxProfit = maxProfit,
                minRoiPercent = minRoiPercent,
                maxRoiPercent = maxRoiPercent,
                minReagentCost = minReagentCost,
                maxReagentCost = maxReagentCost,
                minOutputPrice = minOutputPrice,
                maxOutputPrice = maxOutputPrice,
                minOutputPriceChangePercent = minOutputPriceChangePercent,
                maxOutputPriceChangePercent = maxOutputPriceChangePercent,
                requireCompleteReagentPricing = requireCompleteReagentPricing,
            )
        val result = craftingMarketSearchRepository.search(request)
        val totalPages =
            if (result.totalItems == 0L) {
                0
            } else {
                ((result.totalItems + normalizedPageSize - 1) / normalizedPageSize).toInt()
            }
        return CraftingMarketSearchPage(
            items =
                result.rows.map { row ->
                    CraftingMarketSearchRow(
                        rowId = buildRowId(row),
                        recipeId = row.recipeId,
                        recipe =
                            AuctionMarketRecipe(
                                id = row.recipeId,
                                name = row.recipeName.orEmpty(),
                                mediaUrl = row.recipeMediaUrl,
                            ),
                        item =
                            AuctionMarketItem(
                                id = row.craftedItemId,
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
                                    AuctionMarketRecipe(
                                        id = row.recipeId,
                                        name = row.recipeName.orEmpty(),
                                        mediaUrl = row.recipeMediaUrl,
                                    ),
                            ),
                        listingKey =
                            AuctionListingKey(
                                bonusKey = row.bonusKey,
                                modifierKey = row.modifierKey,
                                petSpeciesId = row.petSpeciesId,
                            ),
                        professionId = row.professionId,
                        professionName = row.professionName,
                        skillTierName = row.skillTierName,
                        professionCategoryName = row.professionCategoryName,
                        craftedQuantity = row.craftedQuantity,
                        listingQuantity = row.listingQuantity,
                        reagentCostCopper = row.reagentCost,
                        outputPriceCopper = row.outputUnitPrice,
                        outputP25PriceCopper = row.outputP25Price,
                        outputP75PriceCopper = row.outputP75Price,
                        profitCopper = row.profitCopper,
                        roiPercent = row.roiPercent,
                        outputPriceChangePercent = row.outputPriceChangePercent,
                        profitChangePercent = row.profitChangePercent,
                        reagentsFullyPriced = row.reagentsFullyPriced,
                        outputPriced = row.outputUnitPrice != null,
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
        val context = auctionMarketContextService.resolve(regionCode, realmSlug, localeOverride)
        val options =
            craftingMarketSearchRepository
                .professionOptions(context.localeColumnSuffix)
                .map {
                    AuctionMarketFilterOption(
                        id = it.id,
                        label = it.label,
                        parentId = it.parentId,
                    )
                }
        return AuctionMarketFilterResponse(
            filters =
                listOf(
                    AuctionMarketFilter(
                        id = "professionIds",
                        label = "Profession",
                        type = AuctionMarketFilter.Type.MULTI_SELECT,
                        options = options,
                    ),
                    AuctionMarketFilter(
                        id = "profit",
                        label = "Profit",
                        type = AuctionMarketFilter.Type.RANGE,
                        min = null,
                        max = null,
                    ),
                    AuctionMarketFilter(
                        id = "roiPercent",
                        label = "ROI %",
                        type = AuctionMarketFilter.Type.RANGE,
                        min = null,
                        max = null,
                    ),
                    AuctionMarketFilter(
                        id = "reagentCost",
                        label = "Reagent cost",
                        type = AuctionMarketFilter.Type.RANGE,
                        min = null,
                        max = null,
                    ),
                    AuctionMarketFilter(
                        id = "outputPrice",
                        label = "Output price",
                        type = AuctionMarketFilter.Type.RANGE,
                        min = null,
                        max = null,
                    ),
                    AuctionMarketFilter(
                        id = "outputPriceChangePercent",
                        label = "Price trend %",
                        type = AuctionMarketFilter.Type.RANGE,
                        min = null,
                        max = null,
                    ),
                    AuctionMarketFilter(
                        id = "requireCompleteReagentPricing",
                        label = "Complete reagent pricing only",
                        type = AuctionMarketFilter.Type.BOOLEAN,
                    ),
                ),
        )
    }

    private fun buildRowId(row: CraftingMarketSqlRow): String =
        "${row.recipeId}|${row.bonusKey}|${row.modifierKey}|${row.petSpeciesId}"

    private fun validateLongRange(
        label: String,
        min: Long?,
        max: Long?,
    ) {
        if (min != null && max != null && min > max) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$label min must be <= max")
        }
    }

    private fun validateDoubleRange(
        label: String,
        min: Double?,
        max: Double?,
    ) {
        if (min != null && max != null && min > max) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$label min must be <= max")
        }
    }
}
