package net.jonasmf.auctionengine.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.jonasmf.auctionengine.generated.model.AuctionListingKey
import net.jonasmf.auctionengine.generated.model.AuctionMarketItem
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemCrafting
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemCraftingAnalyticsPoint
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemCraftingAnalyticsResponse
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemCraftingDetail
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemCraftingHeatmapCell
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemCraftingReagent
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemCurrentListing
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemDetailPoint
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemDetailResponse
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemDetailSummary
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemHourlyPoint
import net.jonasmf.auctionengine.generated.model.AuctionMarketMetrics
import net.jonasmf.auctionengine.generated.model.AuctionMarketNamedId
import net.jonasmf.auctionengine.generated.model.AuctionMarketQuantityPieSlice
import net.jonasmf.auctionengine.generated.model.AuctionMarketRecipe
import net.jonasmf.auctionengine.generated.model.MarketDataSource
import net.jonasmf.auctionengine.repository.rds.AuctionMarketItemDetailDailyRow
import net.jonasmf.auctionengine.repository.rds.AuctionMarketItemDetailHourlyRow
import net.jonasmf.auctionengine.repository.rds.AuctionMarketItemDetailPieRow
import net.jonasmf.auctionengine.repository.rds.AuctionMarketItemDetailRepository
import net.jonasmf.auctionengine.repository.rds.AuctionMarketItemCraftingAnalyticsDailyRow
import net.jonasmf.auctionengine.repository.rds.AuctionMarketItemCraftingHeatmapRow
import net.jonasmf.auctionengine.repository.rds.AuctionMarketItemCraftingReagentRow
import net.jonasmf.auctionengine.repository.rds.AuctionMarketItemCraftingRow
import net.jonasmf.auctionengine.repository.rds.AuctionMarketItemCurrentListingRow
import net.jonasmf.auctionengine.repository.rds.AuctionMarketItemHeaderRow
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.time.ZoneOffset

private data class AuctionMarketItemDetailRepositoryRows(
    val header: AuctionMarketItemHeaderRow?,
    val dailyRealm: List<AuctionMarketItemDetailDailyRow>,
    val dailyCommodity: List<AuctionMarketItemDetailDailyRow>,
    val hourlyRealm: List<AuctionMarketItemDetailHourlyRow>,
    val hourlyCommodity: List<AuctionMarketItemDetailHourlyRow>,
    val pieRealm: List<AuctionMarketItemDetailPieRow>,
    val pieCommodity: List<AuctionMarketItemDetailPieRow>,
    val selectedSnapshot: Pair<Long?, Long?>,
    val commoditySnapshot: Pair<Long?, Long?>,
    val currentListings: List<AuctionMarketItemCurrentListingRow>,
    val craftingRows: List<AuctionMarketItemCraftingRow>,
    val reagentRows: Map<Int, List<AuctionMarketItemCraftingReagentRow>>,
)

@Service
class AuctionMarketItemDetailService(
    private val auctionMarketContextService: AuctionMarketContextService,
    private val detailRepository: AuctionMarketItemDetailRepository,
) {
    fun itemDetail(
        regionCode: String,
        realmSlug: String,
        itemId: Int,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
        scope: String,
        localeOverride: String?,
        preferredRecipeId: Int? = null,
    ): AuctionMarketItemDetailResponse {
        val context = auctionMarketContextService.resolve(regionCode, realmSlug, localeOverride)
        // Both 0 and -1 are used in our data flows to mean "no pet species" for non-pet items.
        // Treat both as rollup when bonus/modifier are empty so we do not over-filter hourly/daily
        // series by a synthetic pet id and accidentally return all-null commodity/realm series.
        val rollupListing = bonusKey.isEmpty() && modifierKey.isEmpty() && petSpeciesId <= 0
        val variant = !rollupListing
        val localeSuffix = context.localeColumnSuffix

        val listingKey = AuctionListingKey(bonusKey, modifierKey, petSpeciesId)
        val redundant =
            context.selectedSnapshot.connectedRealmId == context.commoditySnapshot.connectedRealmId
        val commodityScopeRequested = scope.equals("commodity", ignoreCase = true)
        val loadCommodity = commodityScopeRequested && !redundant

        val realmFrom = context.selectedSnapshot.date.minusDays(13)
        val realmTo = context.selectedSnapshot.date
        val commodityFrom = context.commoditySnapshot.date.minusDays(13)
        val commodityTo = context.commoditySnapshot.date

        val mdcSnapshot = MDC.getCopyOfContextMap()
        val repositoryRows =
            runBlocking {
                coroutineScope {
                    val headerDef =
                        async {
                            withAuctionMdc(mdcSnapshot) {
                                detailRepository.loadItemHeader(itemId, localeSuffix)
                            }
                        }
                    val dailyRealmDef =
                        async {
                            if (loadCommodity) {
                                emptyList()
                            } else {
                                withAuctionMdc(mdcSnapshot) {
                                    detailRepository.loadDailySeries(
                                        context.selectedSnapshot.connectedRealmId,
                                        itemId,
                                        realmFrom,
                                        realmTo,
                                        variant,
                                        bonusKey,
                                        modifierKey,
                                        petSpeciesId,
                                    )
                                }
                            }
                        }
                    val dailyCommodityDef =
                        async {
                            if (loadCommodity) {
                                withAuctionMdc(mdcSnapshot) {
                                    detailRepository.loadDailySeries(
                                        context.commoditySnapshot.connectedRealmId,
                                        itemId,
                                        commodityFrom,
                                        commodityTo,
                                        variant,
                                        bonusKey,
                                        modifierKey,
                                        petSpeciesId,
                                    )
                                }
                            } else {
                                emptyList()
                            }
                        }
                    val hourlyRealmDef =
                        async {
                            if (loadCommodity) {
                                emptyList()
                            } else {
                                withAuctionMdc(mdcSnapshot) {
                                    detailRepository.loadHourlySeries(
                                        context.selectedSnapshot.connectedRealmId,
                                        itemId,
                                        realmFrom,
                                        realmTo,
                                        variant,
                                        bonusKey,
                                        modifierKey,
                                        petSpeciesId,
                                    )
                                }
                            }
                        }
                    val hourlyCommodityDef =
                        async {
                            if (loadCommodity) {
                                withAuctionMdc(mdcSnapshot) {
                                    detailRepository.loadHourlySeries(
                                        context.commoditySnapshot.connectedRealmId,
                                        itemId,
                                        commodityFrom,
                                        commodityTo,
                                        variant,
                                        bonusKey,
                                        modifierKey,
                                        petSpeciesId,
                                    )
                                }
                            } else {
                                emptyList()
                            }
                        }
                    val pieRealmDef =
                        async {
                            if (loadCommodity) {
                                emptyList()
                            } else {
                                withAuctionMdc(mdcSnapshot) {
                                    detailRepository.loadQuantityPie(
                                        context.selectedSnapshot.connectedRealmId,
                                        itemId,
                                        context.selectedSnapshot.date,
                                        variant,
                                        bonusKey,
                                        modifierKey,
                                        petSpeciesId,
                                    )
                                }
                            }
                        }
                    val pieCommodityDef =
                        async {
                            if (loadCommodity) {
                                withAuctionMdc(mdcSnapshot) {
                                    detailRepository.loadQuantityPie(
                                        context.commoditySnapshot.connectedRealmId,
                                        itemId,
                                        context.commoditySnapshot.date,
                                        variant,
                                        bonusKey,
                                        modifierKey,
                                        petSpeciesId,
                                    )
                                }
                            } else {
                                emptyList()
                            }
                        }
                    val selectedSnapshotDef =
                        async {
                            if (loadCommodity) {
                                null to null
                            } else {
                                withAuctionMdc(mdcSnapshot) {
                                    detailRepository.loadSnapshotPriceQuantity(
                                        context.selectedSnapshot.connectedRealmId,
                                        itemId,
                                        context.selectedSnapshot.date,
                                        context.selectedSnapshot.hour,
                                        variant,
                                        bonusKey,
                                        modifierKey,
                                        petSpeciesId,
                                    )
                                }
                            }
                        }
                    val commoditySnapshotDef =
                        async {
                            if (loadCommodity) {
                                withAuctionMdc(mdcSnapshot) {
                                    detailRepository.loadSnapshotPriceQuantity(
                                        context.commoditySnapshot.connectedRealmId,
                                        itemId,
                                        context.commoditySnapshot.date,
                                        context.commoditySnapshot.hour,
                                        variant,
                                        bonusKey,
                                        modifierKey,
                                        petSpeciesId,
                                    )
                                }
                            } else {
                                null to null
                            }
                        }
                    val currentListingsDef =
                        async {
                            withAuctionMdc(mdcSnapshot) {
                                val snapshot =
                                    if (loadCommodity) {
                                        context.commoditySnapshot
                                    } else {
                                        context.selectedSnapshot
                                    }
                                detailRepository.loadCurrentListings(
                                    snapshot.connectedRealmId,
                                    itemId,
                                    variant,
                                    bonusKey,
                                    modifierKey,
                                    petSpeciesId,
                                )
                            }
                        }
                    val craftingDef =
                        async {
                            withAuctionMdc(mdcSnapshot) {
                                val craftingRows =
                                    detailRepository.loadCraftings(
                                        context.selectedSnapshot.connectedRealmId,
                                        context.commoditySnapshot.connectedRealmId,
                                        itemId,
                                        context.selectedSnapshot.date,
                                        context.commoditySnapshot.date,
                                        context.selectedSnapshot.hour,
                                        context.commoditySnapshot.hour,
                                        variant,
                                        bonusKey,
                                        modifierKey,
                                        petSpeciesId,
                                        preferredRecipeId,
                                        localeSuffix,
                                    )
                                val reagentRows =
                                    detailRepository
                                        .loadCraftingReagents(
                                            context.selectedSnapshot.connectedRealmId,
                                            context.commoditySnapshot.connectedRealmId,
                                            itemId,
                                            craftingRows.map { it.recipeId },
                                            context.selectedSnapshot.date,
                                            context.commoditySnapshot.date,
                                            context.selectedSnapshot.hour,
                                            context.commoditySnapshot.hour,
                                            localeSuffix,
                                        ).groupBy { it.recipeId }
                                craftingRows to reagentRows
                            }
                        }

                    val (craftingRows, reagentRows) = craftingDef.await()
                    AuctionMarketItemDetailRepositoryRows(
                        header = headerDef.await(),
                        dailyRealm = dailyRealmDef.await(),
                        dailyCommodity = dailyCommodityDef.await(),
                        hourlyRealm = hourlyRealmDef.await(),
                        hourlyCommodity = hourlyCommodityDef.await(),
                        pieRealm = pieRealmDef.await(),
                        pieCommodity = pieCommodityDef.await(),
                        selectedSnapshot = selectedSnapshotDef.await(),
                        commoditySnapshot = commoditySnapshotDef.await(),
                        currentListings = currentListingsDef.await(),
                        craftingRows = craftingRows,
                        reagentRows = reagentRows,
                    )
                }
            }

        val header =
            repositoryRows.header
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: $itemId")
        val dailyRealm = repositoryRows.dailyRealm.map { it.toDetailPoint() }
        val dailyCommodity = repositoryRows.dailyCommodity.map { it.toDetailPoint() }
        val hourlyRealm = repositoryRows.hourlyRealm.map { it.toHourlyPoint() }
        val hourlyCommodity = repositoryRows.hourlyCommodity.map { it.toHourlyPoint() }
        val pieRealm = repositoryRows.pieRealm.map { it.toPieSlice() }
        val pieCommodity = repositoryRows.pieCommodity.map { it.toPieSlice() }
        val (selPrice, selQty) = repositoryRows.selectedSnapshot
        val (comPrice, comQty) = repositoryRows.commoditySnapshot

        val selectedMetrics =
            AuctionMarketMetrics(
                connectedRealmId = context.selectedSnapshot.connectedRealmId,
                timestamp = context.selectedSnapshot.timestamp,
                date = context.selectedSnapshot.date,
                hourOfDay = context.selectedSnapshot.hour,
                price = selPrice,
                quantity = selQty,
            )

        val commodityMetrics =
            AuctionMarketMetrics(
                connectedRealmId = context.commoditySnapshot.connectedRealmId,
                timestamp = context.commoditySnapshot.timestamp,
                date = context.commoditySnapshot.date,
                hourOfDay = context.commoditySnapshot.hour,
                price = comPrice,
                quantity = comQty,
            )

        val summary =
            buildSummary(
                dailyRealm = dailyRealm,
                dailyCommodity = dailyCommodity,
                selectedRealmPrice = selPrice,
                selectedRealmQuantity = selQty,
                commodityPrice = comPrice,
                commodityQuantity = comQty,
                regionalMetricsRedundant = redundant,
            )

        val craftingRows = repositoryRows.craftingRows
        val reagentRows = repositoryRows.reagentRows
        val craftingDtos = craftingRows.map { it.toCraftingDetailDto(reagentRows[it.recipeId].orEmpty()) }
        val craftingDto = craftingRows.firstOrNull()?.toCraftingDto()

        return AuctionMarketItemDetailResponse(
            item = header.toAuctionMarketItem(),
            listingKey = listingKey,
            rollupListing = rollupListing,
            regionalMetricsRedundant = redundant,
            marketDataSources = marketDataSources(context, redundant, loadCommodity),
            selectedRealm = selectedMetrics,
            commodity = commodityMetrics,
            summary = summary,
            dailySeriesRealm = dailyRealm,
            dailySeriesCommodity = dailyCommodity,
            hourlySeriesRealm = hourlyRealm,
            hourlySeriesCommodity = hourlyCommodity,
            quantityPieRealm = pieRealm,
            quantityPieCommodity = pieCommodity,
            currentListings = repositoryRows.currentListings.map { it.toCurrentListingDto() },
            crafting = craftingDto,
            craftings = craftingDtos,
        )
    }

    private fun marketDataSources(
        context: MarketContext,
        redundant: Boolean,
        commodityScopeRequested: Boolean,
    ): List<MarketDataSource> {
        val selected =
            MarketDataSource(
                connectedRealmId = context.selectedSnapshot.connectedRealmId,
                auctionHouseLastModified =
                    OffsetDateTime.ofInstant(context.selectedAuctionHouseLastModified, ZoneOffset.UTC),
            )
        return if (redundant || !commodityScopeRequested) {
            listOf(selected)
        } else {
            val commodity =
                MarketDataSource(
                    connectedRealmId = context.commoditySnapshot.connectedRealmId,
                    auctionHouseLastModified =
                        OffsetDateTime.ofInstant(context.commodityAuctionHouseLastModified, ZoneOffset.UTC),
                )
            listOf(selected, commodity)
        }
    }

    private fun buildSummary(
        dailyRealm: List<AuctionMarketItemDetailPoint>,
        dailyCommodity: List<AuctionMarketItemDetailPoint>,
        selectedRealmPrice: Long?,
        selectedRealmQuantity: Long?,
        commodityPrice: Long?,
        commodityQuantity: Long?,
        regionalMetricsRedundant: Boolean,
    ): AuctionMarketItemDetailSummary {
        val realmPct = dayOverDayPercent(dailyRealm)
        val commodityPct =
            if (regionalMetricsRedundant) {
                realmPct
            } else {
                dayOverDayPercent(dailyCommodity)
            }
        val realmVsCommodity =
            if (regionalMetricsRedundant || commodityPrice == null || commodityPrice == 0L) {
                null
            } else if (selectedRealmPrice == null) {
                null
            } else {
                100.0 * (selectedRealmPrice - commodityPrice) / commodityPrice.toDouble()
            }
        return AuctionMarketItemDetailSummary(
            selectedRealmPrice = selectedRealmPrice,
            selectedRealmQuantity = selectedRealmQuantity,
            commodityPrice = commodityPrice,
            commodityQuantity = commodityQuantity,
            selectedRealmPriceChangePercent = realmPct,
            commodityPriceChangePercent = commodityPct,
            realmVsCommodityPricePercent = realmVsCommodity,
        )
    }

    private fun dayOverDayPercent(daily: List<AuctionMarketItemDetailPoint>): Double? {
        val withAvg = daily.mapNotNull { p -> p.avgPrice?.let { p to it } }
        if (withAvg.size < 2) return null
        val prev = withAvg[withAvg.size - 2].second
        val cur = withAvg.last().second
        if (prev == 0.0) return null
        return 100.0 * (cur - prev) / prev
    }

    private fun AuctionMarketItemHeaderRow.toAuctionMarketItem(): AuctionMarketItem =
        AuctionMarketItem(
            id = itemId,
            name = itemName,
            mediaUrl = itemMediaUrl,
            quality =
                qualityId?.let {
                    AuctionMarketNamedId(
                        it,
                        qualityName.orEmpty(),
                        qualityType,
                    )
                },
            itemClass =
                itemClassId?.let {
                    AuctionMarketNamedId(
                        it,
                        itemClassName.orEmpty(),
                    )
                },
            itemSubclass =
                itemSubclassId?.let {
                    AuctionMarketNamedId(
                        it,
                        itemSubclassName.orEmpty(),
                    )
                },
            recipe =
                recipeId?.let {
                    AuctionMarketRecipe(
                        id = it,
                        name = recipeName.orEmpty(),
                        mediaUrl = recipeMediaUrl,
                        rank = recipeRank,
                    )
                },
        )

    private fun AuctionMarketItemDetailDailyRow.toDetailPoint(): AuctionMarketItemDetailPoint =
        AuctionMarketItemDetailPoint(
            statDate = statDate,
            pointTimestamp = pointTimestamp,
            minPrice = minPrice,
            avgPrice = avgPrice,
            p25Price = p25Price,
            p75Price = p75Price,
            maxPrice = maxPrice,
            minQuantity = minQuantity,
            avgQuantity = avgQuantity,
            maxQuantity = maxQuantity,
        )

    private fun AuctionMarketItemDetailHourlyRow.toHourlyPoint(): AuctionMarketItemHourlyPoint =
        AuctionMarketItemHourlyPoint(
            timestamp = timestamp,
            hourOfDay = hourOfDay,
            minPrice = minPrice,
            avgPrice = avgPrice,
            p25Price = p25Price,
            p75Price = p75Price,
            maxPrice = maxPrice,
            totalQuantity = totalQuantity,
        )

    private fun AuctionMarketItemDetailPieRow.toPieSlice(): AuctionMarketQuantityPieSlice =
        AuctionMarketQuantityPieSlice(
            hourOfDay = hourOfDay,
            fraction = fraction,
            quantity = quantity,
        )

    private fun AuctionMarketItemCurrentListingRow.toCurrentListingDto(): AuctionMarketItemCurrentListing =
        AuctionMarketItemCurrentListing(
            price = price,
            quantity = quantity,
        )

    fun craftingAnalytics(
        regionCode: String,
        realmSlug: String,
        itemId: Int,
        recipeId: Int,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
        localeOverride: String?,
    ): AuctionMarketItemCraftingAnalyticsResponse {
        val context = auctionMarketContextService.resolve(regionCode, realmSlug, localeOverride)
        if (!detailRepository.recipeProducesItem(recipeId = recipeId, itemId = itemId)) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No recipe with id=$recipeId produces item with id=$itemId",
            )
        }
        val rollupListing = bonusKey.isEmpty() && modifierKey.isEmpty() && petSpeciesId <= 0
        val variant = !rollupListing
        val from = context.selectedSnapshot.date.minusDays(13)
        val to = context.selectedSnapshot.date
        val daily =
            detailRepository.loadCraftingAnalyticsDaily(
                context.selectedSnapshot.connectedRealmId,
                context.commoditySnapshot.connectedRealmId,
                itemId,
                recipeId,
                from,
                to,
                context.selectedSnapshot.hour,
                context.commoditySnapshot.hour,
                variant,
                bonusKey,
                modifierKey,
                petSpeciesId,
            )
        val heatmap =
            detailRepository.loadCraftingAnalyticsHeatmap(
                context.selectedSnapshot.connectedRealmId,
                context.commoditySnapshot.connectedRealmId,
                itemId,
                recipeId,
                from,
                to,
                variant,
                bonusKey,
                modifierKey,
                petSpeciesId,
            )
        return AuctionMarketItemCraftingAnalyticsResponse(
            dailySeries = daily.map { it.toAnalyticsPoint() },
            heatmap = heatmap.map { it.toHeatmapCell() },
        )
    }

    /**
     * Builds the deprecated single-recipe `crafting` summary that legacy clients still consume.
     * Mirrors the pre-recipe-search behavior of returning `null` when the row lacks the basic
     * economics (no reagent cost or no listed output), so that clients which keep `crafting`
     * around for a release window do not see a populated-but-incomplete object where they
     * previously saw `null`.
     */
    private fun AuctionMarketItemCraftingRow.toCraftingDto(): AuctionMarketItemCrafting? {
        if (reagentCost == null || outputUnitPrice == null) return null
        return AuctionMarketItemCrafting(
            recipeId = recipeId,
            recipeRank = recipeRank,
            recipeName = recipeName,
            reagentCost = reagentCost,
            buyout = outputUnitPrice,
            profit = profit,
            roiPercent = roiPercent,
        )
    }

    private fun AuctionMarketItemCraftingRow.toCraftingDetailDto(reagents: List<AuctionMarketItemCraftingReagentRow>): AuctionMarketItemCraftingDetail =
        AuctionMarketItemCraftingDetail(
            recipeId = recipeId,
            recipeRank = recipeRank,
            recipeName = recipeName,
            recipeMediaUrl = recipeMediaUrl,
            craftedQuantity = craftedQuantity,
            reagents = reagents.map { it.toReagentDto() },
            reagentCost = reagentCost,
            reagentsFullyPriced = reagentsFullyPriced,
            outputUnitPrice = outputUnitPrice,
            buyout = outputUnitPrice,
            profit = profit,
            roiPercent = roiPercent,
        )

    private fun AuctionMarketItemCraftingReagentRow.toReagentDto(): AuctionMarketItemCraftingReagent =
        AuctionMarketItemCraftingReagent(
            itemId = itemId,
            name = name,
            mediaUrl = mediaUrl,
            quantity = quantity,
            unitPrice = unitPrice,
            lineTotal = lineTotal,
            priced = unitPrice != null,
            purchaseRank = purchaseRank,
        )

    private fun AuctionMarketItemCraftingAnalyticsDailyRow.toAnalyticsPoint(): AuctionMarketItemCraftingAnalyticsPoint =
        AuctionMarketItemCraftingAnalyticsPoint(
            statDate = statDate,
            profit = profit,
            roiPercent = roiPercent,
            reagentCost = reagentCost,
            outputUnitPrice = outputUnitPrice,
        )

    private fun AuctionMarketItemCraftingHeatmapRow.toHeatmapCell(): AuctionMarketItemCraftingHeatmapCell =
        AuctionMarketItemCraftingHeatmapCell(
            dayOfWeek = dayOfWeek,
            hourOfDay = hourOfDay,
            profit = profit,
            outputUnitPrice = outputUnitPrice,
            roiPercent = roiPercent,
            sampleCount = sampleCount,
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
}
