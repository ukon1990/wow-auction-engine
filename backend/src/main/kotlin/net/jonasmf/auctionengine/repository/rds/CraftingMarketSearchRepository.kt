package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.constant.Region
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate

@Repository
class CraftingMarketSearchRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val logger = LoggerFactory.getLogger(CraftingMarketSearchRepository::class.java)

    private val sortColumns =
        mapOf(
            "itemName" to "item_name",
            "recipeName" to "recipe_name",
            "professionName" to "profession_name",
            "reagentCost" to "reagent_cost",
            "outputPrice" to "output_unit_price",
            "profit" to "profit_copper",
            "roiPercent" to "roi_percent",
            "outputPriceChangePercent" to "output_price_change_percent",
            "profitChangePercent" to "profit_change_percent",
            "listingQuantity" to "listing_quantity",
            "saleRate" to "sale_rate",
            "soldPerDay" to "sold_per_day",
        )

    fun search(request: CraftingMarketSearchRequest): CraftingMarketSearchResult {
        val countParams = ArrayList<Any?>()
        val withSql = buildWithSql(request, countParams)
        val countWhereParams = ArrayList<Any?>()
        val countWhereSql = buildWhereSql(request, countWhereParams)
        countParams.addAll(countWhereParams)
        val countSql = buildCountSql(withSql, countWhereSql)

        val queryStart = System.nanoTime()
        val totalItems =
            jdbcTemplate.queryForObject(countSql, Long::class.java, *countParams.toTypedArray()) ?: 0L

        val dataParams = ArrayList<Any?>()
        val dataWithSql = buildWithSql(request, dataParams)
        val dataWhereParams = ArrayList<Any?>()
        val dataWhereSql = buildWhereSql(request, dataWhereParams)
        dataParams.addAll(dataWhereParams)
        dataParams.add(request.pageSize)
        dataParams.add(request.page * request.pageSize)
        val dataSql = buildCraftingMarketPagedSql(dataWithSql, dataWhereSql, buildOrderBySql(request), false)

        val rows =
            jdbcTemplate.query(
                dataSql,
                CraftingMarketSearchRowMappers.row,
                *dataParams.toTypedArray(),
            )
        logger.debug(
            "Crafting market search queryMs={} totalItems={} rows={}",
            (System.nanoTime() - queryStart) / 1_000_000,
            totalItems,
            rows.size,
        )
        return CraftingMarketSearchResult(rows = rows, totalItems = totalItems)
    }

    fun professionOptions(localeColumnSuffix: String): List<AuctionMarketFilterOptionRow> =
        jdbcTemplate.query(
            """
            SELECT
                CAST(p.id AS CHAR) AS id,
                COALESCE(l.${localeColumnSuffix}, l.en_gb, l.en_us, CAST(p.id AS CHAR)) AS label,
                NULL AS parent_id
            FROM profession p
                LEFT JOIN locale l ON l.id = p.name_id
            ORDER BY label
            """.trimIndent(),
            CraftingMarketSearchRowMappers.option,
        )

    fun expansionOptions(localeColumnSuffix: String): List<AuctionMarketFilterOptionRow> =
        jdbcTemplate.query(
            """
            SELECT
                CAST(e.id AS CHAR) AS id,
                COALESCE(l.${localeColumnSuffix}, l.en_gb, l.en_us, e.slug) AS label,
                NULL AS parent_id
            FROM expansion e
                LEFT JOIN locale l ON l.id = e.name_id
            ORDER BY e.display_order, e.id
            """.trimIndent(),
            CraftingMarketSearchRowMappers.option,
        )

    private fun buildWithSql(
        request: CraftingMarketSearchRequest,
        params: MutableList<Any?>,
    ): String {
        val hourSel = hourColumnSuffix(request.selectedHour)
        val hourCom = hourColumnSuffix(request.commodityHour)
        val priceSel = "price$hourSel"
        val priceCom = "price$hourCom"
        val previousDate = java.sql.Date.valueOf(request.previousDate)
        val commodityPreviousDate = java.sql.Date.valueOf(request.commodityPreviousDate)
        val loc = request.localeColumnSuffix

        params.add(request.selectedConnectedRealmId)
        params.add(request.commodityConnectedRealmId)
        params.add(request.selectedConnectedRealmId)
        params.add(request.commodityConnectedRealmId)
        params.add(request.selectedConnectedRealmId)
        params.add(request.commodityConnectedRealmId)
        params.add(request.selectedConnectedRealmId)
        params.add(previousDate)
        params.add(request.commodityConnectedRealmId)
        params.add(commodityPreviousDate)
        params.add(request.region.name)

        return """
            WITH
            pricing_item_ids AS (
                ${RecipeReagentPricingSql.allPricingItemIdsSql()}
            ),
            sel_latest_history AS (
                SELECT MAX(id) AS update_history_id
                FROM connected_realm_update_history
                WHERE connected_realm_id = ?
            ),
            com_latest_history AS (
                SELECT MAX(id) AS update_history_id
                FROM connected_realm_update_history
                WHERE connected_realm_id = ?
            ),
            reagent_sel_base AS (
                SELECT
                    a.item_id,
                    a.buyout AS price,
                    a.bonus_key,
                    a.modifier_key,
                    COALESCE(a.pet_species_id, -1) AS pet_species_id
                FROM auction a
                    INNER JOIN sel_latest_history lh ON lh.update_history_id = a.update_history_id
                WHERE a.connected_realm_id = ?
                  AND a.buyout IS NOT NULL
                  AND EXISTS (SELECT 1 FROM pricing_item_ids pi WHERE pi.item_id = a.item_id)
            ),
            reagent_sel_ranked AS (
                SELECT
                    item_id,
                    price,
                    ROW_NUMBER() OVER (
                        PARTITION BY item_id
                        ORDER BY price ASC, bonus_key, modifier_key, pet_species_id
                    ) AS rn
                FROM reagent_sel_base
            ),
            reagent_sel AS (
                SELECT item_id, price FROM reagent_sel_ranked WHERE rn = 1
            ),
            reagent_com_base AS (
                SELECT
                    a.item_id,
                    a.buyout AS price,
                    a.bonus_key,
                    a.modifier_key,
                    COALESCE(a.pet_species_id, -1) AS pet_species_id
                FROM auction a
                    INNER JOIN com_latest_history lh ON lh.update_history_id = a.update_history_id
                WHERE a.connected_realm_id = ?
                  AND a.buyout IS NOT NULL
                  AND EXISTS (SELECT 1 FROM pricing_item_ids pi WHERE pi.item_id = a.item_id)
            ),
            reagent_com_ranked AS (
                SELECT
                    item_id,
                    price,
                    ROW_NUMBER() OVER (
                        PARTITION BY item_id
                        ORDER BY price ASC, bonus_key, modifier_key, pet_species_id
                    ) AS rn
                FROM reagent_com_base
            ),
            reagent_com AS (
                SELECT item_id, price FROM reagent_com_ranked WHERE rn = 1
            ),
            reagent_items AS (
                SELECT item_id FROM pricing_item_ids
            ),
            reagent_unit AS (
                SELECT
                    i.item_id,
                    COALESCE(rs.price, rc.price) AS price
                FROM reagent_items i
                    LEFT JOIN reagent_sel rs ON rs.item_id = i.item_id
                    LEFT JOIN reagent_com rc ON rc.item_id = i.item_id
            ),
            recipe_outputs AS (
                SELECT
                    ro.recipe_id,
                    ro.crafted_item_id,
                    ro.sort_order,
                    COALESCE(NULLIF(ro.crafted_quantity, 0), 1) AS crafted_qty
                FROM v_recipe_crafted_output ro
            ),
            ${RecipeReagentPricingSql.recipeReagentLinesCte(rankExpr = RecipeReagentPricingSql.craftingTargetRankExpr(), priceCte = "reagent_unit")},
            ${RecipeReagentPricingSql.recipeReagentCostCte()},
            realm_outputs AS (
                SELECT
                    ro.recipe_id,
                    ro.crafted_item_id,
                    ro.crafted_qty,
                    a.bonus_key,
                    a.modifier_key,
                    COALESCE(a.pet_species_id, -1) AS pet_species_id,
                    a.buyout AS output_unit_price,
                    a.p25 AS output_p25_price,
                    a.p75 AS output_p75_price,
                    a.quantity AS listing_quantity
                FROM recipe_outputs ro
                    INNER JOIN auction a
                        ON a.item_id = ro.crafted_item_id
                        AND a.connected_realm_id = ?
                        AND a.buyout IS NOT NULL
                    INNER JOIN sel_latest_history lh ON lh.update_history_id = a.update_history_id
            ),
            com_outputs AS (
                SELECT
                    ro.recipe_id,
                    ro.crafted_item_id,
                    ro.crafted_qty,
                    a.bonus_key,
                    a.modifier_key,
                    COALESCE(a.pet_species_id, -1) AS pet_species_id,
                    a.buyout AS output_unit_price,
                    a.p25 AS output_p25_price,
                    a.p75 AS output_p75_price,
                    a.quantity AS listing_quantity
                FROM recipe_outputs ro
                    INNER JOIN auction a
                        ON a.item_id = ro.crafted_item_id
                        AND a.connected_realm_id = ?
                        AND a.buyout IS NOT NULL
                    INNER JOIN com_latest_history lh ON lh.update_history_id = a.update_history_id
            ),
            listed_outputs AS (
                SELECT * FROM realm_outputs
                UNION ALL
                SELECT c.recipe_id, c.crafted_item_id, c.crafted_qty, c.bonus_key, c.modifier_key, c.pet_species_id,
                       c.output_unit_price, c.output_p25_price, c.output_p75_price, c.listing_quantity
                FROM com_outputs c
                WHERE NOT EXISTS (
                    SELECT 1 FROM realm_outputs ro
                    WHERE ro.recipe_id = c.recipe_id
                      AND ro.bonus_key <=> c.bonus_key
                      AND ro.modifier_key <=> c.modifier_key
                      AND ro.pet_species_id <=> c.pet_species_id
                )
            ),
            recipes_with_listing AS (
                SELECT DISTINCT recipe_id FROM listed_outputs
            ),
            unlisted_outputs AS (
                SELECT
                    ro.recipe_id,
                    ro.crafted_item_id,
                    ro.crafted_qty,
                    '' AS bonus_key,
                    '' AS modifier_key,
                    0 AS pet_species_id,
                    CAST(NULL AS UNSIGNED) AS output_unit_price,
                    CAST(NULL AS UNSIGNED) AS output_p25_price,
                    CAST(NULL AS UNSIGNED) AS output_p75_price,
                    CAST(NULL AS SIGNED) AS listing_quantity
                FROM recipe_outputs ro
                WHERE ro.recipe_id NOT IN (SELECT recipe_id FROM recipes_with_listing)
            ),
            crafted_current AS (
                SELECT * FROM listed_outputs
                UNION ALL
                SELECT * FROM unlisted_outputs
            ),
            prev_realm AS (
                SELECT
                    ro.recipe_id,
                    ro.crafted_item_id,
                    ash.bonus_key,
                    ash.modifier_key,
                    ash.pet_species_id,
                    ash.$priceSel AS prev_unit_price
                FROM recipe_outputs ro
                    INNER JOIN auction_stats_hourly ash
                        ON ash.item_id = ro.crafted_item_id
                        AND ash.connected_realm_id = ?
                        AND ash.date = ?
                        AND ash.$priceSel IS NOT NULL
            ),
            prev_com AS (
                SELECT
                    ro.recipe_id,
                    ro.crafted_item_id,
                    ash.bonus_key,
                    ash.modifier_key,
                    ash.pet_species_id,
                    ash.$priceCom AS prev_unit_price
                FROM recipe_outputs ro
                    INNER JOIN auction_stats_hourly ash
                        ON ash.item_id = ro.crafted_item_id
                        AND ash.connected_realm_id = ?
                        AND ash.date = ?
                        AND ash.$priceCom IS NOT NULL
            ),
            crafted_prev AS (
                SELECT recipe_id, crafted_item_id, bonus_key, modifier_key, pet_species_id, prev_unit_price FROM prev_realm
                UNION ALL
                SELECT c.recipe_id, c.crafted_item_id, c.bonus_key, c.modifier_key, c.pet_species_id, c.prev_unit_price
                FROM prev_com c
                WHERE NOT EXISTS (
                    SELECT 1 FROM prev_realm pr
                    WHERE pr.recipe_id = c.recipe_id
                      AND pr.crafted_item_id <=> c.crafted_item_id
                      AND pr.bonus_key <=> c.bonus_key
                      AND pr.modifier_key <=> c.modifier_key
                      AND pr.pet_species_id <=> c.pet_species_id
                )
            ),
            recipe_dim AS (
                SELECT
                    ro.recipe_id,
                    ro.crafted_item_id,
                    ro.crafted_qty AS crafted_quantity,
                    reci.media_url AS recipe_media_url,
                    ${RecipeReagentPricingSql.craftingTargetRankExpr(recipeAlias = "reci")} AS recipe_rank,
                    COALESCE(reci_l.$loc, reci_l.en_gb, reci_l.en_us) AS recipe_name,
                    p.id AS profession_id,
                    COALESCE(p_l.$loc, p_l.en_gb, p_l.en_us) AS profession_name,
                    COALESCE(st_l.$loc, st_l.en_gb, st_l.en_us) AS skill_tier_name,
                    COALESCE(pc_l.$loc, pc_l.en_gb, pc_l.en_us) AS profession_category_name
                FROM recipe_outputs ro
                    INNER JOIN v_recipe reci ON reci.id = ro.recipe_id
                    LEFT JOIN profession_category pc ON pc.internal_id = reci.profession_category_id
                    LEFT JOIN locale pc_l ON pc_l.id = pc.name_id
                    LEFT JOIN skill_tier st ON st.id = pc.skill_tier_id
                    LEFT JOIN locale st_l ON st_l.id = st.name_id
                    LEFT JOIN profession p ON p.id = st.profession_id
                    LEFT JOIN locale p_l ON p_l.id = p.name_id
                    LEFT JOIN locale reci_l ON reci_l.id = reci.name_id
            ),
            base AS (
                SELECT
                    cc.recipe_id,
                    cc.crafted_item_id,
                    cc.bonus_key,
                    cc.modifier_key,
                    cc.pet_species_id,
                    cc.crafted_qty AS crafted_quantity,
                    cc.listing_quantity,
                    cc.output_unit_price,
                    cc.output_p25_price,
                    cc.output_p75_price,
                    rrc.reagent_cost,
                    rrc.reagents_fully_priced,
                    (cc.output_unit_price * cc.crafted_qty - rrc.reagent_cost) AS profit_copper,
                    CASE
                        WHEN cc.output_unit_price IS NULL
                            OR rrc.reagent_cost IS NULL
                            OR rrc.reagent_cost <= 0 THEN NULL
                        ELSE ((cc.output_unit_price * cc.crafted_qty - rrc.reagent_cost) / rrc.reagent_cost) * 100.0
                    END AS roi_percent,
                    CASE
                        WHEN cc.output_unit_price IS NULL
                            OR cp.prev_unit_price IS NULL
                            OR cp.prev_unit_price = 0 THEN NULL
                        ELSE ((cc.output_unit_price - cp.prev_unit_price) / cp.prev_unit_price) * 100.0
                    END AS output_price_change_percent,
                    CASE
                        WHEN cp.prev_unit_price IS NULL
                            OR rrc.reagent_cost IS NULL THEN NULL
                        ELSE (cp.prev_unit_price * cc.crafted_qty - rrc.reagent_cost)
                    END AS prev_profit_copper,
                    rd.recipe_name,
                    rd.recipe_media_url,
                    rd.recipe_rank,
                    rd.profession_id,
                    rd.profession_name,
                    rd.skill_tier_name,
                    rd.profession_category_name,
                    COALESCE(
                        d.item_name_$loc,
                        d.item_name_en_gb,
                        d.item_name_en_us,
                        CONCAT('Item ', cc.crafted_item_id)
                    ) AS item_name,
                    d.item_media_url,
                    d.quality_id,
                    d.quality_type,
                    COALESCE(d.quality_name_$loc, d.quality_name_en_gb, d.quality_name_en_us) AS quality_name,
                    d.item_class_id,
                    COALESCE(d.item_class_name_$loc, d.item_class_name_en_gb, d.item_class_name_en_us) AS item_class_name,
                    d.item_subclass_id,
                    COALESCE(d.item_subclass_name_$loc, d.item_subclass_name_en_gb, d.item_subclass_name_en_us) AS item_subclass_name,
                    d.expansion_id,
                    tsm.sale_rate,
                    tsm.sold_per_day
                FROM crafted_current cc
                    INNER JOIN recipe_dim rd
                        ON rd.recipe_id = cc.recipe_id
                        AND rd.crafted_item_id = cc.crafted_item_id
                    LEFT JOIN recipe_reagent_cost rrc
                        ON rrc.recipe_id = cc.recipe_id
                        AND rrc.target_rank = rd.recipe_rank
                    LEFT JOIN crafted_prev cp
                        ON cp.recipe_id = cc.recipe_id
                        AND cp.crafted_item_id <=> cc.crafted_item_id
                        AND cp.bonus_key <=> cc.bonus_key
                        AND cp.modifier_key <=> cc.modifier_key
                        AND cp.pet_species_id <=> cc.pet_species_id
                    LEFT JOIN v_auction_market_item_details d
                        ON d.item_id = cc.crafted_item_id
                    LEFT JOIN tsm_region_metric tsm
                        ON tsm.region = ?
                        AND tsm.subject_type = 'ITEM'
                        AND tsm.subject_id = cc.crafted_item_id
            ),
            computed AS (
                SELECT
                    b.*,
                    CASE
                        WHEN b.prev_profit_copper IS NULL OR b.profit_copper IS NULL OR b.prev_profit_copper = 0 THEN NULL
                        ELSE ((b.profit_copper - b.prev_profit_copper) / b.prev_profit_copper) * 100.0
                    END AS profit_change_percent
                FROM base b
            )
            """.trimIndent()
    }

    private fun buildCountSql(
        withSql: String,
        whereSql: String,
    ): String =
        """
        $withSql
        SELECT COUNT(*)
        FROM computed c
        $whereSql
        """.trimIndent()

    private fun buildOrderBySql(request: CraftingMarketSearchRequest): String {
        val dir = if (request.sortDirection.equals("desc", ignoreCase = true)) "DESC" else "ASC"
        val primary =
            when (request.sortBy) {
                "reagentCost", "outputPrice", "profit", "listingQuantity" -> {
                    val col = sortColumns.getValue(request.sortBy)
                    "((wrapped.$col) IS NULL) ASC, wrapped.$col $dir"
                }
                "roiPercent", "outputPriceChangePercent", "profitChangePercent", "saleRate", "soldPerDay" -> {
                    val col = sortColumns.getValue(request.sortBy)
                    "((wrapped.$col) IS NULL) ASC, wrapped.$col $dir"
                }
                else -> {
                    val col = sortColumns[request.sortBy] ?: sortColumns.getValue("itemName")
                    "wrapped.$col $dir"
                }
            }
        return "ORDER BY $primary, wrapped.item_name ASC, wrapped.recipe_id ASC, wrapped.bonus_key ASC, wrapped.modifier_key ASC, wrapped.pet_species_id ASC"
    }

    private fun buildWhereSql(
        request: CraftingMarketSearchRequest,
        params: MutableList<Any?>,
    ): String {
        val predicates = mutableListOf<String>()
        appendCraftingQueryPredicate(predicates, params, request.query)
        appendCraftingInPredicate(predicates, params, "c.profession_id", request.professionIds)
        appendCraftingInPredicate(predicates, params, "c.expansion_id", request.expansionIds)
        appendCraftingInPredicate(predicates, params, "c.quality_id", request.qualityIds)
        if (request.requireCompleteReagentPricing) {
            predicates.add("c.reagents_fully_priced = 1")
        }
        appendCraftingRangePredicates(predicates, params, request)
        return if (predicates.isEmpty()) "" else "WHERE " + predicates.joinToString(" AND ")
    }

    private fun hourColumnSuffix(hour: Int): String {
        require(hour in 0..23) { "Hour must be between 0 and 23: $hour" }
        return hour.toString().padStart(2, '0')
    }

    private fun String.escapeLike(): String =
        replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_")

    /** For integration tests: `EXPLAIN` / `ANALYZE` against real MariaDB. */
    internal fun buildCraftingMarketSearchPagedSqlForExplain(request: CraftingMarketSearchRequest): Pair<String, Array<Any?>> {
        val params = ArrayList<Any?>()
        val withSql = buildWithSql(request, params)
        val whereSql = buildWhereSql(request, params)
        val offset = request.page * request.pageSize
        params.add(request.pageSize)
        params.add(offset)
        return Pair(buildCraftingMarketPagedSql(withSql, whereSql, buildOrderBySql(request)), params.toTypedArray())
    }
}
