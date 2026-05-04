package net.jonasmf.auctionengine.testsupport

import org.springframework.jdbc.core.JdbcTemplate

object MarketSearchTestFixtures {
    fun seedMarketSearchData(jdbcTemplate: JdbcTemplate) {
        jdbcTemplate.update("INSERT INTO region (id, name, type) VALUES (1, 'Europe', 1)")
        insertAuctionHouse(jdbcTemplate, id = 100, connectedId = 1084, lastModified = "2026-05-01 11:15:00")
        insertAuctionHouse(jdbcTemplate, id = 101, connectedId = -2, lastModified = "2026-05-01 10:30:00")
        jdbcTemplate.update("INSERT INTO connected_realm (id, auction_house_id) VALUES (1084, 100), (-2, 101)")
        jdbcTemplate.update(
            """
            INSERT INTO realm (id, category, game_build, locale, name, slug, timezone, region_id)
            VALUES (200, 'normal', 0, 5, 'Argent Dawn', 'argent-dawn', 'UTC', 1)
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
