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
                expansionIds = null,
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
        assertEquals(1, result.items.single().item.recipe?.rank)
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
            950L,
            result.items
                .single()
                .selectedRealm
                ?.p25Price,
        )
        assertEquals(
            1_100L,
            result.items
                .single()
                .selectedRealm
                ?.p75Price,
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
        assertEquals(
            850L,
            result.items
                .single()
                .commodity
                ?.p25Price,
        )
        assertEquals(
            950L,
            result.items
                .single()
                .commodity
                ?.p75Price,
        )
        assertEquals("realm", result.items.single().preferredScope)
        assertEquals(1_000L, result.items.single().listingPrice)
        assertEquals(4L, result.items.single().listingQuantity)
        assertEquals(false, result.items.single().commodityOnly)
    }

    @Test
    fun `search returns TSM saleRate and soldPerDay for request region`() {
        seedMarketSearchData()
        MarketSearchTestFixtures.seedTsmItemMetric(jdbcTemplate)

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
                expansionIds = null,
                recipeOnly = null,
                minPrice = null,
                maxPrice = null,
                minQuantity = null,
                maxQuantity = null,
            )

        val row = result.items.single()
        assertEquals(0.25, row.saleRate!!, 0.0000001)
        assertEquals(1.5, row.soldPerDay!!, 0.0000001)
    }

    @Test
    fun `search filters by minSaleRatePercent excluding rows below threshold`() {
        seedMarketSearchData()
        MarketSearchTestFixtures.seedTsmItemMetric(jdbcTemplate, saleRate = "0.25000000")

        val matching =
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
                expansionIds = null,
                recipeOnly = null,
                minPrice = null,
                maxPrice = null,
                minQuantity = null,
                maxQuantity = null,
                minSaleRatePercent = 20.0,
            )
        assertEquals(1, matching.page.totalItems)

        val excluded =
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
                expansionIds = null,
                recipeOnly = null,
                minPrice = null,
                maxPrice = null,
                minQuantity = null,
                maxQuantity = null,
                minSaleRatePercent = 30.0,
            )
        assertEquals(0, excluded.page.totalItems)
    }

    @Test
    fun `search filters by minSoldPerDay excluding rows below threshold`() {
        seedMarketSearchData()
        MarketSearchTestFixtures.seedTsmItemMetric(jdbcTemplate, soldPerDay = "1.50000000")

        val matching =
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
                expansionIds = null,
                recipeOnly = null,
                minPrice = null,
                maxPrice = null,
                minQuantity = null,
                maxQuantity = null,
                minSoldPerDay = 1.0,
            )
        assertEquals(1, matching.page.totalItems)

        val excluded =
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
                expansionIds = null,
                recipeOnly = null,
                minPrice = null,
                maxPrice = null,
                minQuantity = null,
                maxQuantity = null,
                minSoldPerDay = 2.0,
            )
        assertEquals(0, excluded.page.totalItems)
    }

    @Test
    fun `search includes commodity-only items in unfiltered results with default itemName sort`() {
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
                query = null,
                qualityIds = null,
                itemClassIds = null,
                itemSubclassIds = null,
                expansionIds = null,
                recipeOnly = null,
                minPrice = null,
                maxPrice = null,
                minQuantity = null,
                maxQuantity = null,
            )

        assertEquals(2, result.page.totalItems)
        val commodityRow = result.items.single { it.item.id == 19020 }
        assertEquals("commodity", commodityRow.preferredScope)
        assertEquals(555L, commodityRow.listingPrice)
        assertEquals(true, commodityRow.commodityOnly)
    }

    @Test
    fun `search sorts by selectedPrice using unified listing copper`() {
        seedMarketSearchData()
        MarketSearchTestFixtures.seedCommodityOnlyItem(jdbcTemplate)

        val asc =
            service.search(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                localeOverride = null,
                page = 0,
                pageSize = 10,
                sortBy = "selectedPrice",
                sortDirection = "asc",
                query = null,
                qualityIds = null,
                itemClassIds = null,
                itemSubclassIds = null,
                expansionIds = null,
                recipeOnly = null,
                minPrice = null,
                maxPrice = null,
                minQuantity = null,
                maxQuantity = null,
            )
        assertEquals(2, asc.page.totalItems)
        assertEquals(listOf(19020, 19019), asc.items.map { it.item.id })

        val desc =
            service.search(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                localeOverride = null,
                page = 0,
                pageSize = 10,
                sortBy = "selectedPrice",
                sortDirection = "desc",
                query = null,
                qualityIds = null,
                itemClassIds = null,
                itemSubclassIds = null,
                expansionIds = null,
                recipeOnly = null,
                minPrice = null,
                maxPrice = null,
                minQuantity = null,
                maxQuantity = null,
            )
        assertEquals(listOf(19019, 19020), desc.items.map { it.item.id })
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
                expansionIds = null,
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
        assertEquals("commodity", row.preferredScope)
        assertEquals(555L, row.listingPrice)
        assertEquals(99L, row.listingQuantity)
        assertEquals(true, row.commodityOnly)
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
                expansionIds = null,
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
        val saleRateFilter = result.filters.single { it.id == "saleRatePercent" }
        assertEquals("Sale rate %", saleRateFilter.label)
        val soldPerDayFilter = result.filters.single { it.id == "soldPerDay" }
        assertEquals("Avg sold/day", soldPerDayFilter.label)
        val expansion = result.filters.single { it.id == "expansionIds" }
        assertTrue(expansion.options.orEmpty().any { it.id == "1" && it.label == "Vanilla" })
    }

    @Test
    fun `search filters by expansion id`() {
        seedMarketSearchData()
        jdbcTemplate.update("UPDATE item SET expansion_id = 1 WHERE id = 19019")

        val matching =
            service.search(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                localeOverride = null,
                page = 0,
                pageSize = 10,
                sortBy = "itemName",
                sortDirection = "asc",
                query = null,
                qualityIds = null,
                itemClassIds = null,
                itemSubclassIds = null,
                expansionIds = listOf(1),
                recipeOnly = null,
                minPrice = null,
                maxPrice = null,
                minQuantity = null,
                maxQuantity = null,
            )
        assertEquals(1, matching.page.totalItems)

        val nonMatching =
            service.search(
                regionCode = "eu",
                realmSlug = "argent-dawn",
                localeOverride = null,
                page = 0,
                pageSize = 10,
                sortBy = "itemName",
                sortDirection = "asc",
                query = null,
                qualityIds = null,
                itemClassIds = null,
                itemSubclassIds = null,
                expansionIds = listOf(2),
                recipeOnly = null,
                minPrice = null,
                maxPrice = null,
                minQuantity = null,
                maxQuantity = null,
            )
        assertEquals(0, nonMatching.page.totalItems)
    }

    @Test
    fun `quality filter options are sorted from common to artifact without duplicates`() {
        seedMarketSearchData()
        val commonName = insertLocale(30, "Common", "Gewoehnlich", "ITEM_QUALITY", "COMMON", "name")
        val uncommonName = insertLocale(31, "Uncommon", "Ungewoehnlich", "ITEM_QUALITY", "UNCOMMON", "name")
        val epicName = insertLocale(32, "Epic", "Episch", "ITEM_QUALITY", "EPIC", "name")
        val legendaryName = insertLocale(33, "Legendary", "Legendär", "ITEM_QUALITY", "LEGENDARY", "name")
        val artifactName = insertLocale(34, "Artifact", "Artefakt", "ITEM_QUALITY", "ARTIFACT", "name")
        jdbcTemplate.update("INSERT INTO item_quality (internal_id, type, name_id) VALUES (1, 'COMMON', ?)", commonName)
        jdbcTemplate.update("INSERT INTO item_quality (internal_id, type, name_id) VALUES (2, 'UNCOMMON', ?)", uncommonName)
        jdbcTemplate.update("INSERT INTO item_quality (internal_id, type, name_id) VALUES (4, 'EPIC', ?)", epicName)
        jdbcTemplate.update("INSERT INTO item_quality (internal_id, type, name_id) VALUES (5, 'LEGENDARY', ?)", legendaryName)
        jdbcTemplate.update("INSERT INTO item_quality (internal_id, type, name_id) VALUES (6, 'ARTIFACT', ?)", artifactName)

        val result = service.filters("eu", "argent-dawn", null)
        val quality = result.filters.single { it.id == "qualityIds" }
        val options = quality.options.orEmpty()

        assertEquals(listOf("1", "2", "3", "4", "5", "6"), options.map { it.id })
        assertEquals(
            listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "ARTIFACT"),
            options.map { it.qualityType },
        )
        assertEquals(options.map { it.qualityType }.toSet().size, options.size)
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
