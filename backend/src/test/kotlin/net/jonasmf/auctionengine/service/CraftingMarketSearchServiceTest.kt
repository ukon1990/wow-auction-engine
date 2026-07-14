package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.testsupport.MarketSearchTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class CraftingMarketSearchServiceTest : IntegrationTestBase() {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var craftingMarketSearchService: CraftingMarketSearchService

    @Test
    fun `crafting search returns recipe variant with profit roi and trend`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)

        val result =
            craftingMarketSearchService.search(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                localeOverride = null,
                page = 0,
                pageSize = 10,
                sortBy = "itemName",
                sortDirection = "asc",
                query = null,
                professionIds = null,
                expansionIds = null,
                qualityIds = null,
                minProfit = null,
                maxProfit = null,
                minRoiPercent = null,
                maxRoiPercent = null,
                minReagentCost = null,
                maxReagentCost = null,
                minOutputPrice = null,
                maxOutputPrice = null,
                minOutputPriceChangePercent = null,
                maxOutputPriceChangePercent = null,
                requireCompleteReagentPricing = false,
            )

        assertEquals(1L, result.page.totalItems)
        val row = result.items.single()
        assertEquals(7001, row.recipeId)
        assertEquals(1, row.recipe?.rank)
        assertEquals(1, row.item?.recipe?.rank)
        assertEquals("Healing Potion", row.item?.name)
        assertEquals(1_000L, row.outputPriceCopper)
        assertEquals(950L, row.outputP25PriceCopper)
        assertEquals(1_100L, row.outputP75PriceCopper)
        assertEquals(100L, row.reagentCostCopper)
        assertEquals(900L, row.profitCopper)
        assertNotNull(row.roiPercent)
        assertTrue(row.roiPercent!! > 800.0)
        assertNotNull(row.outputPriceChangePercent)
        assertEquals(25.0, row.outputPriceChangePercent!!, 0.01)
        assertTrue(row.reagentsFullyPriced)
        assertEquals("Alchemy", row.professionName)
        assertTrue(row.outputPriced)
        assertNull(row.profileFit)
    }

    @Test
    fun `authenticated crafting search returns documented default when no profile matches`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)

        val row = search(actorSubject = "user-without-profiles").items.single()

        assertEquals("default", row.profileFit?.state?.value)
        assertEquals("no_matching_profile_default", row.profileFit?.diagnostic?.value)
        assertNull(row.profileFit?.craftable)
        assertNull(row.profileFit?.bestCandidate)
    }

    @Test
    fun `authenticated crafting search ranks only matching owned profiles and keeps craftability unknown`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)
        jdbcTemplate.update("UPDATE item SET expansion_id = 1 WHERE id = 19019")
        val treeId = createProfessionTree()
        createCraftingProfile("owner", "Lower Skill", treeId, skillLevel = 50, allocationCount = 2)
        createCraftingProfile("owner", "Higher Skill", treeId, skillLevel = 100, allocationCount = 1)
        createCraftingProfile("other-user", "Not Yours", treeId, skillLevel = 999, allocationCount = 4)

        val fit = search(actorSubject = "owner").items.single().profileFit

        assertEquals("configured", fit?.state?.value)
        assertEquals("recipe_rules_missing", fit?.diagnostic?.value)
        assertNull(fit?.craftable)
        assertEquals("Higher Skill", fit?.bestCandidate?.characterName)
        assertEquals(listOf("Lower Skill"), fit?.alternatives?.map { it.characterName })
    }

    @Test
    fun `authenticated crafting search evaluates craftability from manual allocations rules and effects`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)
        jdbcTemplate.update("UPDATE item SET expansion_id = 1 WHERE id = 19019")
        val treeId = createProfessionTree()
        val nodeId = 5001L
        val entryId = 6001L
        jdbcTemplate.update(
            "INSERT INTO profession_skill_tree_node (id, tree_id, external_node_id, name, max_ranks, display_order) VALUES (?, ?, 501, 'Skill milestone', 1, 0)",
            nodeId,
            treeId,
        )
        jdbcTemplate.update(
            "INSERT INTO profession_skill_tree_entry (id, node_id, external_entry_id, name, rank_limit, display_order) VALUES (?, ?, 601, 'Waist mastery', 1, 0)",
            entryId,
            nodeId,
        )
        jdbcTemplate.update(
            "INSERT INTO profession_skill_tree_node_effect (node_id, effect_type, skill_bonus, crafting_category, unlock_rank, source) VALUES (?, 'SKILL_BONUS', 35, NULL, 0, 'description')",
            nodeId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO recipe_crafting_rule (recipe_id, base_skill, quality_thresholds)
            VALUES (7001, 50, '[80, 100, 120]')
            """.trimIndent(),
        )
        jdbcTemplate.update(
            "INSERT INTO user_character (owner_subject, region, realm_name, character_name) VALUES ('owner', 'eu', 'Argent Dawn', 'Crafter')",
        )
        val characterId = jdbcTemplate.queryForObject("SELECT id FROM user_character WHERE character_name = 'Crafter'", Long::class.java)!!
        jdbcTemplate.update(
            "INSERT INTO user_character_profession_profile (character_id, profession_id, tree_id, skill_level) VALUES (?, 50, ?, 85)",
            characterId,
            treeId,
        )
        val profileId = jdbcTemplate.queryForObject("SELECT id FROM user_character_profession_profile WHERE character_id = ?", Long::class.java, characterId)!!
        jdbcTemplate.update(
            "INSERT INTO user_character_profession_allocation (profile_id, entry_id, rank) VALUES (?, ?, 1)",
            profileId,
            entryId,
        )

        val fit = search(actorSubject = "owner").items.single().profileFit

        assertEquals("profile_evaluated", fit?.diagnostic?.value)
        assertEquals(true, fit?.craftable)
        assertEquals("Crafter", fit?.bestCandidate?.characterName)
        assertEquals(1, fit?.bestCandidate?.predictedQuality)
    }

    @Test
    fun `crafting search keeps recipes with different ranks distinct`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)
        addRankTwoHealingPotionRecipe()

        val result = search()

        assertEquals(listOf(1, 2), result.items.mapNotNull { it.recipe?.rank }.sorted())
        assertEquals(listOf(7001, 7004), result.items.map { it.recipeId }.sorted())
    }

    @Test
    fun `crafting search prices reagents from commodity when realm has no listing`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)
        MarketSearchTestFixtures.addRecipeWithReagentPricedOnlyOnCommodityAh(jdbcTemplate)

        val result =
            craftingMarketSearchService.search(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                localeOverride = null,
                page = 0,
                pageSize = 10,
                sortBy = "itemName",
                sortDirection = "asc",
                query = null,
                professionIds = null,
                expansionIds = null,
                qualityIds = null,
                minProfit = null,
                maxProfit = null,
                minRoiPercent = null,
                maxRoiPercent = null,
                minReagentCost = null,
                maxReagentCost = null,
                minOutputPrice = null,
                maxOutputPrice = null,
                minOutputPriceChangePercent = null,
                maxOutputPriceChangePercent = null,
                requireCompleteReagentPricing = false,
            )

        assertEquals(2L, result.page.totalItems)
        val row = result.items.single { it.recipeId == 7003 }
        assertEquals(100L, row.reagentCostCopper)
        assertEquals(900L, row.profitCopper)
        assertTrue(row.reagentsFullyPriced)
        assertTrue(row.outputPriced)
    }

    @Test
    fun `crafting search returns recipe row when crafted output has no auction listing`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)
        MarketSearchTestFixtures.addRecipeWithUnlistedCraftedOutput(jdbcTemplate)

        val result =
            craftingMarketSearchService.search(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                localeOverride = null,
                page = 0,
                pageSize = 50,
                sortBy = "itemName",
                sortDirection = "asc",
                query = null,
                professionIds = null,
                expansionIds = null,
                qualityIds = null,
                minProfit = null,
                maxProfit = null,
                minRoiPercent = null,
                maxRoiPercent = null,
                minReagentCost = null,
                maxReagentCost = null,
                minOutputPrice = null,
                maxOutputPrice = null,
                minOutputPriceChangePercent = null,
                maxOutputPriceChangePercent = null,
                requireCompleteReagentPricing = false,
            )

        assertEquals(2L, result.page.totalItems)
        val unlisted = result.items.single { it.recipeId == 7002 }
        assertNull(unlisted.outputPriceCopper)
        assertFalse(unlisted.outputPriced)
        assertEquals(50L, unlisted.reagentCostCopper)
        assertTrue(unlisted.reagentsFullyPriced)
        assertNull(unlisted.profitCopper)
    }

    @Test
    fun `crafting search treats crafted_quantity zero as one for profit computation`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)
        MarketSearchTestFixtures.addRecipeWithZeroCraftedQuantity(jdbcTemplate)

        val result =
            craftingMarketSearchService.search(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                localeOverride = null,
                page = 0,
                pageSize = 10,
                sortBy = "itemName",
                sortDirection = "asc",
                query = null,
                professionIds = null,
                expansionIds = null,
                qualityIds = null,
                minProfit = null,
                maxProfit = null,
                minRoiPercent = null,
                maxRoiPercent = null,
                minReagentCost = null,
                maxReagentCost = null,
                minOutputPrice = null,
                maxOutputPrice = null,
                minOutputPriceChangePercent = null,
                maxOutputPriceChangePercent = null,
                requireCompleteReagentPricing = false,
            )

        val zeroQtyRow = result.items.single { it.recipeId == 7777 }
        assertEquals(1_000L, zeroQtyRow.outputPriceCopper)
        assertEquals(100L, zeroQtyRow.reagentCostCopper)
        assertEquals(900L, zeroQtyRow.profitCopper)
        assertNotNull(zeroQtyRow.roiPercent)
        assertTrue(zeroQtyRow.roiPercent!! > 800.0)
    }

    @Test
    fun `crafting filters rejects inverted profit range`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)

        assertThrows<ResponseStatusException> {
            craftingMarketSearchService.search(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                localeOverride = null,
                page = 0,
                pageSize = 10,
                sortBy = "itemName",
                sortDirection = "asc",
                query = null,
                professionIds = null,
                expansionIds = null,
                qualityIds = null,
                minProfit = 100L,
                maxProfit = 50L,
                minRoiPercent = null,
                maxRoiPercent = null,
                minReagentCost = null,
                maxReagentCost = null,
                minOutputPrice = null,
                maxOutputPrice = null,
                minOutputPriceChangePercent = null,
                maxOutputPriceChangePercent = null,
                requireCompleteReagentPricing = false,
            )
        }
    }

    @Test
    fun `crafting filters include expansion options`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)

        val result = craftingMarketSearchService.filters("eu", "argent-dawn", null)

        val expansion = result.filters.single { it.id == "expansionIds" }
        assertTrue(expansion.options.orEmpty().any { it.id == "1" && it.label == "Vanilla" })
    }

    @Test
    fun `crafting filters include quality options in logical order`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)

        val result = craftingMarketSearchService.filters("eu", "argent-dawn", null)

        val quality = result.filters.single { it.id == "qualityIds" }
        assertTrue(quality.options.orEmpty().isNotEmpty())
        assertEquals("RARE", quality.options.orEmpty().single().qualityType)
    }

    @Test
    fun `crafting search filters by expansion id`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)
        jdbcTemplate.update("UPDATE item SET expansion_id = 1 WHERE id = 19019")

        val matching =
            craftingMarketSearchService.search(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                localeOverride = null,
                page = 0,
                pageSize = 10,
                sortBy = "itemName",
                sortDirection = "asc",
                query = null,
                professionIds = null,
                expansionIds = listOf(1),
                qualityIds = null,
                minProfit = null,
                maxProfit = null,
                minRoiPercent = null,
                maxRoiPercent = null,
                minReagentCost = null,
                maxReagentCost = null,
                minOutputPrice = null,
                maxOutputPrice = null,
                minOutputPriceChangePercent = null,
                maxOutputPriceChangePercent = null,
                requireCompleteReagentPricing = false,
            )
        assertEquals(1L, matching.page.totalItems)

        val nonMatching =
            craftingMarketSearchService.search(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                localeOverride = null,
                page = 0,
                pageSize = 10,
                sortBy = "itemName",
                sortDirection = "asc",
                query = null,
                professionIds = null,
                expansionIds = listOf(2),
                qualityIds = null,
                minProfit = null,
                maxProfit = null,
                minRoiPercent = null,
                maxRoiPercent = null,
                minReagentCost = null,
                maxReagentCost = null,
                minOutputPrice = null,
                maxOutputPrice = null,
                minOutputPriceChangePercent = null,
                maxOutputPriceChangePercent = null,
                requireCompleteReagentPricing = false,
            )
        assertEquals(0L, nonMatching.page.totalItems)
    }

    private fun search(actorSubject: String? = null) =
        craftingMarketSearchService.search(
            regionCode = "eu",
            realmSlug = "argent-dawn",
            localeOverride = null,
            page = 0,
            pageSize = 10,
            sortBy = "itemName",
            sortDirection = "asc",
            query = null,
            professionIds = null,
            expansionIds = null,
            qualityIds = null,
            minProfit = null,
            maxProfit = null,
            minRoiPercent = null,
            maxRoiPercent = null,
            minReagentCost = null,
            maxReagentCost = null,
            minOutputPrice = null,
            maxOutputPrice = null,
            minOutputPriceChangePercent = null,
            maxOutputPriceChangePercent = null,
            requireCompleteReagentPricing = false,
            actorSubject = actorSubject,
        )

    private fun createProfessionTree(): Long {
        jdbcTemplate.update(
            "INSERT INTO profession_tree_import (source_type, content_hash) VALUES ('test', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')",
        )
        jdbcTemplate.update(
            "INSERT INTO profession_skill_tree (expansion_id, profession_id, external_tree_id, name, import_id) VALUES (1, 50, 1, 'Alchemy', 1)",
        )
        return jdbcTemplate.queryForObject("SELECT id FROM profession_skill_tree WHERE external_tree_id = 1", Long::class.java)!!
    }

    private fun createCraftingProfile(
        subject: String,
        characterName: String,
        treeId: Long,
        skillLevel: Int,
        allocationCount: Int,
    ) {
        jdbcTemplate.update(
            "INSERT INTO user_character (owner_subject, region, realm_name, character_name) VALUES (?, 'eu', 'Argent Dawn', ?)",
            subject,
            characterName,
        )
        val characterId = jdbcTemplate.queryForObject("SELECT id FROM user_character WHERE owner_subject = ? AND character_name = ?", Long::class.java, subject, characterName)!!
        jdbcTemplate.update(
            "INSERT INTO user_character_profession_profile (character_id, profession_id, tree_id, skill_level) VALUES (?, 50, ?, ?)",
            characterId,
            treeId,
            skillLevel,
        )
        val profileId = jdbcTemplate.queryForObject("SELECT id FROM user_character_profession_profile WHERE character_id = ?", Long::class.java, characterId)!!
        repeat(allocationCount) { index ->
            val nodeId = 1000L + characterId * 10 + index
            val entryId = 2000L + characterId * 10 + index
            jdbcTemplate.update("INSERT INTO profession_skill_tree_node (id, tree_id, external_node_id, name, max_ranks, display_order) VALUES (?, ?, ?, 'Node', 1, ?)", nodeId, treeId, nodeId, index)
            jdbcTemplate.update("INSERT INTO profession_skill_tree_entry (id, node_id, external_entry_id, name, rank_limit, display_order) VALUES (?, ?, ?, 'Entry', 1, ?)", entryId, nodeId, entryId, index)
            jdbcTemplate.update("INSERT INTO user_character_profession_allocation (profile_id, entry_id, rank) VALUES (?, ?, 1)", profileId, entryId)
        }
    }

    private fun addRankTwoHealingPotionRecipe() {
        jdbcTemplate.update(
            """
            INSERT INTO locale (id, en_gb, en_us, de_de, source_type, source_key, source_field)
            VALUES (1100, 'Recipe: Greater Healing Potion', 'Recipe: Greater Healing Potion',
                    'Rezept: Großer Heiltrank', 'RECIPE', '7004', 'name')
            """.trimIndent(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO recipe (id, crafted_item_id, crafted_quantity, media_url, name_id, rank)
            VALUES (7004, 19019, 2, 'https://media.example/recipe7004.png', 1100, 2)
            """.trimIndent(),
        )
        jdbcTemplate.update("INSERT INTO recipe_reagent (item_id, quantity, recipe_id) VALUES (19050, 1, 7004)")
    }
}
