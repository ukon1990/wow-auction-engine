package net.jonasmf.auctionengine.testsupport

import org.springframework.jdbc.core.JdbcTemplate

object MarketSearchTestFixtures {
    fun seedMarketSearchData(jdbcTemplate: JdbcTemplate) {
        jdbcTemplate.update("INSERT INTO region (id, name, type) VALUES (2, 'Europe', 1)")
        insertAuctionHouse(jdbcTemplate, id = 100, connectedId = 1084, lastModified = "2026-05-01 11:15:00")
        insertAuctionHouse(jdbcTemplate, id = 101, connectedId = -2, lastModified = "2026-05-01 10:30:00")
        jdbcTemplate.update("INSERT INTO connected_realm (id, auction_house_id) VALUES (1084, 100), (-2, 101)")
        insertUpdateHistory(jdbcTemplate, id = 1_000, connectedRealmId = 1084, lastModified = "2026-04-30 11:15:00")
        insertUpdateHistory(jdbcTemplate, id = 1_001, connectedRealmId = 1084, lastModified = "2026-05-01 11:15:00")
        insertUpdateHistory(jdbcTemplate, id = 2_000, connectedRealmId = -2, lastModified = "2026-04-30 10:30:00")
        insertUpdateHistory(jdbcTemplate, id = 2_001, connectedRealmId = -2, lastModified = "2026-05-01 10:30:00")
        jdbcTemplate.update(
            """
            INSERT INTO realm (id, category, game_build, locale, name, slug, timezone, region_id)
            VALUES (200, 'normal', 0, 5, 'Argent Dawn', 'argent-dawn', 'UTC', 2)
            """.trimIndent(),
        )
        jdbcTemplate.update("INSERT INTO connected_realm_realms (connected_realm_id, realms_id) VALUES (1084, 200)")

        val itemName = insertLocale(jdbcTemplate, 1, "Healing Potion", "Heiltrank", "ITEM", "19019", "name")
        val qualityName = insertLocale(jdbcTemplate, 2, "Rare", "Selten", "ITEM_QUALITY", "RARE", "name")
        val className = insertLocale(jdbcTemplate, 3, "Consumable", "Verbrauchbar", "ITEM_CLASS", "2", "name")
        val subclassName = insertLocale(jdbcTemplate, 4, "Potion", "Trank", "ITEM_SUBCLASS", "2:7", "displayName")
        val recipeName = insertLocale(jdbcTemplate, 5, "Recipe: Healing Potion", "Rezept: Heiltrank", "RECIPE", "7001", "name")

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
        insertAuction(
            jdbcTemplate,
            id = "1084-old-19019",
            connectedRealmId = 1084,
            itemId = 19019,
            buyout = 777,
            quantity = 1,
            lastSeen = "2026-04-30 11:15:00",
            updateHistoryId = 1_000,
        )
        insertAuction(
            jdbcTemplate,
            id = "1084-current-19019",
            connectedRealmId = 1084,
            itemId = 19019,
            buyout = 1_000,
            quantity = 4,
            lastSeen = "2026-05-01 11:15:00",
            updateHistoryId = 1_001,
        )
        insertAuction(
            jdbcTemplate,
            id = "-2-old-19019",
            connectedRealmId = -2,
            itemId = 19019,
            buyout = 444,
            quantity = 2,
            lastSeen = "2026-04-30 10:30:00",
            updateHistoryId = 2_000,
        )
        insertAuction(
            jdbcTemplate,
            id = "-2-current-19019",
            connectedRealmId = -2,
            itemId = 19019,
            buyout = 900,
            quantity = 8,
            lastSeen = "2026-05-01 10:30:00",
            updateHistoryId = 2_001,
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

    /**
     * Adds profession metadata, a reagent, and prior-day pricing so [CraftingMarketSearchService] integration tests
     * can run against the same realm as [seedMarketSearchData].
     */
    fun augmentMarketSearchDataForCrafting(jdbcTemplate: JdbcTemplate) {
        val profName = insertLocale(jdbcTemplate, 100, "Alchemy", "Alchemie", "PROFESSION", "50", "name")
        val tierName = insertLocale(jdbcTemplate, 101, "Classic", "Klassisch", "SKILL_TIER", "600", "name")
        val catName = insertLocale(jdbcTemplate, 102, "Outland", "Scherbenwelt", "PROF_CAT", "9001", "name")
        jdbcTemplate.update("INSERT INTO profession (id, name_id) VALUES (50, ?)", profName)
        jdbcTemplate.update(
            """
            INSERT INTO skill_tier (id, maximum_skill_level, minimum_skill_level, name_id, profession_id)
            VALUES (600, 375, 1, ?, 50)
            """.trimIndent(),
            tierName,
        )
        jdbcTemplate.update(
            """
            INSERT INTO profession_category (internal_id, name_id, skill_tier_id)
            VALUES (9001, ?, 600)
            """.trimIndent(),
            catName,
        )
        jdbcTemplate.update("UPDATE recipe SET profession_category_id = 9001 WHERE id = 7001")

        val reagentName = insertLocale(jdbcTemplate, 103, "Peacebloom", "Friedensblume", "ITEM", "19050", "name")
        jdbcTemplate.update(
            """
            INSERT INTO item (
                id, is_equippable, is_stackable, level, max_count, media_url, purchase_price, purchase_quantity,
                required_level, sell_price, item_class_id, item_subclass_id, name_id, quality_id
            ) VALUES (19050, 0, 1, 1, 0, 'https://media.example/herb.png', 0, 1, 1, 0, 2, 501, ?, 3)
            """.trimIndent(),
            reagentName,
        )
        jdbcTemplate.update(
            """
            INSERT INTO recipe_reagent (item_id, quantity, recipe_id)
            VALUES (19050, 2, 7001)
            """.trimIndent(),
        )
        insertAuction(
            jdbcTemplate,
            id = "1084-old-19050",
            connectedRealmId = 1084,
            itemId = 19050,
            buyout = 10,
            quantity = 5,
            lastSeen = "2026-04-30 11:15:00",
            updateHistoryId = 1_000,
        )
        insertAuction(
            jdbcTemplate,
            id = "1084-current-19050",
            connectedRealmId = 1084,
            itemId = 19050,
            buyout = 50,
            quantity = 20,
            lastSeen = "2026-05-01 11:15:00",
            updateHistoryId = 1_001,
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id, item_id, date, pet_species_id, modifier_key, bonus_key, price11, quantity11
            ) VALUES (1084, 19050, '2026-05-01', -1, '', '', 50, 20)
            """.trimIndent(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id, item_id, date, pet_species_id, modifier_key, bonus_key, price11, quantity11
            ) VALUES (1084, 19019, '2026-04-30', -1, '', '', 800, 3)
            """.trimIndent(),
        )
    }

    /**
     * Adds recipe 7003: crafted item has realm listings; its reagent exists **only** on the commodity AH
     * (`price10` on connected realm -2), with **no** `auction_stats_hourly` row on the selected realm (1084).
     * Use after [augmentMarketSearchDataForCrafting]. Insert-only — never deletes auction rows.
     */
    fun addRecipeWithReagentPricedOnlyOnCommodityAh(jdbcTemplate: JdbcTemplate) {
        val reagentName = insertLocale(jdbcTemplate, 106, "Silverleaf", "Silberblatt", "ITEM", "19052", "name")
        val craftedName = insertLocale(jdbcTemplate, 107, "Silver Tonic", "Silbertinktur", "ITEM", "19100", "name")
        val recipeName = insertLocale(jdbcTemplate, 108, "Recipe: Silver Tonic", "Rezept: Silbertinktur", "RECIPE", "7003", "name")
        jdbcTemplate.update(
            """
            INSERT INTO item (
                id, is_equippable, is_stackable, level, max_count, media_url, purchase_price, purchase_quantity,
                required_level, sell_price, item_class_id, item_subclass_id, name_id, quality_id
            ) VALUES (19052, 0, 1, 1, 0, 'https://media.example/herb2.png', 0, 1, 1, 0, 2, 501, ?, 3)
            """.trimIndent(),
            reagentName,
        )
        jdbcTemplate.update(
            """
            INSERT INTO item (
                id, is_equippable, is_stackable, level, max_count, media_url, purchase_price, purchase_quantity,
                required_level, sell_price, item_class_id, item_subclass_id, name_id, quality_id
            ) VALUES (19100, 0, 1, 1, 0, 'https://media.example/tonic.png', 0, 1, 1, 0, 2, 501, ?, 3)
            """.trimIndent(),
            craftedName,
        )
        jdbcTemplate.update(
            """
            INSERT INTO recipe (id, crafted_item_id, crafted_quantity, media_url, name_id, profession_category_id)
            VALUES (7003, 19100, 1, 'https://media.example/recipe7003.png', ?, 9001)
            """.trimIndent(),
            recipeName,
        )
        jdbcTemplate.update(
            """
            INSERT INTO recipe_reagent (item_id, quantity, recipe_id)
            VALUES (19052, 2, 7003)
            """.trimIndent(),
        )
        insertAuction(
            jdbcTemplate,
            id = "1084-old-19100",
            connectedRealmId = 1084,
            itemId = 19100,
            buyout = 600,
            quantity = 2,
            lastSeen = "2026-04-30 11:15:00",
            updateHistoryId = 1_000,
        )
        insertAuction(
            jdbcTemplate,
            id = "1084-current-19100",
            connectedRealmId = 1084,
            itemId = 19100,
            buyout = 1_000,
            quantity = 4,
            lastSeen = "2026-05-01 11:15:00",
            updateHistoryId = 1_001,
        )
        insertAuction(
            jdbcTemplate,
            id = "-2-old-19052",
            connectedRealmId = -2,
            itemId = 19052,
            buyout = 10,
            quantity = 5,
            lastSeen = "2026-04-30 10:30:00",
            updateHistoryId = 2_000,
        )
        insertAuction(
            jdbcTemplate,
            id = "-2-current-19052",
            connectedRealmId = -2,
            itemId = 19052,
            buyout = 50,
            quantity = 20,
            lastSeen = "2026-05-01 10:30:00",
            updateHistoryId = 2_001,
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id, item_id, date, pet_species_id, modifier_key, bonus_key, price11, quantity11
            ) VALUES (1084, 19100, '2026-05-01', -1, '', '', 1000, 4)
            """.trimIndent(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id, item_id, date, pet_species_id, modifier_key, bonus_key, price11, quantity11
            ) VALUES (1084, 19100, '2026-04-30', -1, '', '', 800, 3)
            """.trimIndent(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id, item_id, date, pet_species_id, modifier_key, bonus_key, price10, quantity10
            ) VALUES (-2, 19052, '2026-05-01', -1, '', '', 50, 20)
            """.trimIndent(),
        )
    }

    /**
     * Second recipe whose crafted item has no `auction_stats_hourly` rows (still listed reagent 19050 from
     * [augmentMarketSearchDataForCrafting]).
     */
    fun addRecipeWithUnlistedCraftedOutput(jdbcTemplate: JdbcTemplate) {
        val itemName = insertLocale(jdbcTemplate, 104, "Ghost Dust", "Geisterstaub", "ITEM", "19051", "name")
        jdbcTemplate.update(
            """
            INSERT INTO item (
                id, is_equippable, is_stackable, level, max_count, media_url, purchase_price, purchase_quantity,
                required_level, sell_price, item_class_id, item_subclass_id, name_id, quality_id
            ) VALUES (19051, 0, 1, 1, 0, 'https://media.example/dust2.png', 0, 1, 1, 0, 2, 501, ?, 3)
            """.trimIndent(),
            itemName,
        )
        val recipeName = insertLocale(jdbcTemplate, 105, "Recipe: Ghost Dust", "Rezept: Geisterstaub", "RECIPE", "7002", "name")
        jdbcTemplate.update(
            """
            INSERT INTO recipe (id, crafted_item_id, crafted_quantity, media_url, name_id)
            VALUES (7002, 19051, 1, 'https://media.example/recipe2.png', ?)
            """.trimIndent(),
            recipeName,
        )
        jdbcTemplate.update(
            """
            INSERT INTO recipe_reagent (item_id, quantity, recipe_id)
            VALUES (19050, 1, 7002)
            """.trimIndent(),
        )
    }

    /**
     * Adds recipe 7777 with `crafted_quantity = 0` to exercise the COALESCE/NULLIF normalization
     * in [CraftingMarketSearchRepository]. The crafted output is listed; reagents reuse 19050 from
     * [augmentMarketSearchDataForCrafting]. With normalization, this should be treated as `1` and
     * produce a sensible profit (1000 - 100 = 900), not 0 - 100 = -100.
     *
     * Recipe id is intentionally 7777 to avoid collisions with the per-test fixtures in
     * AuctionMarketItemDetailServiceTest that already use ids in the 7001..7100 range.
     */
    fun addRecipeWithZeroCraftedQuantity(jdbcTemplate: JdbcTemplate) {
        val itemName = insertLocale(jdbcTemplate, 109, "Mana Potion", "Manatrank", "ITEM", "19101", "name")
        val recipeName = insertLocale(jdbcTemplate, 110, "Recipe: Mana Potion", "Rezept: Manatrank", "RECIPE", "7777", "name")
        jdbcTemplate.update(
            """
            INSERT INTO item (
                id, is_equippable, is_stackable, level, max_count, media_url, purchase_price, purchase_quantity,
                required_level, sell_price, item_class_id, item_subclass_id, name_id, quality_id
            ) VALUES (19101, 0, 1, 1, 0, 'https://media.example/mana.png', 0, 1, 1, 0, 2, 501, ?, 3)
            """.trimIndent(),
            itemName,
        )
        jdbcTemplate.update(
            """
            INSERT INTO recipe (id, crafted_item_id, crafted_quantity, media_url, name_id, profession_category_id)
            VALUES (7777, 19101, 0, 'https://media.example/recipe7777.png', ?, 9001)
            """.trimIndent(),
            recipeName,
        )
        jdbcTemplate.update(
            """
            INSERT INTO recipe_reagent (item_id, quantity, recipe_id)
            VALUES (19050, 2, 7777)
            """.trimIndent(),
        )
        insertAuction(
            jdbcTemplate,
            id = "1084-old-19101",
            connectedRealmId = 1084,
            itemId = 19101,
            buyout = 600,
            quantity = 2,
            lastSeen = "2026-04-30 11:15:00",
            updateHistoryId = 1_000,
        )
        insertAuction(
            jdbcTemplate,
            id = "1084-current-19101",
            connectedRealmId = 1084,
            itemId = 19101,
            buyout = 1_000,
            quantity = 5,
            lastSeen = "2026-05-01 11:15:00",
            updateHistoryId = 1_001,
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id, item_id, date, pet_species_id, modifier_key, bonus_key, price11, quantity11
            ) VALUES (1084, 19101, '2026-05-01', -1, '', '', 1000, 5)
            """.trimIndent(),
        )
    }

    /** Item present only on regional commodity (`connected_realm_id` negative), not on the selected realm. */
    fun seedCommodityOnlyItem(jdbcTemplate: JdbcTemplate) {
        val itemName = insertLocale(jdbcTemplate, 6, "Copper Dust", "Kupferstaub", "ITEM", "19020", "name")
        jdbcTemplate.update(
            """
            INSERT INTO item (
                id, is_equippable, is_stackable, level, max_count, media_url, purchase_price, purchase_quantity,
                required_level, sell_price, item_class_id, item_subclass_id, name_id, quality_id
            ) VALUES (19020, 0, 1, 1, 0, 'https://media.example/dust.png', 0, 1, 1, 0, 2, 501, ?, 3)
            """.trimIndent(),
            itemName,
        )
        insertAuction(
            jdbcTemplate,
            id = "-2-current-19020",
            connectedRealmId = -2,
            itemId = 19020,
            buyout = 555,
            quantity = 99,
            lastSeen = "2026-05-01 10:30:00",
            updateHistoryId = 2_001,
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id, item_id, date, pet_species_id, modifier_key, bonus_key, price10, quantity10
            ) VALUES (-2, 19020, '2026-05-01', -1, '', '', 555, 99)
            """.trimIndent(),
        )
    }

    private fun insertUpdateHistory(
        jdbcTemplate: JdbcTemplate,
        id: Long,
        connectedRealmId: Int,
        lastModified: String,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO connected_realm_update_history (
                id, auction_count, last_modified, update_timestamp, completed_timestamp, connected_realm_id
            ) VALUES (?, 0, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            lastModified,
            lastModified,
            lastModified,
            connectedRealmId,
        )
    }

    private fun insertAuction(
        jdbcTemplate: JdbcTemplate,
        id: String,
        connectedRealmId: Int,
        itemId: Int,
        buyout: Long,
        quantity: Int,
        lastSeen: String,
        updateHistoryId: Long,
    ) {
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
            ) VALUES (?, ?, ?, NULL, NULL, -1, NULL, NULL, '', '', ?, NULL, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            connectedRealmId,
            itemId,
            buyout,
            buyout,
            buyout,
            quantity,
            lastSeen,
            lastSeen,
            updateHistoryId,
        )
    }

    private fun insertAuctionHouse(
        jdbcTemplate: JdbcTemplate,
        id: Int,
        connectedId: Int,
        lastModified: String,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO auction_house (
                id, auto_update, avg_delay, connected_id, game_build, highest_delay, last_modified, lowest_delay,
                next_update, region, update_attempts
            ) VALUES (?, 1, 60, ?, 0, 60, ?, 60, ?, 'Europe', 0)
            """.trimIndent(),
            id,
            connectedId,
            lastModified,
            lastModified,
        )
    }

    private fun insertLocale(
        jdbcTemplate: JdbcTemplate,
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
