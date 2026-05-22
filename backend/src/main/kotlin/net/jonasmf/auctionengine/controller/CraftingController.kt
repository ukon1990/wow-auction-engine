package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.generated.api.CraftingMarketApi
import net.jonasmf.auctionengine.generated.model.AuctionMarketFilterResponse
import net.jonasmf.auctionengine.generated.model.CraftingMarketSearchPage
import net.jonasmf.auctionengine.service.CraftingMarketSearchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class CraftingController(
    private val craftingMarketSearchService: CraftingMarketSearchService,
) : CraftingMarketApi {
    override suspend fun filters(
        region: String,
        realmSlug: String,
        locale: String?,
    ): ResponseEntity<AuctionMarketFilterResponse> =
        ResponseEntity.ok(
            craftingMarketSearchService.filters(
                regionCode = region,
                realmSlug = realmSlug,
                localeOverride = locale,
            ),
        )

    override suspend fun search(
        region: String,
        realmSlug: String,
        locale: String?,
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
    ): ResponseEntity<CraftingMarketSearchPage> =
        ResponseEntity.ok(
            craftingMarketSearchService.search(
                regionCode = region,
                realmSlug = realmSlug,
                localeOverride = locale,
                page = page,
                pageSize = pageSize,
                sortBy = sortBy,
                sortDirection = sortDirection,
                query = query,
                professionIds = professionIds,
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
            ),
        )
}
