package net.jonasmf.auctionengine.repository.rds

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.dto.item.ItemDTO
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRangeRequest
import net.jonasmf.auctionengine.generated.model.AdminExpansionRequest
import net.jonasmf.auctionengine.generated.model.GameLocale
import net.jonasmf.auctionengine.mapper.toDomain
import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class AdminExpansionRepositoryTest : IntegrationTestBase() {
    @Autowired
    lateinit var adminExpansionRepository: AdminExpansionRepository

    @Autowired
    lateinit var itemJdbcRepository: ItemJdbcRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private val mapper = jacksonObjectMapper()

    @BeforeEach
    fun seedExpansions() {
        val catalog =
            listOf(
                Triple(1, "vanilla", "Vanilla"),
                Triple(2, "the-burning-crusade", "The Burning Crusade"),
                Triple(3, "wrath-of-the-lich-king", "Wrath of the Lich King"),
            )
        catalog.forEach { (id, slug, name) ->
            if (!adminExpansionRepository.expansionExists(id)) {
                adminExpansionRepository.createExpansion(
                    expansionRequest(
                        id = id,
                        slug = slug,
                        majorVersion = id,
                        displayOrder = id * 10,
                        enName = name,
                    ),
                )
            }
        }
    }

    @Test
    fun `create update delete range manages expansion ranges`() {
        val range =
            adminExpansionRepository.createRange(
                rangeRequest(
                    expansionId = 1,
                    startItemId = 100,
                    endItemId = 110,
                    note = "seed",
                ),
            )

        assertEquals(1, range.expansion.id)
        assertEquals(100, range.startItemId)
        assertEquals(110, range.endItemId)
        assertEquals("manual", range.source)
        assertTrue(range.enabled)
        assertTrue(adminExpansionRepository.expansionExists(1))

        val updated =
            adminExpansionRepository.updateRange(
                range.id,
                rangeRequest(
                    expansionId = 2,
                    startItemId = 120,
                    endItemId = 130,
                    enabled = false,
                ),
            )

        assertEquals(2, updated?.expansion?.id)
        assertEquals(120, updated?.startItemId)
        assertFalse(updated?.enabled ?: true)
        assertTrue(adminExpansionRepository.deleteRange(range.id))
        assertEquals(null, adminExpansionRepository.findRange(range.id))
    }

    @Test
    fun `hasOverlappingEnabledRange detects enabled overlaps for different expansions`() {
        adminExpansionRepository.createRange(rangeRequest(expansionId = 1, startItemId = 100, endItemId = 120))

        assertTrue(
            adminExpansionRepository.hasOverlappingEnabledRange(
                null,
                rangeRequest(expansionId = 2, startItemId = 110, endItemId = 130),
            ),
        )
        assertFalse(
            adminExpansionRepository.hasOverlappingEnabledRange(
                null,
                rangeRequest(expansionId = 1, startItemId = 110, endItemId = 130),
            ),
        )
        assertFalse(
            adminExpansionRepository.hasOverlappingEnabledRange(
                null,
                rangeRequest(expansionId = 2, startItemId = 110, endItemId = 130, enabled = false),
            ),
        )
    }

    @Test
    fun `applyEnabledRanges updates matched items and reports conflicts`() {
        itemJdbcRepository.syncItems(listOf(loadItem(171374), loadItem(171391)))
        jdbcTemplate.update(
            """
            INSERT INTO `item` (id, is_override, override_note)
            VALUES (?, TRUE, ?)
            """.trimIndent(),
            171374,
            "manual override",
        )
        adminExpansionRepository.createRange(rangeRequest(expansionId = 1, startItemId = 171374, endItemId = 171374))
        adminExpansionRepository.createRange(rangeRequest(expansionId = 2, startItemId = 171391, endItemId = 171391))
        adminExpansionRepository.createRange(rangeRequest(expansionId = 3, startItemId = 171391, endItemId = 171391))

        val summary = adminExpansionRepository.applyEnabledRanges()

        assertEquals(1, summary.matchedItemCount)
        assertEquals(1, summary.conflictItemCount)
        assertEquals(1, itemExpansionId(171374, isOverride = false))
        assertEquals(null, itemExpansionId(171374, isOverride = true))
        assertEquals(null, itemExpansionId(171391, isOverride = false))
    }

    private fun loadItem(itemId: Int) =
        mapper.readValue<ItemDTO>(loadFixture(this, "/blizzard/item/$itemId-response.json")).toDomain()

    private fun itemExpansionId(
        itemId: Int,
        isOverride: Boolean,
    ): Int? =
        jdbcTemplate.queryForObject(
            "SELECT expansion_id FROM `item` WHERE id = ? AND is_override = ?",
            Int::class.java,
            itemId,
            isOverride,
        )

    @Test
    fun `create update and delete expansion manages locales`() {
        val created =
            adminExpansionRepository.createExpansion(
                expansionRequest(
                    id = 99,
                    slug = "test-expansion",
                    majorVersion = 99,
                    displayOrder = 990,
                    enName = "Test Expansion",
                ),
            )

        assertEquals(99, created.id)
        assertEquals("Test Expansion", created.name)
        assertEquals("Test Expansion", created.nameLocales?.enUS)

        val updated =
            adminExpansionRepository.updateExpansion(
                99,
                expansionRequest(
                    id = 99,
                    slug = "test-expansion-updated",
                    majorVersion = 99,
                    displayOrder = 995,
                    enName = "Updated Expansion",
                    deName = "Aktualisiert",
                ),
            )

        assertEquals("test-expansion-updated", updated?.slug)
        assertEquals("Updated Expansion", updated?.name)
        assertEquals("Aktualisiert", updated?.nameLocales?.deDE)

        assertTrue(adminExpansionRepository.deleteExpansion(99))
        assertEquals(null, adminExpansionRepository.findExpansion(99))
    }

    @Test
    fun `isExpansionReferenced detects item ranges`() {
        adminExpansionRepository.createRange(rangeRequest(expansionId = 1, startItemId = 50, endItemId = 60))
        assertTrue(adminExpansionRepository.isExpansionReferenced(1))
    }

    @Test
    fun `list expansions resolves locale suffix`() {
        val german =
            adminExpansionRepository.listExpansions("de_DE").first { it.id == 1 }

        assertEquals("Vanilla", german.name)
    }

    private fun expansionRequest(
        id: Int,
        slug: String,
        majorVersion: Int,
        displayOrder: Int,
        enName: String,
        deName: String? = null,
    ) = AdminExpansionRequest(
        id = id,
        slug = slug,
        majorVersion = majorVersion,
        displayOrder = displayOrder,
        nameLocales =
            GameLocale(
                enUS = enName,
                enGB = enName,
                deDE = deName,
            ),
    )

    private fun rangeRequest(
        expansionId: Int,
        startItemId: Int,
        endItemId: Int,
        source: String = "manual",
        enabled: Boolean = true,
        note: String? = null,
    ) = AdminExpansionItemRangeRequest(
        expansionId = expansionId,
        startItemId = startItemId,
        endItemId = endItemId,
        source = source,
        enabled = enabled,
        note = note,
    )
}
