package net.jonasmf.auctionengine.repository.rds

import java.time.LocalDate
import java.time.OffsetDateTime

data class AuctionMarketItemDetailDailyRow(
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

data class AuctionMarketItemDetailHourlyRow(
    val timestamp: OffsetDateTime,
    val hourOfDay: Int,
    val minPrice: Long?,
    val avgPrice: Double?,
    val p25Price: Long?,
    val p75Price: Long?,
    val maxPrice: Long?,
    val totalQuantity: Long?,
)

data class AuctionMarketItemDetailPieRow(
    val hourOfDay: Int,
    val quantity: Long?,
    val fraction: Double,
)

data class AuctionMarketItemHeaderRow(
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
    val recipeId: Int?,
    val recipeRank: Int?,
    val recipeName: String?,
    val recipeMediaUrl: String?,
)

data class AuctionMarketItemCurrentListingRow(
    val price: Long,
    val quantity: Int,
)

data class AuctionMarketItemCraftingRow(
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

data class AuctionMarketItemCraftingReagentRow(
    val recipeId: Int,
    val itemId: Int,
    val name: String,
    val mediaUrl: String?,
    val quantity: Int,
    val unitPrice: Long?,
    val lineTotal: Long?,
)

data class AuctionMarketItemCraftingAnalyticsDailyRow(
    val statDate: LocalDate,
    val profit: Long?,
    val roiPercent: Double?,
    val reagentCost: Long?,
    val outputUnitPrice: Long?,
)

data class AuctionMarketItemCraftingHeatmapRow(
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val profit: Double?,
    val outputUnitPrice: Double?,
    val roiPercent: Double?,
    val sampleCount: Int,
)
