package net.jonasmf.auctionengine.repository.rds

/**
 * Shared SQL fragments for recipe reagent pricing.
 *
 * When [recipe_reagent_rank] rows exist for a reagent, the priced item is the rank-specific
 * [recipe_reagent_rank.item_id] matching the recipe's output quality rank
 * ([RecipeReagentPricingSql.targetRankExpr]). Otherwise the base [v_recipe_reagent.item_id] is used.
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

    fun pricingItemIdExpr(
        reagentAlias: String = "rr",
        rankExpr: String,
    ): String =
        """
        COALESCE(
            (
                SELECT rrk.item_id
                FROM recipe_reagent_rank rrk
                WHERE rrk.recipe_reagent_id = $reagentAlias.internal_id
                  AND rrk.rank = $rankExpr
                LIMIT 1
            ),
            $reagentAlias.item_id
        )
        """.trimIndent()

    fun recipeReagentLinesCte(
        recipeSource: String = "v_recipe",
        recipeOutputsCte: String = "recipe_outputs",
        rankExpr: String = targetRankExpr("r"),
    ): String =
        """
        recipe_reagent_lines AS (
            SELECT
                r.id AS recipe_id,
                $rankExpr AS target_rank,
                rr.internal_id AS recipe_reagent_id,
                rr.quantity,
                ${pricingItemIdExpr(rankExpr = rankExpr)} AS pricing_item_id
            FROM $recipeSource r
                INNER JOIN $recipeOutputsCte ro ON ro.recipe_id = r.id
                LEFT JOIN v_recipe_reagent rr ON rr.recipe_id = r.id
        )
        """.trimIndent()

    fun recipeReagentLinesForRecipesCte(recipeIdFilterSql: String): String =
        """
        recipe_reagent_lines AS (
            SELECT
                rr.recipe_id,
                ${targetRankExpr("r")} AS target_rank,
                rr.internal_id AS recipe_reagent_id,
                rr.item_id,
                rr.quantity,
                ${pricingItemIdExpr(rankExpr = targetRankExpr("r"))} AS pricing_item_id
            FROM v_recipe_reagent rr
                INNER JOIN v_recipe r ON r.id = rr.recipe_id
            WHERE $recipeIdFilterSql
        )
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
}
