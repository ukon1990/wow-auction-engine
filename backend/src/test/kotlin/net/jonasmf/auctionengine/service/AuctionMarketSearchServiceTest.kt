package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.testsupport.MarketSearchTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
                .commodity
                ?.connectedRealmId,
        )
        assertEquals(
            10,
            result.items
                .single()
                .commodity
                ?.hourOfDay,
        )
        assertEquals(
            900L,
            result.items
                .single()
                .commodity
                ?.price,
        )
    }

    @Test
    fun `search includes commodity-only items with null selected realm price`() {
        seedMarketSearchData()
        MarketSearchTestFixtures.seedCommodityOnlyItem(jdbcTemplate)

        val result =
            service.search(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                localeOverride = null,
                page = 0,
                pageSize = 10,
                sortBy = "itemName",
                sortDirection = "asc",
                query = "copper dust",
                qualityIds = null,
                itemClassIds = null,
                itemSubclassIds = null,
                recipeOnly = null,
                minPrice = null,
                maxPrice = null,
                minQuantity = null,
                maxQuantity = null,
            )

        val row = result.items.single { it.item.id == 19020 }
        assertNull(row.selectedRealm?.price)
        assertNull(row.selectedRealm?.quantity)
        assertEquals(555L, row.commodity?.price)
        assertEquals(99L, row.commodity?.quantity)
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
        val priceFilter = result.filters.single { it.id == "price" }
        assertEquals(null, priceFilter.min)
        assertEquals(null, priceFilter.max)
        val quantityFilter = result.filters.single { it.id == "quantity" }
        assertEquals(null, quantityFilter.min)
        assertEquals(null, quantityFilter.max)
    }

    @Test
    fun `filters include lookup values not present in current market result set`() {
        seedMarketSearchData()
        val extraQualityName = insertLocale(20, "Epic", "Episch", "ITEM_QUALITY", "EPIC", "name")
        val extraClassName = insertLocale(21, "Armor", "Ruestung", "ITEM_CLASS", "4", "name")
        val extraSubclassName = insertLocale(22, "Mail", "Kette", "ITEM_SUBCLASS", "4:3", "displayName")
        jdbcTemplate.update("INSERT INTO item_quality (internal_id, type, name_id) VALUES (4, 'EPIC', ?)", extraQualityName)
        jdbcTemplate.update("INSERT INTO item_class (id, name_id) VALUES (4, ?)", extraClassName)
        jdbcTemplate.update(
            """
            INSERT INTO item_subclass (
                internal_id, class_id, hide_subclass_in_tooltips, subclass_id, display_name_id, item_class_owner_id
            ) VALUES (610, 4, 0, 3, ?, 4)
            """.trimIndent(),
            extraSubclassName,
        )

        val result = service.filters("eu", "argent-dawn", "de_DE")

        val quality = result.filters.single { it.id == "qualityIds" }
        assertTrue(quality.options.orEmpty().any { it.id == "4" && it.label == "Episch" })
        val itemClass = result.filters.single { it.id == "itemClassIds" }
        assertTrue(itemClass.options.orEmpty().any { it.id == "4" && it.label == "Ruestung" })
        val itemSubclass = result.filters.single { it.id == "itemSubclassIds" }
        assertTrue(itemSubclass.options.orEmpty().any { it.id == "3" && it.label == "Kette" && it.parentId == "4" })
    }

    private fun seedMarketSearchData() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
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
