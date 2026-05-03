package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class AuctionMarketSearchServiceTest : IntegrationTestBase() {
    @Autowired
    lateinit var service: AuctionMarketSearchService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `search derives locale and snapshots from selected realm and auction houses`() {
        seedMarketSearchData()

        val result =
            service.search(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                localeOverride = null,
                page = 0,
                pageSize = 10,
                sortBy = "itemName",
                sortDirection = "asc",
                query = "potion",
                qualityIds = null,
                itemClassIds = null,
                itemSubclassIds = null,
                recipeOnly = null,
                minPrice = null,
                maxPrice = null,
                minQuantity = null,
                maxQuantity = null,
            )

        assertEquals(1, result.page.totalItems)
        assertEquals(
            "Healing Potion",
            result.items
                .single()
                .item.name,
        )
        assertEquals(
            "https://media.example/item.png",
            result.items
                .single()
                .item.mediaUrl,
        )
        assertEquals(
            2,
            result.items
                .single()
                .item.itemClass
                ?.id,
        )
        assertEquals(
            7,
            result.items
                .single()
                .item.itemSubclass
                ?.id,
        )
        assertEquals(
            7001,
            result.items
                .single()
                .item.recipe
                ?.id,
        )
        assertEquals(
            1084,
            result.items
                .single()
                .selectedRealm
                ?.connectedRealmId,
        )
        assertEquals(
            11,
            result.items
                .single()
                .selectedRealm
                ?.hourOfDay,
        )
        assertEquals(
            1_000L,
            result.items
                .single()
                .selectedRealm
                ?.price,
        )
        assertEquals(
            -2,
            result.items
                .single()
                .community
                ?.connectedRealmId,
        )
        assertEquals(
            10,
            result.items
                .single()
                .community
                ?.hourOfDay,
        )
        assertEquals(
            900L,
            result.items
                .single()
                .community
                ?.price,
        )
    }

    @Test
    fun `search supports explicit locale override filters and pagination totals`() {
        seedMarketSearchData()

        val result =
            service.search(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                localeOverride = "de_DE",
                page = 0,
                pageSize = 10,
                sortBy = "itemName",
                sortDirection = "asc",
                query = "heiltrank",
                qualityIds = listOf(3),
                itemClassIds = listOf(2),
                itemSubclassIds = listOf(7),
                recipeOnly = true,
                minPrice = 500,
                maxPrice = 1_500,
                minQuantity = 1,
                maxQuantity = 10,
            )

        assertEquals(1, result.page.totalItems)
        assertEquals(
            "Heiltrank",
            result.items
                .single()
                .item.name,
        )
        assertEquals(1, result.page.totalPages)
    }

    @Test
    fun `filters are derived from database values`() {
        seedMarketSearchData()

        val result = service.filters("eu", "argent-dawn", null)

        val quality = result.filters.single { it.id == "qualityIds" }
        assertTrue(quality.options.orEmpty().any { it.id == "3" && it.label == "Rare" })
        val itemSubclass = result.filters.single { it.id == "itemSubclassIds" }
        assertTrue(itemSubclass.options.orEmpty().any { it.id == "7" && it.parentId == "2" })
        assertNotNull(result.filters.single { it.id == "price" }.min)
    }

    private fun seedMarketSearchData() {
        insertRegion()
        insertAuctionHouse(id = 100, connectedId = 1084, lastModified = "2026-05-01 11:15:00")
        insertAuctionHouse(id = 101, connectedId = -2, lastModified = "2026-05-01 10:30:00")
        jdbcTemplate.update("INSERT INTO connected_realm (id, auction_house_id) VALUES (1084, 100), (-2, 101)")
        jdbcTemplate.update(
            """
            INSERT INTO realm (id, category, game_build, locale, name, slug, timezone, region_id)
            VALUES (200, 'normal', 0, 5, 'Argent Dawn', 'argent-dawn', 'UTC', 1)
            """.trimIndent(),
        )
        jdbcTemplate.update("INSERT INTO connected_realm_realms (connected_realm_id, realms_id) VALUES (1084, 200)")

        val itemName = insertLocale(1, "Healing Potion", "Heiltrank", "ITEM", "19019", "name")
        val qualityName = insertLocale(2, "Rare", "Selten", "ITEM_QUALITY", "RARE", "name")
        val className = insertLocale(3, "Consumable", "Verbrauchbar", "ITEM_CLASS", "2", "name")
        val subclassName = insertLocale(4, "Potion", "Trank", "ITEM_SUBCLASS", "2:7", "displayName")
        val recipeName = insertLocale(5, "Recipe: Healing Potion", "Rezept: Heiltrank", "RECIPE", "7001", "name")

        jdbcTemplate.update("INSERT INTO item_quality (internal_id, type, name_id) VALUES (3, 'RARE', ?)", qualityName)
        jdbcTemplate.update("INSERT INTO item_class (id, name_id) VALUES (2, ?)", className)
        jdbcTemplate.update(
            """
            INSERT INTO item_subclass (
                internal_id, class_id, hide_subclass_in_tooltips, subclass_id, display_name_id, item_class_owner_id
            ) VALUES (501, 2, 0, 7, ?, 2)
            """.trimIndent(),
            subclassName,
        )
        jdbcTemplate.update(
            """
            INSERT INTO item (
                id, is_equippable, is_stackable, level, max_count, media_url, purchase_price, purchase_quantity,
                required_level, sell_price, item_class_id, item_subclass_id, name_id, quality_id
            ) VALUES (19019, 0, 1, 1, 0, 'https://media.example/item.png', 0, 1, 1, 0, 2, 501, ?, 3)
            """.trimIndent(),
            itemName,
        )
        jdbcTemplate.update(
            """
            INSERT INTO recipe (id, crafted_item_id, crafted_quantity, media_url, name_id)
            VALUES (7001, 19019, 1, 'https://media.example/recipe.png', ?)
            """.trimIndent(),
            recipeName,
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id, item_id, date, pet_species_id, modifier_key, bonus_key, price11, quantity11
            ) VALUES (1084, 19019, '2026-05-01', -1, '', '', 1000, 4)
            """.trimIndent(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id, item_id, date, pet_species_id, modifier_key, bonus_key, price10, quantity10
            ) VALUES (-2, 19019, '2026-05-01', -1, '', '', 900, 8)
            """.trimIndent(),
        )
    }

    private fun insertRegion() {
        jdbcTemplate.update("INSERT INTO region (id, name, type) VALUES (1, 'Europe', 1)")
    }

    private fun insertAuctionHouse(
        id: Int,
        connectedId: Int,
        lastModified: String,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO auction_house (
                id, auto_update, avg_delay, connected_id, game_build, highest_delay, last_modified, lowest_delay,
                next_update, region, stats_last_modified, update_attempts
            ) VALUES (?, 1, 60, ?, 0, 60, ?, 60, ?, 'Europe', 0, 0)
            """.trimIndent(),
            id,
            connectedId,
            lastModified,
            lastModified,
        )
    }

    private fun insertLocale(
        id: Long,
        enGb: String,
        deDe: String,
        sourceType: String,
        sourceKey: String,
        sourceField: String,
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
            sourceType,
            sourceKey,
            sourceField,
        )
        return id
    }
}
