package net.jonasmf.auctionengine.repository.rds

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecipeReagentPricingSqlTest {
    @Test
    fun `pricing fragments include rank-aware item resolution`() {
        val lines = RecipeReagentPricingSql.recipeReagentLinesCte()
        assertTrue(lines.contains("recipe_reagent_rank"))
        assertTrue(lines.contains("pricing_item_id"))

        val cost = RecipeReagentPricingSql.recipeReagentCostCte()
        assertTrue(cost.contains("target_rank"))
        assertTrue(cost.contains("pricing_item_id"))
    }
}
