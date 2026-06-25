package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.generated.model.AdminItemOverrideRequest
import net.jonasmf.auctionengine.generated.model.GameLocale
import net.jonasmf.auctionengine.testsupport.MarketSearchTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class AdminItemRepositoryTest : IntegrationTestBase() {
    @Autowired
    lateinit var adminItemRepository: AdminItemRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun seedItems() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
    }

    @Test
    fun `sparse override wins in v_item and delete restores base`() {
        val baseExpansion =
            jdbcTemplate.queryForObject(
                "SELECT expansion_id FROM `item` WHERE id = 19019 AND is_override = FALSE",
                Int::class.java,
            )

        adminItemRepository.upsertOverride(
            itemId = 19019,
            request =
                AdminItemOverrideRequest(
                    expansionId = 2,
                    overrideNote = "manual fix",
                ),
            requireBase = true,
        )

        val effectiveExpansion =
            jdbcTemplate.queryForObject(
                "SELECT expansion_id FROM v_item WHERE id = 19019",
                Int::class.java,
            )
        assertEquals(2, effectiveExpansion)

        val item = adminItemRepository.findAdminItem(19019, includeOverride = true)
        assertNotNull(item)
        assertTrue(item!!.hasOverride)
        assertTrue(item.hasBase)
        assertEquals("manual fix", item.overrideNote)

        assertTrue(adminItemRepository.deleteOverride(19019))

        val afterDelete =
            jdbcTemplate.queryForObject(
                "SELECT expansion_id FROM v_item WHERE id = 19019",
                Int::class.java,
            )
        assertEquals(baseExpansion, afterDelete)
        assertFalse(adminItemRepository.hasOverrideRow(19019))
    }

    @Test
    fun `override only item is readable through v_item`() {
        adminItemRepository.createOverrideOnly(
            itemId = 424242,
            request =
                AdminItemOverrideRequest(
                    nameLocales = GameLocale(enUS = "Removed Item", enGB = "Removed Item"),
                    qualityId = 3,
                    itemClassId = 2,
                    itemSubclassId = 501,
                    level = 1,
                    requiredLevel = 1,
                    maxCount = 20,
                    purchasePrice = 0,
                    purchaseQuantity = 1,
                    sellPrice = 0,
                    isEquippable = false,
                    isStackable = true,
                    expansionId = 1,
                    overrideNote = "manual only",
                ),
        )

        val row = adminItemRepository.findEffectiveRow(424242)
        assertNotNull(row)
        assertEquals(1, row!!.level)
        assertEquals(1, row.expansionId)

        val item = adminItemRepository.findAdminItem(424242)
        assertNotNull(item)
        assertTrue(item!!.hasOverride)
        assertFalse(item.hasBase)
        assertEquals("Removed Item", item.name)
    }

    @Test
    fun `search filters by override flag`() {
        adminItemRepository.upsertOverride(
            itemId = 19019,
            request = AdminItemOverrideRequest(expansionId = 2),
            requireBase = true,
        )

        val withOverride =
            adminItemRepository.search(
                AdminItemSearchQuery(page = 0, pageSize = 10, hasOverride = true, itemId = 19019),
            )
        assertEquals(1, withOverride.items.size)
        assertTrue(withOverride.items.first().hasOverride)

        val withoutOverride =
            adminItemRepository.search(
                AdminItemSearchQuery(page = 0, pageSize = 10, hasOverride = false, itemId = 19019),
            )
        assertTrue(withoutOverride.items.isEmpty())
    }

    @Test
    fun `sync upsert does not modify override rows`() {
        adminItemRepository.upsertOverride(
            itemId = 19019,
            request = AdminItemOverrideRequest(expansionId = 2, level = 99),
            requireBase = true,
        )

        jdbcTemplate.update(
            """
            INSERT INTO `item` (
                id, is_override, level, max_count, purchase_price, purchase_quantity, required_level,
                sell_price, is_equippable, is_stackable
            ) VALUES (19019, FALSE, 5, 0, 0, 1, 1, 0, 0, 1)
            ON DUPLICATE KEY UPDATE level = VALUES(level)
            """.trimIndent(),
        )

        val overrideLevel =
            jdbcTemplate.queryForObject(
                "SELECT level FROM `item` WHERE id = 19019 AND is_override = TRUE",
                Int::class.java,
            )
        assertEquals(99, overrideLevel)

        val effectiveLevel =
            jdbcTemplate.queryForObject(
                "SELECT level FROM v_item WHERE id = 19019",
                Int::class.java,
            )
        assertEquals(99, effectiveLevel)
    }
}
