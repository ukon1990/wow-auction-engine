package net.jonasmf.auctionengine.repository.rds

internal fun buildCraftingMarketPagedSql(
    withSql: String,
    whereSql: String,
    orderBySql: String,
    includeTotalItems: Boolean = true,
): String {
    val totalItemsSql = if (includeTotalItems) ",\n                COUNT(*) OVER () AS total_items" else ""
    return """
        $withSql
        SELECT
            wrapped.recipe_id, wrapped.crafted_item_id, wrapped.bonus_key, wrapped.modifier_key,
            wrapped.pet_species_id, wrapped.crafted_quantity, wrapped.listing_quantity,
            wrapped.output_unit_price, wrapped.output_p25_price, wrapped.output_p75_price,
            wrapped.reagent_cost, wrapped.profit_copper, wrapped.roi_percent,
            wrapped.output_price_change_percent, wrapped.profit_change_percent, wrapped.reagents_fully_priced,
            wrapped.recipe_name, wrapped.recipe_media_url, wrapped.recipe_rank, wrapped.profession_id,
            wrapped.profession_name, wrapped.expansion_id, wrapped.skill_tier_name, wrapped.profession_category_name,
            wrapped.item_name, wrapped.item_media_url, wrapped.quality_id, wrapped.quality_type, wrapped.quality_name,
            wrapped.item_class_id, wrapped.item_class_name, wrapped.item_subclass_id, wrapped.item_subclass_name,
            wrapped.sale_rate, wrapped.sold_per_day
        FROM (
            SELECT
                c.recipe_id, c.crafted_item_id, c.bonus_key, c.modifier_key, c.pet_species_id, c.crafted_quantity,
                c.listing_quantity, c.output_unit_price, c.output_p25_price, c.output_p75_price, c.reagent_cost,
                CASE WHEN c.reagents_fully_priced THEN c.profit_copper ELSE NULL END AS profit_copper,
                CASE WHEN c.reagents_fully_priced THEN c.roi_percent ELSE NULL END AS roi_percent,
                c.output_price_change_percent,
                CASE WHEN c.reagents_fully_priced THEN c.profit_change_percent ELSE NULL END AS profit_change_percent,
                c.reagents_fully_priced, c.recipe_name, c.recipe_media_url, c.recipe_rank, c.profession_id,
                c.profession_name, c.expansion_id, c.skill_tier_name, c.profession_category_name,
                c.item_name, c.item_media_url, c.quality_id, c.quality_type, c.quality_name,
                c.item_class_id, c.item_class_name, c.item_subclass_id, c.item_subclass_name,
                c.sale_rate, c.sold_per_day$totalItemsSql
            FROM computed c
            $whereSql
        ) wrapped
        $orderBySql
        LIMIT ? OFFSET ?
    """.trimIndent()
}
