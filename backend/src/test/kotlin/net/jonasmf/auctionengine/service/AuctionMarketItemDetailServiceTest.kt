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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException

class AuctionMarketItemDetailServiceTest : IntegrationTestBase() {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var service: AuctionMarketItemDetailService

    @Test
    fun `item detail returns reagent prices and crafting profit using crafted quantity`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)
        addSecondRecipeForHealingPotion()

        val detail =
            service.itemDetail(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19019,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
                scope = "realm",
                localeOverride = null,
                preferredRecipeId = 7001,
            )

        assertEquals(2, detail.craftings.size)
        assertEquals(7001, detail.craftings.first().recipeId)
        assertEquals(listOf(1, 2), detail.craftings.map { it.recipeRank }.sortedBy { it })
        assertEquals(1, detail.item.recipe?.rank)

        val base = detail.craftings.single { it.recipeId == 7001 }
        assertEquals(1, base.craftedQuantity)
        assertTrue(base.reagentsFullyPriced)
        assertEquals(100L, base.reagentCost)
        assertEquals(1000L, base.outputUnitPrice)
        assertEquals(900L, base.profit)
        assertEquals(900.0, base.roiPercent!!, 0.01)
        assertEquals(1, base.reagents.size)
        assertEquals(19050, base.reagents.single().itemId)
        assertEquals("Peacebloom", base.reagents.single().name)
        assertEquals(50L, base.reagents.single().unitPrice)
        assertEquals(100L, base.reagents.single().lineTotal)
        assertTrue(base.reagents.single().priced)
        assertTrue(
            detail.hourlySeriesRealm.any { it.avgPrice != null },
            "hourly realm series should include pricing values when hourly auction stats are present",
        )

        val bulk = detail.craftings.single { it.recipeId == 7004 }
        assertEquals(2, bulk.craftedQuantity)
        assertEquals(50L, bulk.reagentCost)
        assertEquals(1950L, bulk.profit)
    }

    @Test
    fun `item detail returns TSM saleRate and soldPerDay for request region`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.seedTsmItemMetric(jdbcTemplate)

        val detail =
            service.itemDetail(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19019,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
                scope = "realm",
                localeOverride = null,
            )

        assertEquals(0.25, detail.saleRate!!, 0.0000001)
        assertEquals(1.5, detail.soldPerDay!!, 0.0000001)
    }

    @Test
    fun `item detail uses rank-specific reagent item ids when configured`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)
        addSecondRecipeForHealingPotion()
        addRankTwoReagentVariantPricing()

        val detail =
            service.itemDetail(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19019,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
                scope = "realm",
                localeOverride = null,
                preferredRecipeId = 7004,
            )

        val rankTwo = detail.craftings.single { it.recipeId == 7004 }
        assertEquals(200L, rankTwo.reagentCost)
        assertEquals(19053, rankTwo.reagents.single().itemId)
        assertEquals("Goldthorn", rankTwo.reagents.single().name)
        assertEquals(2, rankTwo.reagents.single().purchaseRank)
        assertEquals(200L, rankTwo.reagents.single().unitPrice)
    }

    @Test
    fun `item detail treats petSpeciesId -1 as rollup for non-pet items`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)

        val petZero =
            service.itemDetail(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19019,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
                scope = "realm",
                localeOverride = null,
            )
        val petMinusOne =
            service.itemDetail(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19019,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = -1,
                scope = "realm",
                localeOverride = null,
            )

        assertEquals(petZero.summary.selectedRealmPrice, petMinusOne.summary.selectedRealmPrice)
        assertEquals(petZero.summary.selectedRealmQuantity, petMinusOne.summary.selectedRealmQuantity)
        assertTrue(petMinusOne.hourlySeriesRealm.any { it.avgPrice != null })
    }

    @Test
    fun `item detail exposes current realm listings from latest auction price rows`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)

        val detail =
            service.itemDetail(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19019,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
                scope = "realm",
                localeOverride = null,
            )

        assertEquals(listOf(950L, 1_200L), detail.currentListings.map { it.price })
        assertEquals(listOf(1, 3), detail.currentListings.map { it.quantity })
    }

    @Test
    fun `commodity scope item detail exposes current commodity listings`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)

        val detail =
            service.itemDetail(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19019,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
                scope = "commodity",
                localeOverride = null,
            )

        assertEquals(listOf(880L, 930L), detail.currentListings.map { it.price })
        assertEquals(listOf(3, 5), detail.currentListings.map { it.quantity })
    }

    @Test
    fun `item detail current listings filter variants only when variant key is explicit`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        addVariantCurrentListingForHealingPotion()

        val rollup =
            service.itemDetail(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19019,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
                scope = "realm",
                localeOverride = null,
            )
        val variant =
            service.itemDetail(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19019,
                bonusKey = "bonus-a",
                modifierKey = "mod-a",
                petSpeciesId = 123,
                scope = "realm",
                localeOverride = null,
            )

        assertEquals(listOf(950L, 1_200L, 2_222L), rollup.currentListings.map { it.price })
        assertEquals(listOf(2_222L), variant.currentListings.map { it.price })
        assertEquals(7, variant.currentListings.single().quantity)
    }

    @Test
    fun `item detail falls back to commodity reagent price`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)
        MarketSearchTestFixtures.addRecipeWithReagentPricedOnlyOnCommodityAh(jdbcTemplate)

        val detail =
            service.itemDetail(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19100,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
                scope = "realm",
                localeOverride = null,
            )

        val crafting = detail.craftings.single()
        assertEquals(7003, crafting.recipeId)
        assertTrue(crafting.reagentsFullyPriced)
        assertEquals(100L, crafting.reagentCost)
        assertEquals(900L, crafting.profit)
        assertEquals(50L, crafting.reagents.single().unitPrice)
        assertEquals(100L, crafting.reagents.single().lineTotal)
    }

    @Test
    fun `commodity scope item detail exposes hourly commodity price values`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        addCommodityOnlyCraftingFixture()

        val detail =
            service.itemDetail(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19200,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = -1,
                scope = "commodity",
                localeOverride = null,
            )

        assertTrue(
            detail.hourlySeriesCommodity.any { it.avgPrice != null },
            "hourly commodity series should include pricing values when commodity hourly stats are present",
        )
    }

    @Test
    fun `item detail keeps incomplete recipe visible with reagent lines`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)
        addIncompleteRecipeForHealingPotion()

        val detail =
            service.itemDetail(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19019,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
                scope = "realm",
                localeOverride = null,
                preferredRecipeId = 7005,
            )

        val incomplete = detail.craftings.first()
        assertEquals(7005, incomplete.recipeId)
        assertFalse(incomplete.reagentsFullyPriced)
        assertNull(incomplete.reagentCost)
        assertNull(incomplete.profit)
        assertNull(incomplete.roiPercent)
        assertEquals(1000L, incomplete.outputUnitPrice)
        assertEquals(1, incomplete.reagents.size)
        assertFalse(incomplete.reagents.single().priced)
        assertNull(incomplete.reagents.single().unitPrice)
        assertNull(incomplete.reagents.single().lineTotal)

        assertNull(
            detail.crafting,
            "deprecated single-recipe crafting field must be null when the chosen recipe lacks reagent pricing",
        )
    }

    @Test
    fun `item detail and analytics fall back to commodity output with zero crafted quantity`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        addCommodityOnlyCraftingFixture()

        val detail =
            service.itemDetail(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19200,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = -1,
                scope = "commodity",
                localeOverride = null,
            )

        val crafting = detail.craftings.single()
        assertEquals(7100, crafting.recipeId)
        assertEquals(1, crafting.craftedQuantity)
        assertTrue(crafting.reagentsFullyPriced)
        assertEquals(145L, crafting.reagentCost)
        assertEquals(190L, crafting.outputUnitPrice)
        assertEquals(45L, crafting.profit)
        assertEquals(31.034, crafting.roiPercent!!, 0.01)
        assertEquals(19199, crafting.reagents.single().itemId)
        assertEquals(145L, crafting.reagents.single().unitPrice)
        assertEquals(145L, crafting.reagents.single().lineTotal)

        val analytics =
            service.craftingAnalytics(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19200,
                recipeId = 7100,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = -1,
                localeOverride = null,
            )
        val latest = analytics.dailySeries.last()
        assertEquals(145L, latest.reagentCost)
        assertEquals(190L, latest.outputUnitPrice)
        assertEquals(45L, latest.profit)
        val latestDow = latest.statDate.dayOfWeek.value - 1
        val heatmapCell = analytics.heatmap.single { it.dayOfWeek == latestDow && it.hourOfDay == 10 }
        assertEquals(1, heatmapCell.sampleCount)
        assertEquals(45.0, heatmapCell.profit!!, 0.01)
    }

    @Test
    fun `item detail does not duplicate reagents when reagent item is itself craftable by other recipes`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)
        addExtraRecipesProducingReagentPeacebloom()

        val detail =
            service.itemDetail(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19019,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
                scope = "realm",
                localeOverride = null,
                preferredRecipeId = 7001,
            )

        val crafting = detail.craftings.single { it.recipeId == 7001 }
        assertEquals(
            1,
            crafting.reagents.size,
            "reagent rows must be unique per (recipeId, itemId) even when the reagent is the output of other recipes",
        )
        assertEquals(19050, crafting.reagents.single().itemId)
    }

    @Test
    fun `crafting analytics returns 404 when recipe does not produce the requested item`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)

        val ex =
            assertThrows<ResponseStatusException> {
                service.craftingAnalytics(
                    regionCode = "eu",
                    realmSlug = "argent-dawn",
                    itemId = 19019,
                    recipeId = 999_999,
                    bonusKey = "",
                    modifierKey = "",
                    petSpeciesId = 0,
                    localeOverride = null,
                )
            }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `crafting analytics api response has daily and heatmap economics`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.augmentMarketSearchDataForCrafting(jdbcTemplate)

        val analytics =
            service.craftingAnalytics(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19019,
                recipeId = 7001,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
                localeOverride = null,
            )

        assertEquals(14, analytics.dailySeries.size)
        val latest = analytics.dailySeries.last()
        assertEquals(100L, latest.reagentCost)
        assertEquals(1000L, latest.outputUnitPrice)
        assertEquals(900L, latest.profit)
        assertEquals(900.0, latest.roiPercent!!, 0.01)

        val latestDow = latest.statDate.dayOfWeek.value - 1
        val cell = analytics.heatmap.single { it.dayOfWeek == latestDow && it.hourOfDay == 11 }
        assertEquals(1, cell.sampleCount)
        assertEquals(900.0, cell.profit!!, 0.01)
        assertNotNull(cell.roiPercent)
    }

    @Test
    fun `item detail includes fixed daily and hourly spines even when data window is sparse`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)

        val detail =
            service.itemDetail(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                itemId = 19019,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
                scope = "realm",
                localeOverride = null,
            )

        assertEquals(14, detail.dailySeriesRealm.size, "daily series must always provide a 14-day spine")
        assertEquals(
            14 * 24,
            detail.hourlySeriesRealm.size,
            "hourly series must always provide a full 14x24 day/hour spine",
        )
    }

    private fun addSecondRecipeForHealingPotion() {
        val recipeName = insertLocale(1100, "Recipe: Bulk Healing Potion", "Rezept: Viele Heiltränke", "RECIPE", "7004", "name")
        jdbcTemplate.update(
            """
            INSERT INTO recipe (id, crafted_item_id, crafted_quantity, media_url, name_id, rank)
            VALUES (7004, 19019, 2, 'https://media.example/recipe7004.png', ?, 2)
            """.trimIndent(),
            recipeName,
        )
        jdbcTemplate.update("INSERT INTO recipe_reagent (item_id, quantity, recipe_id) VALUES (19050, 1, 7004)")
    }

    private fun addRankTwoReagentVariantPricing() {
        val rankTwoReagentName = insertLocale(1101, "Goldthorn", "Golddorn", "ITEM", "19053", "name")
        jdbcTemplate.update(
            """
            INSERT INTO item (
                id, is_equippable, is_stackable, level, max_count, media_url, purchase_price, purchase_quantity,
                required_level, sell_price, item_class_id, item_subclass_id, name_id, quality_id
            ) VALUES (19053, 0, 1, 1, 0, 'https://media.example/herb-r2.png', 0, 1, 1, 0, 2, 501, ?, 3)
            """.trimIndent(),
            rankTwoReagentName,
        )
        val reagentId =
            jdbcTemplate.queryForObject(
                "SELECT internal_id FROM recipe_reagent WHERE recipe_id = 7004 AND item_id = 19050",
                Long::class.java,
            )!!
        jdbcTemplate.update(
            """
            INSERT INTO recipe_reagent_rank (recipe_reagent_id, rank, is_override, item_id, skill_points)
            VALUES (?, 1, FALSE, 19050, NULL), (?, 2, FALSE, 19053, NULL)
            """.trimIndent(),
            reagentId,
            reagentId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction (
                id, connected_realm_id, item_id, buyout, p25, p75, quantity, last_seen, update_history_id,
                bonus_key, modifier_key, pet_species_id
            ) VALUES ('1084-current-19053', 1084, 19053, 200, 180, 220, 10, '2026-05-01 11:15:00', 1001, '', '', -1)
            """.trimIndent(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id, item_id, date, pet_species_id, modifier_key, bonus_key, price11, quantity11
            ) VALUES (1084, 19053, '2026-05-01', -1, '', '', 200, 10)
            """.trimIndent(),
        )
    }

    private fun addCommodityOnlyCraftingFixture() {
        val outputName = insertLocale(1200, "Test Copper Bar", "Test Kupferbarren", "ITEM", "19200", "name")
        val reagentName = insertLocale(1201, "Test Copper Ore", "Test Kupfererz", "ITEM", "19199", "name")
        val recipeName = insertLocale(1202, "Smelt Test Copper", "Testkupfer verhütten", "RECIPE", "7100", "name")
        jdbcTemplate.update(
            """
            INSERT INTO item (
                id, is_equippable, is_stackable, level, max_count, media_url, purchase_price, purchase_quantity,
                required_level, sell_price, item_class_id, item_subclass_id, name_id, quality_id
            ) VALUES (19200, 0, 1, 1, 0, 'https://media.example/test-bar.png', 0, 1, 1, 0, 2, 501, ?, 3)
            """.trimIndent(),
            outputName,
        )
        jdbcTemplate.update(
            """
            INSERT INTO item (
                id, is_equippable, is_stackable, level, max_count, media_url, purchase_price, purchase_quantity,
                required_level, sell_price, item_class_id, item_subclass_id, name_id, quality_id
            ) VALUES (19199, 0, 1, 1, 0, 'https://media.example/test-ore.png', 0, 1, 1, 0, 2, 501, ?, 3)
            """.trimIndent(),
            reagentName,
        )
        jdbcTemplate.update(
            """
            INSERT INTO recipe (id, crafted_item_id, crafted_quantity, media_url, name_id)
            VALUES (7100, 19200, 0, 'https://media.example/recipe7100.png', ?)
            """.trimIndent(),
            recipeName,
        )
        jdbcTemplate.update("INSERT INTO recipe_reagent (item_id, quantity, recipe_id) VALUES (19199, 1, 7100)")
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id, item_id, date, pet_species_id, modifier_key, bonus_key, price10, quantity10
            ) VALUES (-2, 19200, '2026-05-01', -1, '', '', 190, 20)
            """.trimIndent(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id, item_id, date, pet_species_id, modifier_key, bonus_key, price10, quantity10
            ) VALUES (-2, 19199, '2026-05-01', -1, '', '', 145, 30)
            """.trimIndent(),
        )
    }

    private fun addVariantCurrentListingForHealingPotion() {
        jdbcTemplate.update(
            """
            INSERT INTO auction (
                id,
                connected_realm_id,
                item_id,
                context,
                pet_breed_id,
                pet_species_id,
                pet_quality_id,
                pet_level,
                modifier_key,
                bonus_key,
                buyout,
                bid,
                p25,
                p75,
                quantity,
                first_seen,
                last_seen,
                update_history_id
            ) VALUES (
                '1084-current-19019-variant',
                1084,
                19019,
                NULL,
                NULL,
                123,
                NULL,
                NULL,
                'mod-a',
                'bonus-a',
                2222,
                NULL,
                2222,
                2222,
                7,
                '2026-05-01 11:15:00',
                '2026-05-01 11:15:00',
                1001
            )
            """.trimIndent(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction_price (
                id,
                auction_id,
                buyout,
                bid,
                quantity,
                last_modified,
                update_history_id
            ) VALUES (10000005, '1084-current-19019-variant', 2222, NULL, 7, '2026-05-01 11:15:00', 1001)
            """.trimIndent(),
        )
    }

    private fun addExtraRecipesProducingReagentPeacebloom() {
        val recipeAName = insertLocale(1300, "Recipe: Grow Peacebloom A", "Rezept: Friedensblume A", "RECIPE", "7200", "name")
        val recipeBName = insertLocale(1301, "Recipe: Grow Peacebloom B", "Rezept: Friedensblume B", "RECIPE", "7201", "name")
        jdbcTemplate.update(
            """
            INSERT INTO recipe (id, crafted_item_id, crafted_quantity, media_url, name_id)
            VALUES (7200, 19050, 1, 'https://media.example/recipe7200.png', ?)
            """.trimIndent(),
            recipeAName,
        )
        jdbcTemplate.update(
            """
            INSERT INTO recipe (id, crafted_item_id, crafted_quantity, media_url, name_id)
            VALUES (7201, 19050, 1, 'https://media.example/recipe7201.png', ?)
            """.trimIndent(),
            recipeBName,
        )
    }

    private fun addIncompleteRecipeForHealingPotion() {
        val reagentName = insertLocale(1101, "Missing Herb", "Fehlendes Kraut", "ITEM", "19060", "name")
        val recipeName = insertLocale(1102, "Recipe: Missing Herb Potion", "Rezept: Fehlkrauttrank", "RECIPE", "7005", "name")
        jdbcTemplate.update(
            """
            INSERT INTO item (
                id, is_equippable, is_stackable, level, max_count, media_url, purchase_price, purchase_quantity,
                required_level, sell_price, item_class_id, item_subclass_id, name_id, quality_id
            ) VALUES (19060, 0, 1, 1, 0, 'https://media.example/missing-herb.png', 0, 1, 1, 0, 2, 501, ?, 3)
            """.trimIndent(),
            reagentName,
        )
        jdbcTemplate.update(
            """
            INSERT INTO recipe (id, crafted_item_id, crafted_quantity, media_url, name_id)
            VALUES (7005, 19019, 1, 'https://media.example/recipe7005.png', ?)
            """.trimIndent(),
            recipeName,
        )
        jdbcTemplate.update("INSERT INTO recipe_reagent (item_id, quantity, recipe_id) VALUES (19060, 3, 7005)")
    }

    private fun insertLocale(
        id: Int,
        enGb: String,
        deDe: String,
        source: String,
        sourceId: String,
        usage: String,
    ): Long {
        jdbcTemplate.update(
            """
            INSERT INTO locale (id, en_gb, en_us, de_de, source_type, source_key, source_field)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            enGb,
            enGb,
            deDe,
            source,
            sourceId,
            usage,
        )
        return id.toLong()
    }
}
