package net.jonasmf.auctionengine.repository.rds

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.GameBuildVersion
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dto.item.ItemDTO
import net.jonasmf.auctionengine.mapper.toDomain
import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import java.time.ZonedDateTime

class ItemJdbcRepositoryTest : IntegrationTestBase() {
    @Autowired
    lateinit var itemJdbcRepository: ItemJdbcRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var connectedRealmRepository: ConnectedRealmRepository

    private val mapper = jacksonObjectMapper()

    @Test
    fun `syncItems upserts grouped item graph without duplicates on rerun`() {
        val firstItem = loadItem(171374)
        val secondItem = loadItem(171391)

        itemJdbcRepository.syncItems(listOf(firstItem, secondItem))
        itemJdbcRepository.syncItems(listOf(firstItem, secondItem))

        assertEquals(2, countRows("`item`"))
        assertEquals(1, countRows("item_quality"))
        assertEquals(2, countRows("inventory_type"))
        assertEquals(1, countRows("item_binding"))
        assertEquals(1, countRows("item_class"))
        assertEquals(2, countRows("item_subclass"))
        assertEquals(2, countRows("item_appearance_ref"))
        assertEquals(2, countRows("item_appearance_refs"))
        assertEquals(
            9,
            countRowsWhere(
                "locale",
                "source_type IN ('item','item_quality','item_binding','item_class','item_subclass','inventory_type')",
            ),
        )
    }

    @Test
    fun `findDistinctAuctionItemIdsForDate only returns todays non-pet item ids`() {
        val today = LocalDate.of(2026, 4, 14)
        val yesterday = today.minusDays(1)
        connectedRealmRepository.save(
            ConnectedRealm(
                id = 1,
                auctionHouse =
                    AuctionHouse(
                        lastModified = null,
                        lastRequested = null,
                        nextUpdate = ZonedDateTime.now(),
                        lowestDelay = 0L,
                        highestDelay = 0L,
                        tsmFile = null,
                        statsFile = null,
                        auctionFile = null,
                    ),
            ),
        )

        jdbcTemplate.update(
            """
            INSERT INTO hourly_auction_stats (
                connected_realm_id,
                ah_type_id,
                item_id,
                date,
                pet_species_id,
                modifier_key,
                bonus_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            1,
            GameBuildVersion.RETAIL.ordinal,
            1001,
            today,
            0,
            "",
            "",
        )
        jdbcTemplate.update(
            """
            INSERT INTO hourly_auction_stats (
                connected_realm_id,
                ah_type_id,
                item_id,
                date,
                pet_species_id,
                modifier_key,
                bonus_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            1,
            GameBuildVersion.RETAIL.ordinal,
            1001,
            today,
            0,
            "mod",
            "bonus",
        )
        jdbcTemplate.update(
            """
            INSERT INTO hourly_auction_stats (
                connected_realm_id,
                ah_type_id,
                item_id,
                date,
                pet_species_id,
                modifier_key,
                bonus_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            1,
            GameBuildVersion.RETAIL.ordinal,
            1002,
            yesterday,
            0,
            "",
            "",
        )
        jdbcTemplate.update(
            """
            INSERT INTO hourly_auction_stats (
                connected_realm_id,
                ah_type_id,
                item_id,
                date,
                pet_species_id,
                modifier_key,
                bonus_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            1,
            GameBuildVersion.RETAIL.ordinal,
            2000,
            today,
            55,
            "",
            "",
        )

        val itemIds = itemJdbcRepository.findDistinctAuctionItemIdsForDate(today)

        assertEquals(listOf(1001), itemIds)
    }

    @Test
    fun `findExistingItemIds returns only ids already in canonical item table`() {
        itemJdbcRepository.syncItems(listOf(loadItem(171374), loadItem(171391)))

        val existingIds = itemJdbcRepository.findExistingItemIds(listOf(171374, 171391, 999999))

        assertEquals(setOf(171374, 171391), existingIds)
    }

    private fun loadItem(itemId: Int) =
        mapper.readValue<ItemDTO>(loadFixture(this, "/blizzard/item/$itemId-response.json")).toDomain()

    private fun countRows(tableName: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName", Int::class.java)!!

    private fun countRowsWhere(
        tableName: String,
        condition: String,
    ): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName WHERE $condition", Int::class.java)!!
}
