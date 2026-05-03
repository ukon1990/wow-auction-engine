package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.generated.api.AuctionMarketApi
import net.jonasmf.auctionengine.generated.model.AuctionMarketFilterResponse
import net.jonasmf.auctionengine.generated.model.AuctionMarketSearchPage
import net.jonasmf.auctionengine.service.AuctionMarketSearchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class AuctionMarketController(
    private val auctionMarketSearchService: AuctionMarketSearchService,
) : AuctionMarketApi {
    override fun getAuctionMarketFilters(
        region: String,
        realmSlug: String,
        locale: String?,
    ): ResponseEntity<AuctionMarketFilterResponse> =
        ResponseEntity.ok(
            auctionMarketSearchService.filters(
                regionCode = region,
                realmSlug = realmSlug,
                localeOverride = locale,
            ),
        )

    override fun searchAuctionMarket(
        region: String,
        realmSlug: String,
        locale: String?,
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
    ): ResponseEntity<AuctionMarketSearchPage> =
        ResponseEntity.ok(
            auctionMarketSearchService.search(
                regionCode = region,
                realmSlug = realmSlug,
                localeOverride = locale,
                page = page,
                pageSize = pageSize,
                sortBy = sortBy,
                sortDirection = sortDirection,
                query = query,
                qualityIds = qualityIds,
                itemClassIds = itemClassIds,
                itemSubclassIds = itemSubclassIds,
                recipeOnly = recipeOnly,
                minPrice = minPrice,
                maxPrice = maxPrice,
                minQuantity = minQuantity,
                maxQuantity = maxQuantity,
            ),
        )
}
