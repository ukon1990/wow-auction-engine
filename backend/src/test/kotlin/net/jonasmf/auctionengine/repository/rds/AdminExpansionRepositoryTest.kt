package net.jonasmf.auctionengine.repository.rds

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.dto.item.ItemDTO
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRangeRequest
import net.jonasmf.auctionengine.mapper.toDomain
import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
        adminExpansionRepository.createRange(rangeRequest(expansionId = 1, startItemId = 171374, endItemId = 171374))
        adminExpansionRepository.createRange(rangeRequest(expansionId = 2, startItemId = 171391, endItemId = 171391))
        adminExpansionRepository.createRange(rangeRequest(expansionId = 3, startItemId = 171391, endItemId = 171391))

        val summary = adminExpansionRepository.applyEnabledRanges()

        assertEquals(1, summary.matchedItemCount)
        assertEquals(1, summary.conflictItemCount)
        assertEquals(1, itemExpansionId(171374))
        assertEquals(null, itemExpansionId(171391))
    }

    private fun loadItem(itemId: Int) =
        mapper.readValue<ItemDTO>(loadFixture(this, "/blizzard/item/$itemId-response.json")).toDomain()

    private fun itemExpansionId(itemId: Int): Int? =
        jdbcTemplate.queryForObject(
            "SELECT expansion_id FROM `item` WHERE id = ?",
            Int::class.java,
            itemId,
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
