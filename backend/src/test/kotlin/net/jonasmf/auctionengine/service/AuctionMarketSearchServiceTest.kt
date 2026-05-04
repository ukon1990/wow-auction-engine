package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.testsupport.MarketSearchTestFixtures
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
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
    }
}
