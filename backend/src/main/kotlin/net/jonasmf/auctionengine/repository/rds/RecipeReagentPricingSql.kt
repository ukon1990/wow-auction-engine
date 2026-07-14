package net.jonasmf.auctionengine.repository.rds

/**
 * Shared SQL fragments for recipe reagent pricing.
 *
 * When [recipe_reagent_rank] rows exist for a reagent, the priced item is the cheapest rank-specific
 * variant with rank >= the recipe output quality rank. Otherwise the base [v_recipe_reagent.item_id] is used.
 */
internal object RecipeReagentPricingSql {
    fun allPricingItemIdsSql(): String =
        """
        SELECT DISTINCT item_id
        FROM (
            SELECT rr.item_id
            FROM v_recipe_reagent rr
            UNION ALL
            SELECT rrk.item_id
            FROM recipe_reagent_rank rrk
                INNER JOIN v_recipe_reagent rr ON rr.internal_id = rrk.recipe_reagent_id
        ) pricing_items
        """.trimIndent()

    fun targetRankExpr(recipeAlias: String = "r"): String = "COALESCE(NULLIF($recipeAlias.rank, 0), 1)"

    fun craftingTargetRankExpr(
        recipeAlias: String = "r",
        recipeOutputAlias: String = "ro",
        recipeOutputsCte: String = "recipe_outputs",
    ): String =
        """
        CASE
            WHEN (
                SELECT COUNT(*)
                FROM $recipeOutputsCte output_count
                WHERE output_count.recipe_id = $recipeAlias.id
            ) > 1 THEN $recipeOutputAlias.sort_order + 1
            ELSE ${targetRankExpr(recipeAlias)}
        END
        """.trimIndent()

    fun recipeReagentLinesCte(
        recipeSource: String = "v_recipe",
        recipeOutputsCte: String = "recipe_outputs",
        rankExpr: String = targetRankExpr("r"),
        priceCte: String = "reagent_unit",
    ): String =
        cheapestValidReagentLinesFromSeed(
            seedCteSql =
                """
                SELECT
                    r.id AS recipe_id,
                    $rankExpr AS target_rank,
                    rr.internal_id AS recipe_reagent_id,
                    rr.item_id AS slot_item_id,
                    rr.quantity
                FROM $recipeSource r
                    INNER JOIN $recipeOutputsCte ro ON ro.recipe_id = r.id
                    LEFT JOIN v_recipe_reagent rr ON rr.recipe_id = r.id
                """.trimIndent(),
            priceCte = priceCte,
            includePurchaseRank = false,
        )

    fun recipeReagentLinesForCraftedItemCte(
        recipeIdFilterSql: String,
        priceCte: String = "reagent_price",
    ): String =
        """
        recipe_target_ranks AS (
            SELECT
                ro.recipe_id,
                ${craftingTargetRankExpr(recipeAlias = "r", recipeOutputAlias = "ro", recipeOutputsCte = "v_recipe_crafted_output")} AS target_rank
            FROM v_recipe_crafted_output ro
                INNER JOIN v_recipe r ON r.id = ro.recipe_id
            WHERE ro.crafted_item_id = ?
              AND ($recipeIdFilterSql)
        ),
        ${cheapestValidReagentLinesFromSeed(
            seedCteSql =
                """
                SELECT
                    rr.recipe_id,
                    rtr.target_rank,
                    rr.internal_id AS recipe_reagent_id,
                    rr.item_id AS slot_item_id,
                    rr.quantity
                FROM v_recipe_reagent rr
                    INNER JOIN recipe_target_ranks rtr ON rtr.recipe_id = rr.recipe_id
                """.trimIndent(),
            priceCte = priceCte,
            includePurchaseRank = true,
        )}
        """.trimIndent()

    fun recipeReagentCostCte(reagentUnitCte: String = "reagent_unit"): String =
        """
        recipe_reagent_agg AS (
            SELECT
                rl.recipe_id,
                rl.target_rank,
                SUM(
                    CASE
                        WHEN rl.recipe_reagent_id IS NULL THEN 0
                        WHEN ru.price IS NULL THEN 1
                        ELSE 0
                    END
                ) AS missing_reagents,
                SUM(
                    CASE
                        WHEN rl.recipe_reagent_id IS NULL THEN 0
                        ELSE COALESCE(ru.price, 0) * rl.quantity
                    END
                ) AS reagent_cost_partial
            FROM recipe_reagent_lines rl
                LEFT JOIN $reagentUnitCte ru ON ru.item_id = rl.pricing_item_id
            GROUP BY rl.recipe_id, rl.target_rank
        ),
        recipe_reagent_cost AS (
            SELECT
                recipe_id,
                target_rank,
                CASE WHEN missing_reagents > 0 THEN NULL ELSE reagent_cost_partial END AS reagent_cost,
                missing_reagents = 0 AS reagents_fully_priced
            FROM recipe_reagent_agg
        )
        """.trimIndent()

    private fun cheapestValidReagentLinesFromSeed(
        seedCteSql: String,
        priceCte: String,
        includePurchaseRank: Boolean,
    ): String {
        val purchaseRankColumn = if (includePurchaseRank) ",\n                purchase_rank" else ""
        return """
        reagent_line_seed AS (
            $seedCteSql
        ),
        reagent_rank_candidates AS (
            SELECT
                seed.recipe_id,
                seed.target_rank,
                seed.recipe_reagent_id,
                seed.quantity,
                variant.rank AS purchase_rank,
                COALESCE(variant.item_id, seed.slot_item_id) AS pricing_item_id,
                COALESCE(rp.price, 9223372036854775807) AS unit_price
            FROM reagent_line_seed seed
                LEFT JOIN recipe_reagent_rank variant
                    ON variant.recipe_reagent_id = seed.recipe_reagent_id
                    AND variant.rank >= seed.target_rank
                LEFT JOIN $priceCte rp ON rp.item_id = COALESCE(variant.item_id, seed.slot_item_id)
            WHERE seed.recipe_reagent_id IS NOT NULL
        ),
        reagent_rank_picked AS (
            SELECT
                recipe_id,
                target_rank,
                recipe_reagent_id,
                quantity,
                pricing_item_id,
                purchase_rank,
                ROW_NUMBER() OVER (
                    PARTITION BY recipe_id, target_rank, recipe_reagent_id
                    ORDER BY unit_price ASC, purchase_rank ASC
                ) AS rn
            FROM reagent_rank_candidates
        ),
        recipe_reagent_lines AS (
            SELECT
                recipe_id,
                target_rank,
                recipe_reagent_id,
                quantity,
                pricing_item_id$purchaseRankColumn
            FROM reagent_rank_picked
            WHERE rn = 1
        )
        """.trimIndent()
    }
}
