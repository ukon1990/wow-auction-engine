package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.constant.Region
import java.time.LocalDate

data class AuctionMarketSearchRequest(
    val region: Region,
    val selectedConnectedRealmId: Int,
    val selectedDate: LocalDate,
    val selectedHour: Int,
    val commodityConnectedRealmId: Int,
    val commodityDate: LocalDate,
    val commodityHour: Int,
    val localeColumnSuffix: String,
    val page: Int,
    val pageSize: Int,
    val sortBy: String,
    val sortDirection: String,
    val query: String?,
    val qualityIds: List<Int>,
    val itemClassIds: List<Int>,
    val itemSubclassIds: List<Int>,
    val expansionIds: List<Int>,
    val recipeOnly: Boolean?,
    val minPrice: Long?,
    val maxPrice: Long?,
    val minQuantity: Long?,
    val maxQuantity: Long?,
    val minSaleRatePercent: Double?,
    val maxSaleRatePercent: Double?,
    val minSoldPerDay: Double?,
    val maxSoldPerDay: Double?,
)

data class AuctionMarketSearchResult(
    val rows: List<AuctionMarketRow>,
    val totalItems: Long,
)

data class AuctionMarketRow(
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
    val selectedBonusKey: String,
    val selectedModifierKey: String,
    val selectedPetSpeciesId: Int,
    val selectedPrice: Long?,
    val selectedP25Price: Long?,
    val selectedP75Price: Long?,
    val selectedQuantity: Long?,
    val commodityPrice: Long?,
    val commodityP25Price: Long?,
    val commodityP75Price: Long?,
    val commodityQuantity: Long?,
    val saleRate: Double?,
    val soldPerDay: Double?,
)

data class AuctionMarketFilterOptionRow(
    val id: String,
    val label: String,
    val parentId: String? = null,
    val qualityType: String? = null,
)
