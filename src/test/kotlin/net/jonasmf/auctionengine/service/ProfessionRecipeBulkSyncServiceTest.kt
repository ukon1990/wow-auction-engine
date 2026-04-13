package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingCategory
import net.jonasmf.auctionengine.domain.profession.ModifiedCraftingSlot
import net.jonasmf.auctionengine.domain.profession.Profession
import net.jonasmf.auctionengine.domain.profession.ProfessionCategory
import net.jonasmf.auctionengine.domain.profession.Recipe
import net.jonasmf.auctionengine.domain.profession.RecipeReagent
import net.jonasmf.auctionengine.domain.profession.SkillTier
import net.jonasmf.auctionengine.dto.LocaleDTO
import net.jonasmf.auctionengine.repository.rds.RecipeRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class ProfessionRecipeBulkSyncServiceTest : IntegrationTestBase() {
    @Autowired
    lateinit var professionRecipeBulkSyncService: ProfessionRecipeBulkSyncService

    @Autowired
    lateinit var recipeRepository: RecipeRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `repeated sync does not duplicate profession recipe graph or metadata`() {
        val profession = professionWithRecipe(100, 1000)
        val recipe = recipeDetail(1000, reagentIds = listOf(2000, 2001), includeSlot = true)
        val category = ModifiedCraftingCategory(3000, locale("Optional Category"))
        val slot = ModifiedCraftingSlot(4000, locale("Optional Slot"), listOf(category))

        professionRecipeBulkSyncService.sync(listOf(profession), listOf(recipe), listOf(category), listOf(slot))
        val localeRowsAfterFirstSync = countRows("locale")

        professionRecipeBulkSyncService.sync(listOf(profession), listOf(recipe), listOf(category), listOf(slot))

        assertEquals(localeRowsAfterFirstSync, countRows("locale"))
        assertEquals(1, countRows("profession"))
        assertEquals(1, countRows("skill_tier"))
        assertEquals(1, countRows("profession_category"))
        assertEquals(1, countRows("recipe"))
        assertEquals(2, countRows("recipe_reagent"))
        assertEquals(1, countRows("modified_crafting_slot"))
        assertEquals(1, countRows("modified_crafting_category"))
        assertEquals(1, countRows("modified_crafting_category_metadata"))
        assertEquals(1, countRows("modified_crafting_slot_metadata"))
        assertEquals(1, countRows("modified_crafting_slot_metadata_category"))
        assertEquals(1, countRowsWhere("locale", "source_type = 'profession' AND source_key = '100' AND source_field = 'name'"))
        assertEquals(1, countRowsWhere("locale", "source_type = 'recipe' AND source_key = '1000' AND source_field = 'name'"))
    }

    @Test
    fun `sync removes stale recipe reagents and slots on rerun`() {
        val profession = professionWithRecipe(101, 1001)
        val category = ModifiedCraftingCategory(3001, locale("Optional Category"))
        val slot = ModifiedCraftingSlot(4001, locale("Optional Slot"), listOf(category))

        professionRecipeBulkSyncService.sync(
            professions = listOf(profession),
            recipes = listOf(recipeDetail(1001, reagentIds = listOf(2002, 2003), includeSlot = true)),
            modifiedCraftingCategories = listOf(category),
            modifiedCraftingSlots = listOf(slot),
        )
        professionRecipeBulkSyncService.sync(
            professions = listOf(profession),
            recipes = listOf(recipeDetail(1001, reagentIds = listOf(2002), includeSlot = false)),
            modifiedCraftingCategories = emptyList(),
            modifiedCraftingSlots = emptyList(),
        )

        assertEquals(1, countRows("recipe_reagent"))
        assertEquals(0, countRows("modified_crafting_slot"))
        assertEquals(0, countRows("modified_crafting_category"))
        assertEquals(0, countRows("modified_crafting_category_metadata"))
        assertEquals(0, countRows("modified_crafting_slot_metadata"))
    }

    @Test
    fun `sync persists data queryable for downstream item id extraction`() {
        val profession = professionWithRecipe(102, 1002)
        val recipe = recipeDetail(1002, reagentIds = listOf(2004, 2005), includeSlot = false)

        professionRecipeBulkSyncService.sync(listOf(profession), listOf(recipe), emptyList(), emptyList())

        assertEquals(listOf(2004, 2005), recipeRepository.findDistinctReagentItemIds().sorted())
        assertEquals(listOf(11002), recipeRepository.findDistinctCraftedItemIds().sorted())
        assertEquals(listOf(2004, 2005, 11002), recipeRepository.findDistinctReferencedItemIds())
    }

    private fun professionWithRecipe(
        professionId: Int,
        recipeId: Int,
    ): Profession =
        Profession(
            id = professionId,
            name = locale("Profession $professionId"),
            description = locale("Profession $professionId desc"),
            mediaUrl = "https://example.test/profession/$professionId",
            skillTiers =
                listOf(
                    SkillTier(
                        id = professionId + 10,
                        name = locale("Tier $professionId"),
                        minimumSkillLevel = 1,
                        maximumSkillLevel = 100,
                        categories =
                            listOf(
                                ProfessionCategory(
                                    name = locale("Category $professionId"),
                                    recipes = listOf(Recipe(id = recipeId, name = locale("Recipe $recipeId"))),
                                ),
                            ),
                    ),
                ),
        )

    private fun recipeDetail(
        recipeId: Int,
        reagentIds: List<Int>,
        includeSlot: Boolean,
    ): Recipe =
        Recipe(
            id = recipeId,
            name = locale("Recipe $recipeId"),
            description = locale("Recipe $recipeId desc"),
            craftedItemId = recipeId + 10_000,
            craftedQuantity = 1,
            reagents = reagentIds.map { reagentId -> RecipeReagent(reagentId, locale("Item $reagentId"), 2) },
            modifiedCraftingSlots =
                if (includeSlot) {
                    listOf(
                        ModifiedCraftingSlot(
                            id = 4000 + (recipeId - 1000),
                            description = locale("Slot $recipeId"),
                            displayOrder = 0,
                        ),
                    )
                } else {
                    emptyList()
                },
        )

    private fun locale(value: String) = LocaleDTO(en_US = value, en_GB = value)

    private fun countRows(tableName: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName", Int::class.java)!!

    private fun countRowsWhere(
        tableName: String,
        condition: String,
    ): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName WHERE $condition", Int::class.java)!!
}
