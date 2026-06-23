package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.generated.api.AuctionMarketApi
import net.jonasmf.auctionengine.generated.model.AuctionMarketFilterResponse
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemCraftingAnalyticsResponse
import net.jonasmf.auctionengine.generated.model.AuctionMarketItemDetailResponse
import net.jonasmf.auctionengine.generated.model.AuctionMarketSearchPage
import net.jonasmf.auctionengine.service.AuctionMarketItemDetailService
import net.jonasmf.auctionengine.service.AuctionMarketSearchService
import net.jonasmf.auctionengine.service.CraftingMarketSearchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class AuctionMarketController(
    private val auctionMarketSearchService: AuctionMarketSearchService,
    private val auctionMarketItemDetailService: AuctionMarketItemDetailService,
    private val craftingMarketSearchService: CraftingMarketSearchService,
) : AuctionMarketApi {
    override suspend fun filters(
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

    override suspend fun search(
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
        expansionIds: List<Int>?,
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
                expansionIds = expansionIds,
                recipeOnly = recipeOnly,
                minPrice = minPrice,
                maxPrice = maxPrice,
                minQuantity = minQuantity,
                maxQuantity = maxQuantity,
            ),
        )

    override suspend fun getAuctionMarketItemDetail(
        region: String,
        realmSlug: String,
        itemId: Int,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
        scope: String,
        preferredRecipeId: Int?,
        locale: String?,
    ): ResponseEntity<AuctionMarketItemDetailResponse> =
        ResponseEntity.ok(
            auctionMarketItemDetailService.itemDetail(
                regionCode = region,
                realmSlug = realmSlug,
                itemId = itemId,
                bonusKey = bonusKey,
                modifierKey = modifierKey,
                petSpeciesId = petSpeciesId,
                scope = scope.ifBlank { "realm" },
                localeOverride = locale,
                preferredRecipeId = preferredRecipeId,
            ),
        )

    override suspend fun getAuctionMarketItemCraftingAnalytics(
        region: String,
        realmSlug: String,
        itemId: Int,
        recipeId: Int,
        bonusKey: String,
        modifierKey: String,
        petSpeciesId: Int,
        locale: String?,
    ): ResponseEntity<AuctionMarketItemCraftingAnalyticsResponse> =
        ResponseEntity.ok(
            auctionMarketItemDetailService.craftingAnalytics(
                regionCode = region,
                realmSlug = realmSlug,
                itemId = itemId,
                recipeId = recipeId,
                bonusKey = bonusKey,
                modifierKey = modifierKey,
                petSpeciesId = petSpeciesId,
                localeOverride = locale,
            ),
        )
}
