package net.jonasmf.auctionengine.repository.rds

internal fun buildMarketSearchPagedSql(
    request: AuctionMarketSearchRequest,
    withSql: String,
    fromSql: String,
    whereSql: String,
    orderBySql: String,
    includeTotalItems: Boolean = true,
): String {
    val totalItemsSql = if (includeTotalItems) ",\n                COUNT(*) OVER () AS total_items" else ""
    return """
        $withSql
        SELECT
            wrapped.item_id, wrapped.item_name, wrapped.item_media_url,
            wrapped.quality_id, wrapped.quality_type, wrapped.quality_name,
            wrapped.item_class_id, wrapped.item_class_name,
            wrapped.item_subclass_id, wrapped.item_subclass_name,
            wrapped.recipe_id, wrapped.recipe_rank, wrapped.recipe_name, wrapped.recipe_media_url,
            wrapped.selected_bonus_key, wrapped.selected_modifier_key, wrapped.selected_pet_species_id,
            wrapped.selected_price, wrapped.selected_p25_price, wrapped.selected_p75_price, wrapped.selected_quantity,
            wrapped.commodity_price, wrapped.commodity_p25_price, wrapped.commodity_p75_price, wrapped.commodity_quantity,
            wrapped.sale_rate, wrapped.sold_per_day
        FROM (
            SELECT
                d.item_id,
                COALESCE(d.item_name_${request.localeColumnSuffix}, d.item_name_en_gb, d.item_name_en_us) AS item_name,
                d.item_media_url, d.quality_id, d.quality_type,
                COALESCE(d.quality_name_${request.localeColumnSuffix}, d.quality_name_en_gb, d.quality_name_en_us) AS quality_name,
                d.item_class_id,
                COALESCE(d.item_class_name_${request.localeColumnSuffix}, d.item_class_name_en_gb, d.item_class_name_en_us) AS item_class_name,
                d.item_subclass_id,
                COALESCE(d.item_subclass_name_${request.localeColumnSuffix}, d.item_subclass_name_en_gb, d.item_subclass_name_en_us) AS item_subclass_name,
                d.recipe_id, d.recipe_rank,
                COALESCE(d.recipe_name_${request.localeColumnSuffix}, d.recipe_name_en_gb, d.recipe_name_en_us) AS recipe_name,
                d.recipe_media_url, p.selected_bonus_key, p.selected_modifier_key, p.selected_pet_species_id,
                p.selected_price, p.selected_p25_price, p.selected_p75_price, p.selected_quantity,
                p.commodity_price, p.commodity_p25_price, p.commodity_p75_price, p.commodity_quantity,
                tsm.sale_rate, tsm.sold_per_day$totalItemsSql
            $fromSql
            $whereSql
        ) wrapped
        $orderBySql
        LIMIT ? OFFSET ?
    """.trimIndent()
}
