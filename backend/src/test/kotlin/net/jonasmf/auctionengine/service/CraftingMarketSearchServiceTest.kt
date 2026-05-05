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
        assertEquals("Healing Potion", row.item?.name)
        assertEquals(1_000L, row.outputPriceCopper)
        assertEquals(100L, row.reagentCostCopper)
        assertEquals(900L, row.profitCopper)
        assertNotNull(row.roiPercent)
        assertTrue(row.roiPercent!! > 800.0)
        assertNotNull(row.outputPriceChangePercent)
        assertEquals(25.0, row.outputPriceChangePercent!!, 0.01)
        assertTrue(row.reagentsFullyPriced)
        assertEquals("Alchemy", row.professionName)
        assertTrue(row.outputPriced)
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
}
