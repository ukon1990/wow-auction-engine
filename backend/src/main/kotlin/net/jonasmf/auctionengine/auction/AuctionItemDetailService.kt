package net.jonasmf.auctionengine.auction

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
import net.jonasmf.auctionengine.auction.repository.AuctionItemDetailDailyRow
import net.jonasmf.auctionengine.auction.repository.AuctionItemDetailHourlyRow
import net.jonasmf.auctionengine.auction.repository.AuctionItemDetailPieRow
import net.jonasmf.auctionengine.auction.repository.AuctionItemDetailRepository
import net.jonasmf.auctionengine.auction.repository.AuctionItemCraftingAnalyticsDailyRow
import net.jonasmf.auctionengine.auction.repository.AuctionItemCraftingHeatmapRow
import net.jonasmf.auctionengine.auction.repository.AuctionItemCraftingReagentRow
import net.jonasmf.auctionengine.auction.repository.AuctionItemCraftingRow
import net.jonasmf.auctionengine.auction.repository.AuctionItemCurrentListingRow
import net.jonasmf.auctionengine.auction.repository.AuctionItemHeaderRow
import net.jonasmf.auctionengine.auction.repository.loadItemHeader
import net.jonasmf.auctionengine.auction.repository.loadSnapshotPriceQuantity
import net.jonasmf.auctionengine.auction.repository.recipeProducesItem
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.time.ZoneOffset

private data class AuctionItemDetailRepositoryRows(
    val header: AuctionItemHeaderRow?,
    val dailyRealm: List<AuctionItemDetailDailyRow>,
    val dailyCommodity: List<AuctionItemDetailDailyRow>,
    val hourlyRealm: List<AuctionItemDetailHourlyRow>,
    val hourlyCommodity: List<AuctionItemDetailHourlyRow>,
    val pieRealm: List<AuctionItemDetailPieRow>,
    val pieCommodity: List<AuctionItemDetailPieRow>,
    val selectedSnapshot: Pair<Long?, Long?>,
    val commoditySnapshot: Pair<Long?, Long?>,
    val currentListings: List<AuctionItemCurrentListingRow>,
    val craftingRows: List<AuctionItemCraftingRow>,
    val reagentRows: Map<Int, List<AuctionItemCraftingReagentRow>>,
)

private data class PriceSeriesRows(
    val dailyRealm: List<AuctionItemDetailDailyRow>,
    val dailyCommodity: List<AuctionItemDetailDailyRow>,
    val hourlyRealm: List<AuctionItemDetailHourlyRow>,
    val hourlyCommodity: List<AuctionItemDetailHourlyRow>,
    val pieRealm: List<AuctionItemDetailPieRow>,
    val pieCommodity: List<AuctionItemDetailPieRow>,
)

private data class DetailLoadParameters(
    val context: AuctionContext,
    val itemId: Int,
    val variant: Boolean,
    val bonusKey: String,
    val modifierKey: String,
    val petSpeciesId: Int,
    val loadCommodity: Boolean,
    val preferredRecipeId: Int?,
    val mdcSnapshot: Map<String, String>?,
)

@Service
class AuctionItemDetailService(
    private val auctionContextService: AuctionContextService,
    private val detailRepository: AuctionItemDetailRepository,
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
        val context = auctionContextService.resolve(regionCode, realmSlug, localeOverride)
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

        val repositoryRows =
            loadRepositoryRows(
                DetailLoadParameters(
                    context, itemId, variant, bonusKey, modifierKey, petSpeciesId, loadCommodity,
                    preferredRecipeId, MDC.getCopyOfContextMap(),
                ),
            )

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
            saleRate = header.saleRate,
            soldPerDay = header.soldPerDay,
            craftedByRecipeCount = header.craftedByRecipeCount,
            reagentInRecipeCount = header.reagentInRecipeCount,
        )
    }

    private fun loadRepositoryRows(parameters: DetailLoadParameters): AuctionItemDetailRepositoryRows =
        runBlocking {
            coroutineScope {
                val header = async { withAuctionMdc(parameters.mdcSnapshot) { loadHeader(parameters) } }
                val series = async { loadPriceSeries(parameters) }
                val snapshots = async { withAuctionMdc(parameters.mdcSnapshot) { loadSnapshotMetrics(parameters) } }
                val listings = async { withAuctionMdc(parameters.mdcSnapshot) { loadCurrentListings(parameters) } }
                val crafting = async { withAuctionMdc(parameters.mdcSnapshot) { loadCraftingRows(parameters) } }
                val loadedSeries = series.await()
                val loadedSnapshots = snapshots.await()
                val (craftingRows, reagentRows) = crafting.await()
                AuctionItemDetailRepositoryRows(
                    header = header.await(),
                    dailyRealm = loadedSeries.dailyRealm,
                    dailyCommodity = loadedSeries.dailyCommodity,
                    hourlyRealm = loadedSeries.hourlyRealm,
                    hourlyCommodity = loadedSeries.hourlyCommodity,
                    pieRealm = loadedSeries.pieRealm,
                    pieCommodity = loadedSeries.pieCommodity,
                    selectedSnapshot = loadedSnapshots.first,
                    commoditySnapshot = loadedSnapshots.second,
                    currentListings = listings.await(),
                    craftingRows = craftingRows,
                    reagentRows = reagentRows,
                )
            }
        }

    private fun loadHeader(parameters: DetailLoadParameters): AuctionItemHeaderRow? =
        detailRepository.loadItemHeader(parameters.itemId, parameters.context.localeColumnSuffix, parameters.context.region)

    private suspend fun loadPriceSeries(parameters: DetailLoadParameters): PriceSeriesRows =
        coroutineScope {
            val snapshot = if (parameters.loadCommodity) parameters.context.commoditySnapshot else parameters.context.selectedSnapshot
            val fromDate = snapshot.date.minusDays(13)
            val daily = async { withAuctionMdc(parameters.mdcSnapshot) { loadDailySeries(parameters, snapshot.connectedRealmId, fromDate, snapshot.date) } }
            val hourly = async { withAuctionMdc(parameters.mdcSnapshot) { loadHourlySeries(parameters, snapshot.connectedRealmId, fromDate, snapshot.date) } }
            val pie = async { withAuctionMdc(parameters.mdcSnapshot) { loadQuantityPie(parameters, snapshot.connectedRealmId, snapshot.date) } }
            if (parameters.loadCommodity) {
                PriceSeriesRows(emptyList(), daily.await(), emptyList(), hourly.await(), emptyList(), pie.await())
            } else {
                PriceSeriesRows(daily.await(), emptyList(), hourly.await(), emptyList(), pie.await(), emptyList())
            }
        }

    private fun loadDailySeries(parameters: DetailLoadParameters, connectedRealmId: Int, fromDate: java.time.LocalDate, toDate: java.time.LocalDate) =
        detailRepository.loadDailySeries(connectedRealmId, parameters.itemId, fromDate, toDate, parameters.variant, parameters.bonusKey, parameters.modifierKey, parameters.petSpeciesId)

    private fun loadHourlySeries(parameters: DetailLoadParameters, connectedRealmId: Int, fromDate: java.time.LocalDate, toDate: java.time.LocalDate) =
        detailRepository.loadHourlySeries(connectedRealmId, parameters.itemId, fromDate, toDate, parameters.variant, parameters.bonusKey, parameters.modifierKey, parameters.petSpeciesId)

    private fun loadQuantityPie(parameters: DetailLoadParameters, connectedRealmId: Int, date: java.time.LocalDate) =
        detailRepository.loadQuantityPie(connectedRealmId, parameters.itemId, date, parameters.variant, parameters.bonusKey, parameters.modifierKey, parameters.petSpeciesId)

    private fun loadSnapshotMetrics(parameters: DetailLoadParameters): Pair<Pair<Long?, Long?>, Pair<Long?, Long?>> {
        val snapshot = if (parameters.loadCommodity) parameters.context.commoditySnapshot else parameters.context.selectedSnapshot
        val metrics =
            detailRepository.loadSnapshotPriceQuantity(
                snapshot.connectedRealmId, parameters.itemId, snapshot.date, snapshot.hour, parameters.variant,
                parameters.bonusKey, parameters.modifierKey, parameters.petSpeciesId,
            )
        return if (parameters.loadCommodity) (null to null) to metrics else metrics to (null to null)
    }

    private fun loadCurrentListings(parameters: DetailLoadParameters): List<AuctionItemCurrentListingRow> {
        val snapshot = if (parameters.loadCommodity) parameters.context.commoditySnapshot else parameters.context.selectedSnapshot
        return detailRepository.loadCurrentListings(
            snapshot.connectedRealmId, parameters.itemId, parameters.variant,
            parameters.bonusKey, parameters.modifierKey, parameters.petSpeciesId,
        )
    }

    private fun loadCraftingRows(parameters: DetailLoadParameters): Pair<List<AuctionItemCraftingRow>, Map<Int, List<AuctionItemCraftingReagentRow>>> {
        val context = parameters.context
        val craftingRows =
            detailRepository.loadCraftings(
                context.selectedSnapshot.connectedRealmId, context.commoditySnapshot.connectedRealmId, parameters.itemId,
                context.selectedSnapshot.date, context.commoditySnapshot.date, context.selectedSnapshot.hour,
                context.commoditySnapshot.hour, parameters.variant, parameters.bonusKey, parameters.modifierKey,
                parameters.petSpeciesId, parameters.preferredRecipeId, context.localeColumnSuffix,
            )
        val reagentRows =
            detailRepository.loadCraftingReagents(
                context.selectedSnapshot.connectedRealmId, context.commoditySnapshot.connectedRealmId, parameters.itemId,
                craftingRows.map { it.recipeId }, context.selectedSnapshot.date, context.commoditySnapshot.date,
                context.selectedSnapshot.hour, context.commoditySnapshot.hour, context.localeColumnSuffix,
            ).groupBy { it.recipeId }
        return craftingRows to reagentRows
    }

    private fun marketDataSources(
        context: AuctionContext,
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
        val withAvg = daily.mapNotNull { point -> point.avgPrice?.let { point to it } }
        if (withAvg.size < 2) return null
        val prev = withAvg[withAvg.size - 2].second
        val cur = withAvg.last().second
        if (prev == 0.0) return null
        return 100.0 * (cur - prev) / prev
    }

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
        val context = auctionContextService.resolve(regionCode, realmSlug, localeOverride)
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
    private fun AuctionItemCraftingRow.toCraftingDto(): AuctionMarketItemCrafting? {
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

    private fun AuctionItemCraftingRow.toCraftingDetailDto(reagents: List<AuctionItemCraftingReagentRow>): AuctionMarketItemCraftingDetail =
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

    private fun AuctionItemCraftingReagentRow.toReagentDto(): AuctionMarketItemCraftingReagent =
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

    private fun AuctionItemCraftingAnalyticsDailyRow.toAnalyticsPoint(): AuctionMarketItemCraftingAnalyticsPoint =
        AuctionMarketItemCraftingAnalyticsPoint(
            statDate = statDate,
            profit = profit,
            roiPercent = roiPercent,
            reagentCost = reagentCost,
            outputUnitPrice = outputUnitPrice,
        )

    private fun AuctionItemCraftingHeatmapRow.toHeatmapCell(): AuctionMarketItemCraftingHeatmapCell =
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
