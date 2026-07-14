package net.jonasmf.auctionengine.repository.rds

import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.time.LocalDate
import java.time.ZoneOffset

internal object AuctionMarketItemDetailRowMappers {
    val headerRowMapper =
        RowMapper { rs: ResultSet, _: Int ->
            AuctionMarketItemHeaderRow(
                itemId = rs.getInt("item_id"),
                itemName = rs.getString("item_name"),
                itemMediaUrl = rs.getString("item_media_url"),
                qualityId = rs.getNullableInt("quality_id"),
                qualityType = rs.getString("quality_type"),
                qualityName = rs.getString("quality_name"),
                itemClassId = rs.getNullableInt("item_class_id"),
                itemClassName = rs.getString("item_class_name"),
                itemSubclassId = rs.getNullableInt("item_subclass_id"),
                itemSubclassName = rs.getString("item_subclass_name"),
                recipeId = rs.getNullableInt("recipe_id"),
                recipeRank = rs.getNullableInt("recipe_rank"),
                recipeName = rs.getString("recipe_name"),
                recipeMediaUrl = rs.getString("recipe_media_url"),
            )
        }

    val snapshotRowMapper =
        RowMapper { rs: ResultSet, _: Int ->
            rs.getNullableLong("price") to rs.getNullableLong("qty")
        }

    val currentListingRowMapper =
        RowMapper { rs: ResultSet, _: Int ->
            AuctionMarketItemCurrentListingRow(
                price = rs.getLong("price"),
                quantity = rs.getInt("quantity"),
            )
        }

    val dailyRowMapper =
        RowMapper { rs: ResultSet, _: Int ->
            val d = rs.getObject("stat_date", LocalDate::class.java)
            AuctionMarketItemDetailDailyRow(
                statDate = d,
                pointTimestamp = d.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),
                minPrice = rs.getNullableLong("min_price"),
                avgPrice = rs.getNullableDouble("avg_price"),
                p25Price = rs.getNullableLong("p25_price"),
                p75Price = rs.getNullableLong("p75_price"),
                maxPrice = rs.getNullableLong("max_price"),
                minQuantity = rs.getNullableLong("min_quantity"),
                avgQuantity = rs.getNullableDouble("avg_quantity"),
                maxQuantity = rs.getNullableLong("max_quantity"),
            )
        }

    val hourlyRowMapper =
        RowMapper { rs: ResultSet, _: Int ->
            val ts = rs.getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC)
            AuctionMarketItemDetailHourlyRow(
                timestamp = ts,
                hourOfDay = rs.getInt("hour_of_day"),
                minPrice = rs.getNullableLong("min_price"),
                avgPrice = rs.getNullableDouble("avg_price"),
                p25Price = rs.getNullableLong("p25_price"),
                p75Price = rs.getNullableLong("p75_price"),
                maxPrice = rs.getNullableLong("max_price"),
                totalQuantity = rs.getNullableLong("total_quantity"),
            )
        }

    val pieRowMapper =
        RowMapper { rs: ResultSet, _: Int ->
            AuctionMarketItemDetailPieRow(
                hourOfDay = rs.getInt("hour_of_day"),
                quantity = rs.getNullableLong("quantity"),
                fraction = rs.getDouble("fraction"),
            )
        }

    val craftingRowMapper =
        RowMapper { rs: ResultSet, _: Int ->
            AuctionMarketItemCraftingRow(
                recipeId = rs.getInt("recipe_id"),
                recipeRank = rs.getNullableInt("recipe_rank"),
                recipeName = rs.getString("recipe_name") ?: "",
                recipeMediaUrl = rs.getString("recipe_media_url"),
                craftedQuantity = rs.getInt("crafted_quantity"),
                reagentCost = rs.getNullableLong("reagent_cost"),
                reagentsFullyPriced = rs.getBoolean("reagents_fully_priced"),
                outputUnitPrice = rs.getNullableLong("output_unit_price"),
                profit = rs.getNullableLong("profit"),
                roiPercent = rs.getNullableDouble("roi_percent"),
            )
        }

    val craftingReagentRowMapper =
        RowMapper { rs: ResultSet, _: Int ->
            AuctionMarketItemCraftingReagentRow(
                recipeId = rs.getInt("recipe_id"),
                itemId = rs.getInt("item_id"),
                name = rs.getString("name") ?: "",
                mediaUrl = rs.getString("media_url"),
                quantity = rs.getInt("quantity"),
                unitPrice = rs.getNullableLong("unit_price"),
                lineTotal = rs.getNullableLong("line_total"),
                purchaseRank = rs.getNullableInt("purchase_rank"),
            )
        }

    val craftingAnalyticsDailyRowMapper =
        RowMapper { rs: ResultSet, _: Int ->
            AuctionMarketItemCraftingAnalyticsDailyRow(
                statDate = rs.getObject("stat_date", LocalDate::class.java),
                profit = rs.getNullableLong("profit"),
                roiPercent = rs.getNullableDouble("roi_percent"),
                reagentCost = rs.getNullableLong("reagent_cost"),
                outputUnitPrice = rs.getNullableLong("output_unit_price"),
            )
        }

    val craftingAnalyticsHeatmapRowMapper =
        RowMapper { rs: ResultSet, _: Int ->
            AuctionMarketItemCraftingHeatmapRow(
                dayOfWeek = rs.getInt("day_of_week"),
                hourOfDay = rs.getInt("hour_of_day"),
                profit = rs.getNullableDouble("profit"),
                outputUnitPrice = rs.getNullableDouble("output_unit_price"),
                roiPercent = rs.getNullableDouble("roi_percent"),
                sampleCount = rs.getInt("sample_count"),
            )
        }
}

internal fun ResultSet.getNullableInt(column: String): Int? {
    val v = getInt(column)
    return if (wasNull()) null else v
}

internal fun ResultSet.getNullableLong(column: String): Long? {
    val v = getLong(column)
    return if (wasNull()) null else v
}

internal fun ResultSet.getNullableDouble(column: String): Double? {
    val v = getDouble(column)
    return if (wasNull()) null else v
}
