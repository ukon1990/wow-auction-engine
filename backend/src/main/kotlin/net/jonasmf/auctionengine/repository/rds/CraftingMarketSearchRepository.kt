package net.jonasmf.auctionengine.repository.rds

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate

data class CraftingMarketSearchRequest(
    val selectedConnectedRealmId: Int,
    val selectedDate: LocalDate,
    val selectedHour: Int,
    val commodityConnectedRealmId: Int,
    val commodityDate: LocalDate,
    val commodityHour: Int,
    val previousDate: LocalDate,
    val commodityPreviousDate: LocalDate,
    val localeColumnSuffix: String,
    val page: Int,
    val pageSize: Int,
    val sortBy: String,
    val sortDirection: String,
    val query: String?,
    val professionIds: List<Int>,
    val expansionIds: List<Int>,
    val minProfit: Long?,
    val maxProfit: Long?,
    val minRoiPercent: Double?,
    val maxRoiPercent: Double?,
    val minReagentCost: Long?,
    val maxReagentCost: Long?,
    val minOutputPrice: Long?,
    val maxOutputPrice: Long?,
    val minOutputPriceChangePercent: Double?,
    val maxOutputPriceChangePercent: Double?,
    val requireCompleteReagentPricing: Boolean,
)

data class CraftingMarketSearchResult(
    val rows: List<CraftingMarketSqlRow>,
    val totalItems: Long,
)

data class CraftingMarketSqlRow(
    val recipeId: Int,
    val craftedItemId: Int,
    val bonusKey: String,
    val modifierKey: String,
    val petSpeciesId: Int,
    val craftedQuantity: Int,
    val listingQuantity: Long?,
    val outputUnitPrice: Long?,
    val outputP25Price: Long?,
    val outputP75Price: Long?,
    val reagentCost: Long?,
    val profitCopper: Long?,
    val roiPercent: Double?,
    val outputPriceChangePercent: Double?,
    val profitChangePercent: Double?,
    val reagentsFullyPriced: Boolean,
    val recipeName: String?,
    val recipeMediaUrl: String?,
    val itemName: String,
    val itemMediaUrl: String?,
    val qualityId: Int?,
    val qualityType: String?,
    val qualityName: String?,
    val itemClassId: Int?,
    val itemClassName: String?,
    val itemSubclassId: Int?,
    val itemSubclassName: String?,
    val professionId: Int?,
    val professionName: String?,
    val skillTierName: String?,
    val professionCategoryName: String?,
)

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
        )

    fun search(request: CraftingMarketSearchRequest): CraftingMarketSearchResult {
        val params = ArrayList<Any?>()
        val withSql = buildWithSql(request, params)
        val whereSql = buildWhereSql(request, params)
        val offset = request.page * request.pageSize
        params.add(request.pageSize)
        params.add(offset)
        val sql = buildPagedSql(request, withSql, whereSql)
        val queryStart = System.nanoTime()
        val pairs =
            jdbcTemplate.query(
                sql,
                rowMapperWithTotal,
                *params.toTypedArray(),
            )
        val rows = pairs.map { it.first }
        val totalItems = pairs.firstOrNull()?.second ?: 0L
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
            filterOptionRowMapper,
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
            filterOptionRowMapper,
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

        return """
            WITH
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
                  AND a.item_id IN (SELECT DISTINCT rr.item_id FROM recipe_reagent rr)
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
                  AND a.item_id IN (SELECT DISTINCT rr.item_id FROM recipe_reagent rr)
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
                SELECT DISTINCT item_id FROM recipe_reagent
            ),
            reagent_unit AS (
                SELECT
                    i.item_id,
                    COALESCE(rs.price, rc.price) AS price
                FROM reagent_items i
                    LEFT JOIN reagent_sel rs ON rs.item_id = i.item_id
                    LEFT JOIN reagent_com rc ON rc.item_id = i.item_id
            ),
            recipe_reagent_agg AS (
                SELECT
                    r.id AS recipe_id,
                    SUM(
                        CASE
                            WHEN rr.internal_id IS NULL THEN 0
                            WHEN ru.price IS NULL THEN 1
                            ELSE 0
                        END
                    ) AS missing_reagents,
                    SUM(
                        CASE
                            WHEN rr.internal_id IS NULL THEN 0
                            ELSE COALESCE(ru.price, 0) * rr.quantity
                        END
                    ) AS reagent_cost_partial
                FROM recipe r
                    LEFT JOIN recipe_reagent rr ON rr.recipe_id = r.id
                    LEFT JOIN reagent_unit ru ON ru.item_id = rr.item_id
                WHERE r.crafted_item_id IS NOT NULL
                GROUP BY r.id
            ),
            recipe_reagent_cost AS (
                SELECT
                    recipe_id,
                    CASE WHEN missing_reagents > 0 THEN NULL ELSE reagent_cost_partial END AS reagent_cost,
                    missing_reagents = 0 AS reagents_fully_priced
                FROM recipe_reagent_agg
            ),
            realm_outputs AS (
                SELECT
                    r.id AS recipe_id,
                    r.crafted_item_id,
                    COALESCE(NULLIF(r.crafted_quantity, 0), 1) AS crafted_qty,
                    a.bonus_key,
                    a.modifier_key,
                    COALESCE(a.pet_species_id, -1) AS pet_species_id,
                    a.buyout AS output_unit_price,
                    a.p25 AS output_p25_price,
                    a.p75 AS output_p75_price,
                    a.quantity AS listing_quantity
                FROM recipe r
                    INNER JOIN auction a
                        ON a.item_id = r.crafted_item_id
                        AND a.connected_realm_id = ?
                        AND a.buyout IS NOT NULL
                    INNER JOIN sel_latest_history lh ON lh.update_history_id = a.update_history_id
                WHERE r.crafted_item_id IS NOT NULL
            ),
            com_outputs AS (
                SELECT
                    r.id AS recipe_id,
                    r.crafted_item_id,
                    COALESCE(NULLIF(r.crafted_quantity, 0), 1) AS crafted_qty,
                    a.bonus_key,
                    a.modifier_key,
                    COALESCE(a.pet_species_id, -1) AS pet_species_id,
                    a.buyout AS output_unit_price,
                    a.p25 AS output_p25_price,
                    a.p75 AS output_p75_price,
                    a.quantity AS listing_quantity
                FROM recipe r
                    INNER JOIN auction a
                        ON a.item_id = r.crafted_item_id
                        AND a.connected_realm_id = ?
                        AND a.buyout IS NOT NULL
                    INNER JOIN com_latest_history lh ON lh.update_history_id = a.update_history_id
                WHERE r.crafted_item_id IS NOT NULL
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
                    r.id AS recipe_id,
                    r.crafted_item_id,
                    COALESCE(NULLIF(r.crafted_quantity, 0), 1) AS crafted_qty,
                    '' AS bonus_key,
                    '' AS modifier_key,
                    0 AS pet_species_id,
                    CAST(NULL AS UNSIGNED) AS output_unit_price,
                    CAST(NULL AS UNSIGNED) AS output_p25_price,
                    CAST(NULL AS UNSIGNED) AS output_p75_price,
                    CAST(NULL AS SIGNED) AS listing_quantity
                FROM recipe r
                WHERE r.crafted_item_id IS NOT NULL
                  AND r.id NOT IN (SELECT recipe_id FROM recipes_with_listing)
            ),
            crafted_current AS (
                SELECT * FROM listed_outputs
                UNION ALL
                SELECT * FROM unlisted_outputs
            ),
            prev_realm AS (
                SELECT
                    r.id AS recipe_id,
                    ash.bonus_key,
                    ash.modifier_key,
                    ash.pet_species_id,
                    ash.$priceSel AS prev_unit_price
                FROM recipe r
                    INNER JOIN auction_stats_hourly ash
                        ON ash.item_id = r.crafted_item_id
                        AND ash.connected_realm_id = ?
                        AND ash.date = ?
                        AND ash.$priceSel IS NOT NULL
                WHERE r.crafted_item_id IS NOT NULL
            ),
            prev_com AS (
                SELECT
                    r.id AS recipe_id,
                    ash.bonus_key,
                    ash.modifier_key,
                    ash.pet_species_id,
                    ash.$priceCom AS prev_unit_price
                FROM recipe r
                    INNER JOIN auction_stats_hourly ash
                        ON ash.item_id = r.crafted_item_id
                        AND ash.connected_realm_id = ?
                        AND ash.date = ?
                        AND ash.$priceCom IS NOT NULL
                WHERE r.crafted_item_id IS NOT NULL
            ),
            crafted_prev AS (
                SELECT recipe_id, bonus_key, modifier_key, pet_species_id, prev_unit_price FROM prev_realm
                UNION ALL
                SELECT c.recipe_id, c.bonus_key, c.modifier_key, c.pet_species_id, c.prev_unit_price
                FROM prev_com c
                WHERE NOT EXISTS (
                    SELECT 1 FROM prev_realm pr
                    WHERE pr.recipe_id = c.recipe_id
                      AND pr.bonus_key <=> c.bonus_key
                      AND pr.modifier_key <=> c.modifier_key
                      AND pr.pet_species_id <=> c.pet_species_id
                )
            ),
            recipe_dim AS (
                SELECT
                    reci.id AS recipe_id,
                    reci.crafted_item_id,
                    COALESCE(NULLIF(reci.crafted_quantity, 0), 1) AS crafted_quantity,
                    reci.media_url AS recipe_media_url,
                    COALESCE(reci_l.$loc, reci_l.en_gb, reci_l.en_us) AS recipe_name,
                    p.id AS profession_id,
                    COALESCE(p_l.$loc, p_l.en_gb, p_l.en_us) AS profession_name,
                    COALESCE(st_l.$loc, st_l.en_gb, st_l.en_us) AS skill_tier_name,
                    COALESCE(pc_l.$loc, pc_l.en_gb, pc_l.en_us) AS profession_category_name
                FROM recipe reci
                    LEFT JOIN profession_category pc ON pc.internal_id = reci.profession_category_id
                    LEFT JOIN locale pc_l ON pc_l.id = pc.name_id
                    LEFT JOIN skill_tier st ON st.id = pc.skill_tier_id
                    LEFT JOIN locale st_l ON st_l.id = st.name_id
                    LEFT JOIN profession p ON p.id = st.profession_id
                    LEFT JOIN locale p_l ON p_l.id = p.name_id
                    LEFT JOIN locale reci_l ON reci_l.id = reci.name_id
                WHERE reci.crafted_item_id IS NOT NULL
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
                    d.expansion_id
                FROM crafted_current cc
                    INNER JOIN recipe_dim rd ON rd.recipe_id = cc.recipe_id
                    LEFT JOIN recipe_reagent_cost rrc ON rrc.recipe_id = cc.recipe_id
                    LEFT JOIN crafted_prev cp
                        ON cp.recipe_id = cc.recipe_id
                        AND cp.bonus_key <=> cc.bonus_key
                        AND cp.modifier_key <=> cc.modifier_key
                        AND cp.pet_species_id <=> cc.pet_species_id
                    LEFT JOIN v_auction_market_item_details d
                        ON d.item_id = cc.crafted_item_id
                        AND d.recipe_id = cc.recipe_id
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

    private fun buildPagedSql(
        request: CraftingMarketSearchRequest,
        withSql: String,
        whereSql: String,
    ): String =
        """
        $withSql
        SELECT
            wrapped.recipe_id,
            wrapped.crafted_item_id,
            wrapped.bonus_key,
            wrapped.modifier_key,
            wrapped.pet_species_id,
            wrapped.crafted_quantity,
            wrapped.listing_quantity,
            wrapped.output_unit_price,
            wrapped.output_p25_price,
            wrapped.output_p75_price,
            wrapped.reagent_cost,
            wrapped.profit_copper,
            wrapped.roi_percent,
            wrapped.output_price_change_percent,
            wrapped.profit_change_percent,
            wrapped.reagents_fully_priced,
            wrapped.recipe_name,
            wrapped.recipe_media_url,
            wrapped.profession_id,
            wrapped.profession_name,
            wrapped.skill_tier_name,
            wrapped.profession_category_name,
            wrapped.item_name,
            wrapped.item_media_url,
            wrapped.quality_id,
            wrapped.quality_type,
            wrapped.quality_name,
            wrapped.item_class_id,
            wrapped.item_class_name,
            wrapped.item_subclass_id,
            wrapped.item_subclass_name,
            wrapped.total_items
        FROM (
            SELECT
                c.recipe_id,
                c.crafted_item_id,
                c.bonus_key,
                c.modifier_key,
                c.pet_species_id,
                c.crafted_quantity,
                c.listing_quantity,
                c.output_unit_price,
                c.output_p25_price,
                c.output_p75_price,
                c.reagent_cost,
                CASE WHEN c.reagents_fully_priced THEN c.profit_copper ELSE NULL END AS profit_copper,
                CASE WHEN c.reagents_fully_priced THEN c.roi_percent ELSE NULL END AS roi_percent,
                c.output_price_change_percent,
                CASE WHEN c.reagents_fully_priced THEN c.profit_change_percent ELSE NULL END AS profit_change_percent,
                c.reagents_fully_priced,
                c.recipe_name,
                c.recipe_media_url,
                c.profession_id,
                c.profession_name,
                c.skill_tier_name,
                c.profession_category_name,
                c.item_name,
                c.item_media_url,
                c.quality_id,
                c.quality_type,
                c.quality_name,
                c.item_class_id,
                c.item_class_name,
                c.item_subclass_id,
                c.item_subclass_name,
                COUNT(*) OVER () AS total_items
            FROM computed c
            $whereSql
        ) wrapped
        ${buildOrderBySql(request)}
        LIMIT ? OFFSET ?
        """.trimIndent()

    private fun buildOrderBySql(request: CraftingMarketSearchRequest): String {
        val dir = if (request.sortDirection.equals("desc", ignoreCase = true)) "DESC" else "ASC"
        val primary =
            when (request.sortBy) {
                "reagentCost", "outputPrice", "profit", "listingQuantity" -> {
                    val col = sortColumns.getValue(request.sortBy)
                    "((wrapped.$col) IS NULL) ASC, wrapped.$col $dir"
                }
                "roiPercent", "outputPriceChangePercent", "profitChangePercent" -> {
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
        val itemNameCol = "c.item_name"
        val recipeNameCol = "c.recipe_name"

        request.query?.trim()?.takeIf { it.isNotEmpty() }?.let {
            predicates.add("($itemNameCol LIKE ? ESCAPE '!' OR $recipeNameCol LIKE ? ESCAPE '!')")
            val like = "%${it.escapeLike()}%"
            params.add(like)
            params.add(like)
        }
        if (request.professionIds.isNotEmpty()) {
            predicates.add("c.profession_id IN (${request.professionIds.joinToString(",") { "?" }})")
            params.addAll(request.professionIds)
        }
        if (request.expansionIds.isNotEmpty()) {
            predicates.add("c.expansion_id IN (${request.expansionIds.joinToString(",") { "?" }})")
            params.addAll(request.expansionIds)
        }
        if (request.requireCompleteReagentPricing) {
            predicates.add("c.reagents_fully_priced = 1")
        }
        request.minProfit?.let {
            predicates.add("c.profit_copper IS NOT NULL AND c.profit_copper >= ?")
            params.add(it)
        }
        request.maxProfit?.let {
            predicates.add("c.profit_copper IS NOT NULL AND c.profit_copper <= ?")
            params.add(it)
        }
        request.minRoiPercent?.let {
            predicates.add("c.roi_percent IS NOT NULL AND c.roi_percent >= ?")
            params.add(it)
        }
        request.maxRoiPercent?.let {
            predicates.add("c.roi_percent IS NOT NULL AND c.roi_percent <= ?")
            params.add(it)
        }
        request.minReagentCost?.let {
            predicates.add("c.reagent_cost IS NOT NULL AND c.reagent_cost >= ?")
            params.add(it)
        }
        request.maxReagentCost?.let {
            predicates.add("c.reagent_cost IS NOT NULL AND c.reagent_cost <= ?")
            params.add(it)
        }
        request.minOutputPrice?.let {
            predicates.add("c.output_unit_price IS NOT NULL AND c.output_unit_price >= ?")
            params.add(it)
        }
        request.maxOutputPrice?.let {
            predicates.add("c.output_unit_price IS NOT NULL AND c.output_unit_price <= ?")
            params.add(it)
        }
        request.minOutputPriceChangePercent?.let {
            predicates.add("c.output_price_change_percent IS NOT NULL AND c.output_price_change_percent >= ?")
            params.add(it)
        }
        request.maxOutputPriceChangePercent?.let {
            predicates.add("c.output_price_change_percent IS NOT NULL AND c.output_price_change_percent <= ?")
            params.add(it)
        }
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

    private val rowMapper =
        RowMapper { rs: ResultSet, _: Int ->
            CraftingMarketSqlRow(
                recipeId = rs.getInt("recipe_id"),
                craftedItemId = rs.getInt("crafted_item_id"),
                bonusKey = rs.getString("bonus_key") ?: "",
                modifierKey = rs.getString("modifier_key") ?: "",
                petSpeciesId = rs.getInt("pet_species_id"),
                craftedQuantity = rs.getInt("crafted_quantity"),
                listingQuantity = rs.getNullableLong("listing_quantity"),
                outputUnitPrice = rs.getNullableLong("output_unit_price"),
                outputP25Price = rs.getNullableLong("output_p25_price"),
                outputP75Price = rs.getNullableLong("output_p75_price"),
                reagentCost = rs.getNullableLong("reagent_cost"),
                profitCopper = rs.getNullableLong("profit_copper"),
                roiPercent = rs.getNullableDouble("roi_percent"),
                outputPriceChangePercent = rs.getNullableDouble("output_price_change_percent"),
                profitChangePercent = rs.getNullableDouble("profit_change_percent"),
                reagentsFullyPriced = rs.getBoolean("reagents_fully_priced"),
                recipeName = rs.getString("recipe_name"),
                recipeMediaUrl = rs.getString("recipe_media_url"),
                itemName = rs.getString("item_name") ?: "",
                itemMediaUrl = rs.getString("item_media_url"),
                qualityId = rs.getNullableInt("quality_id"),
                qualityType = rs.getString("quality_type"),
                qualityName = rs.getString("quality_name"),
                itemClassId = rs.getNullableInt("item_class_id"),
                itemClassName = rs.getString("item_class_name"),
                itemSubclassId = rs.getNullableInt("item_subclass_id"),
                itemSubclassName = rs.getString("item_subclass_name"),
                professionId = rs.getNullableInt("profession_id"),
                professionName = rs.getString("profession_name"),
                skillTierName = rs.getString("skill_tier_name"),
                professionCategoryName = rs.getString("profession_category_name"),
            )
        }

    private val rowMapperWithTotal =
        RowMapper { rs: ResultSet, rowNum: Int ->
            val totalItems = rs.getLong("total_items")
            val row = requireNotNull(rowMapper.mapRow(rs, rowNum)) { "crafting row expected" }
            row to totalItems
        }

    private val filterOptionRowMapper =
        RowMapper { rs: ResultSet, _: Int ->
            AuctionMarketFilterOptionRow(
                id = rs.getString("id"),
                label = rs.getString("label") ?: rs.getString("id"),
                parentId = rs.getString("parent_id"),
            )
        }

    private fun ResultSet.getNullableInt(column: String): Int? {
        val value = getInt(column)
        return if (wasNull()) null else value
    }

    private fun ResultSet.getNullableLong(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }

    private fun ResultSet.getNullableDouble(column: String): Double? {
        val value = getDouble(column)
        return if (wasNull()) null else value
    }
}
