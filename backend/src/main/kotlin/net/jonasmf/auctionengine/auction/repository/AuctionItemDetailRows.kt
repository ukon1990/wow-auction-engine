package net.jonasmf.auctionengine.auction.repository

import java.time.LocalDate
import java.time.OffsetDateTime

data class AuctionItemDetailDailyRow(
    val statDate: LocalDate,
    val pointTimestamp: OffsetDateTime,
    val minPrice: Long?,
    val avgPrice: Double?,
    val p25Price: Long?,
    val p75Price: Long?,
    val maxPrice: Long?,
    val minQuantity: Long?,
    val avgQuantity: Double?,
    val maxQuantity: Long?,
)

data class AuctionItemDetailHourlyRow(
    val timestamp: OffsetDateTime,
    val hourOfDay: Int,
    val minPrice: Long?,
    val avgPrice: Double?,
    val p25Price: Long?,
    val p75Price: Long?,
    val maxPrice: Long?,
    val totalQuantity: Long?,
)

data class AuctionItemDetailPieRow(
    val hourOfDay: Int,
    val quantity: Long?,
    val fraction: Double,
)

data class AuctionItemHeaderRow(
    val itemId: Int,
    val itemName: String,
    val itemMediaUrl: String?,
    val qualityId: Int?,
    val qualityType: String?,
    val qualityName: String?,
    val itemClassId: Int?,
    val itemClassName: String?,
    val itemSubclassId: Int?,
    val itemSubclassName: String?,
    val expansionId: Int?,
    val expansionName: String?,
    val recipeId: Int?,
    val recipeRank: Int?,
    val recipeName: String?,
    val recipeMediaUrl: String?,
    val saleRate: Double?,
    val soldPerDay: Double?,
    val craftedByRecipeCount: Int,
    val reagentInRecipeCount: Int,
)

data class AuctionItemCurrentListingRow(
    val price: Long,
    val quantity: Int,
)

data class AuctionItemCraftingRow(
    val recipeId: Int,
    val recipeRank: Int?,
    val recipeName: String,
    val recipeMediaUrl: String?,
    val craftedQuantity: Int,
    val reagentCost: Long?,
    val reagentsFullyPriced: Boolean,
    val outputUnitPrice: Long?,
    val profit: Long?,
    val roiPercent: Double?,
)

data class AuctionItemCraftingReagentRow(
    val recipeId: Int,
    val itemId: Int,
    val name: String,
    val mediaUrl: String?,
    val quantity: Int,
    val unitPrice: Long?,
    val lineTotal: Long?,
    val purchaseRank: Int?,
)

data class AuctionItemCraftingAnalyticsDailyRow(
    val statDate: LocalDate,
    val profit: Long?,
    val roiPercent: Double?,
    val reagentCost: Long?,
    val outputUnitPrice: Long?,
)

data class AuctionItemCraftingHeatmapRow(
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val profit: Double?,
    val outputUnitPrice: Double?,
    val roiPercent: Double?,
    val sampleCount: Int,
)
