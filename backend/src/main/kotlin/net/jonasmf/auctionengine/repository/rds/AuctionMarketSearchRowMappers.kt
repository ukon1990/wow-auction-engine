package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet

internal val auctionMarketRowMapper =
    RowMapper { resultSet: ResultSet, _: Int ->
        AuctionMarketRow(
            itemId = resultSet.getInt("item_id"),
            itemName = resultSet.getString("item_name"),
            itemMediaUrl = resultSet.getString("item_media_url"),
            qualityId = resultSet.nullableInt("quality_id"),
            qualityType = resultSet.getString("quality_type"),
            qualityName = resultSet.getString("quality_name"),
            itemClassId = resultSet.nullableInt("item_class_id"),
            itemClassName = resultSet.getString("item_class_name"),
            itemSubclassId = resultSet.nullableInt("item_subclass_id"),
            itemSubclassName = resultSet.getString("item_subclass_name"),
            recipeId = resultSet.nullableInt("recipe_id"),
            recipeRank = resultSet.nullableInt("recipe_rank"),
            recipeName = resultSet.getString("recipe_name"),
            recipeMediaUrl = resultSet.getString("recipe_media_url"),
            selectedBonusKey = resultSet.getString("selected_bonus_key") ?: "",
            selectedModifierKey = resultSet.getString("selected_modifier_key") ?: "",
            selectedPetSpeciesId = resultSet.getInt("selected_pet_species_id"),
            selectedPrice = resultSet.nullableLong("selected_price"),
            selectedP25Price = resultSet.nullableLong("selected_p25_price"),
            selectedP75Price = resultSet.nullableLong("selected_p75_price"),
            selectedQuantity = resultSet.nullableLong("selected_quantity"),
            commodityPrice = resultSet.nullableLong("commodity_price"),
            commodityP25Price = resultSet.nullableLong("commodity_p25_price"),
            commodityP75Price = resultSet.nullableLong("commodity_p75_price"),
            commodityQuantity = resultSet.nullableLong("commodity_quantity"),
            saleRate = resultSet.nullableDouble("sale_rate"),
            soldPerDay = resultSet.nullableDouble("sold_per_day"),
        )
    }

internal val auctionMarketQualityOptionRowMapper =
    RowMapper { resultSet: ResultSet, _: Int ->
        AuctionMarketFilterOptionRow(
            id = resultSet.getString("id"),
            label = resultSet.getString("label") ?: resultSet.getString("id"),
            parentId = resultSet.getString("parent_id"),
            qualityType = resultSet.getString("quality_type"),
        )
    }

internal val auctionMarketOptionRowMapper =
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
