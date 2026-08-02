package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet

internal val craftingMarketRowMapper =
    RowMapper { resultSet: ResultSet, _: Int ->
        CraftingMarketSqlRow(
            recipeId = resultSet.getInt("recipe_id"),
            recipeRank = resultSet.nullableInt("recipe_rank"),
            craftedItemId = resultSet.getInt("crafted_item_id"),
            bonusKey = resultSet.getString("bonus_key") ?: "",
            modifierKey = resultSet.getString("modifier_key") ?: "",
            petSpeciesId = resultSet.getInt("pet_species_id"),
            craftedQuantity = resultSet.getInt("crafted_quantity"),
            listingQuantity = resultSet.nullableLong("listing_quantity"),
            outputUnitPrice = resultSet.nullableLong("output_unit_price"),
            outputP25Price = resultSet.nullableLong("output_p25_price"),
            outputP75Price = resultSet.nullableLong("output_p75_price"),
            reagentCost = resultSet.nullableLong("reagent_cost"),
            profitCopper = resultSet.nullableLong("profit_copper"),
            roiPercent = resultSet.nullableDouble("roi_percent"),
            outputPriceChangePercent = resultSet.nullableDouble("output_price_change_percent"),
            profitChangePercent = resultSet.nullableDouble("profit_change_percent"),
            reagentsFullyPriced = resultSet.getBoolean("reagents_fully_priced"),
            recipeName = resultSet.getString("recipe_name"),
            recipeMediaUrl = resultSet.getString("recipe_media_url"),
            itemName = resultSet.getString("item_name") ?: "",
            itemMediaUrl = resultSet.getString("item_media_url"),
            qualityId = resultSet.nullableInt("quality_id"),
            qualityType = resultSet.getString("quality_type"),
            qualityName = resultSet.getString("quality_name"),
            itemClassId = resultSet.nullableInt("item_class_id"),
            itemClassName = resultSet.getString("item_class_name"),
            itemSubclassId = resultSet.nullableInt("item_subclass_id"),
            itemSubclassName = resultSet.getString("item_subclass_name"),
            professionId = resultSet.nullableInt("profession_id"),
            professionName = resultSet.getString("profession_name"),
            expansionId = resultSet.nullableInt("expansion_id"),
            skillTierName = resultSet.getString("skill_tier_name"),
            professionCategoryName = resultSet.getString("profession_category_name"),
            saleRate = resultSet.nullableDouble("sale_rate"),
            soldPerDay = resultSet.nullableDouble("sold_per_day"),
        )
    }

internal val craftingMarketOptionRowMapper =
    RowMapper { resultSet: ResultSet, _: Int ->
        AuctionMarketFilterOptionRow(
            id = resultSet.getString("id"),
            label = resultSet.getString("label") ?: resultSet.getString("id"),
            parentId = resultSet.getString("parent_id"),
        )
    }

private fun ResultSet.nullableInt(column: String): Int? {
    val value = getInt(column)
    return if (wasNull()) null else value
}

private fun ResultSet.nullableLong(column: String): Long? {
    val value = getLong(column)
    return if (wasNull()) null else value
}

private fun ResultSet.nullableDouble(column: String): Double? {
    val value = getDouble(column)
    return if (wasNull()) null else value
}
