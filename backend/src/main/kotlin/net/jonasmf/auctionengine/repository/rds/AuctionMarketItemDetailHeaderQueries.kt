package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.constant.Region
import java.sql.Date
import java.time.LocalDate

internal fun AuctionMarketItemDetailRepository.loadItemHeader(itemId: Int, localeColumnSuffix: String, region: Region): AuctionMarketItemHeaderRow? =
    jdbcTemplate.query(
        """
        SELECT d.item_id,
               COALESCE(d.item_name_$localeColumnSuffix, d.item_name_en_gb, d.item_name_en_us) AS item_name,
               d.item_media_url, d.quality_id, d.quality_type,
               COALESCE(d.quality_name_$localeColumnSuffix, d.quality_name_en_gb, d.quality_name_en_us) AS quality_name,
               d.item_class_id,
               COALESCE(d.item_class_name_$localeColumnSuffix, d.item_class_name_en_gb, d.item_class_name_en_us) AS item_class_name,
               d.item_subclass_id,
               COALESCE(d.item_subclass_name_$localeColumnSuffix, d.item_subclass_name_en_gb, d.item_subclass_name_en_us) AS item_subclass_name,
               d.expansion_id, COALESCE(e_l.$localeColumnSuffix, e_l.en_gb, e_l.en_us, e.slug) AS expansion_name,
               d.recipe_id, d.recipe_rank,
               COALESCE(d.recipe_name_$localeColumnSuffix, d.recipe_name_en_gb, d.recipe_name_en_us) AS recipe_name,
               d.recipe_media_url, tsm.sale_rate, tsm.sold_per_day,
               (SELECT COUNT(DISTINCT output.recipe_id) FROM v_recipe_crafted_output output WHERE output.crafted_item_id = d.item_id) AS crafted_by_recipe_count,
               (SELECT COUNT(DISTINCT rr.recipe_id) FROM v_recipe_reagent rr
                    LEFT JOIN recipe_reagent_rank rrk ON rrk.recipe_reagent_id = rr.internal_id
                WHERE rr.item_id = d.item_id OR rrk.item_id = d.item_id) AS reagent_in_recipe_count
        FROM v_auction_market_item_details d
        LEFT JOIN expansion e ON e.id = d.expansion_id
        LEFT JOIN locale e_l ON e_l.id = e.name_id
        LEFT JOIN tsm_region_metric tsm ON tsm.region = ? AND tsm.subject_type = 'ITEM' AND tsm.subject_id = d.item_id
        WHERE d.item_id = ?
        LIMIT 1
        """.trimIndent(),
        AuctionMarketItemDetailRowMappers.headerRowMapper,
        region.name,
        itemId,
    ).firstOrNull()

internal fun AuctionMarketItemDetailRepository.loadSnapshotPriceQuantity(
    connectedRealmId: Int, itemId: Int, statDate: LocalDate, hourOfDay: Int, variant: Boolean,
    bonusKey: String, modifierKey: String, petSpeciesId: Int,
): Pair<Long?, Long?> {
    val variantWhere = if (variant) "AND bonus_key <=> ? AND modifier_key <=> ? AND pet_species_id = ?" else ""
    val sql = """
        SELECT MIN(price) AS price, SUM(quantity) AS qty
        FROM v_auction_house_prices
        WHERE connected_realm_id = ? AND item_id = ? AND date = ? AND hour_of_day = ?
        $variantWhere
    """.trimIndent()
    val params = mutableListOf<Any?>(connectedRealmId, itemId, Date.valueOf(statDate), hourOfDay)
    if (variant) params.addAll(listOf(bonusKey, modifierKey, petSpeciesId))
    return jdbcTemplate.query(sql, AuctionMarketItemDetailRowMappers.snapshotRowMapper, *params.toTypedArray()).firstOrNull() ?: (null to null)
}

internal fun AuctionMarketItemDetailRepository.recipeProducesItem(recipeId: Int, itemId: Int): Boolean =
    jdbcTemplate.queryForObject(
        "SELECT EXISTS (SELECT 1 FROM v_recipe_crafted_output WHERE recipe_id = ? AND crafted_item_id = ?)",
        Boolean::class.java,
        recipeId,
        itemId,
    ) == true
