package net.jonasmf.auctionengine.repository.rds

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dto.item.ItemDTO
import net.jonasmf.auctionengine.generated.model.AdminExpansionItemRangeRequest
import net.jonasmf.auctionengine.generated.model.AdminExpansionRequest
import net.jonasmf.auctionengine.generated.model.GameLocale
import net.jonasmf.auctionengine.mapper.toDomain
import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.LocalDate

class ItemJdbcRepositoryTest : IntegrationTestBase() {
    @Autowired
    lateinit var itemJdbcRepository: ItemJdbcRepository

    @Autowired
    lateinit var adminExpansionRepository: AdminExpansionRepository

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
    fun `findMissingItemIdsForDate returns combined missing ids without loading existing ids in memory`() {
        val today = LocalDate.of(2026, 4, 14)
        val yesterday = today.minusDays(1)
        connectedRealmRepository.save(
            ConnectedRealm(
                id = 1,
                auctionHouse =
                    AuctionHouse(
                        lastModified = null,
                        lastRequested = null,
                        nextUpdate = Instant.now(),
                        lowestDelay = 0L,
                        highestDelay = 0L,
                    ),
            ),
        )

        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id,
                item_id,
                date,
                pet_species_id,
                modifier_key,
                bonus_key
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            1,
            1001,
            today,
            -1,
            "",
            "",
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id,
                item_id,
                date,
                pet_species_id,
                modifier_key,
                bonus_key
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            1,
            1001,
            today,
            -1,
            "mod",
            "bonus",
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id,
                item_id,
                date,
                pet_species_id,
                modifier_key,
                bonus_key
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            1,
            1002,
            yesterday,
            -1,
            "",
            "",
        )
        jdbcTemplate.update(
            """
            INSERT INTO auction_stats_hourly (
                connected_realm_id,
                item_id,
                date,
                pet_species_id,
                modifier_key,
                bonus_key
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            1,
            2000,
            today,
            55,
            "",
            "",
        )
        jdbcTemplate.update(
            "INSERT INTO recipe (id, crafted_item_id) VALUES (?, ?)",
            5001,
            3001,
        )
        jdbcTemplate.update(
            "INSERT INTO recipe (id, crafted_item_id) VALUES (?, ?)",
            5002,
            171374,
        )
        jdbcTemplate.update(
            "INSERT INTO recipe_reagent (internal_id, item_id, quantity) VALUES (?, ?, ?)",
            7001,
            4001,
            2,
        )
        itemJdbcRepository.syncItems(listOf(loadItem(171374)))

        val discovery = itemJdbcRepository.findMissingItemIdsForDate(today)

        assertEquals(1, discovery.auctionSourceCount)
        assertEquals(2, discovery.recipeCraftedSourceCount)
        assertEquals(1, discovery.recipeReagentSourceCount)
        assertEquals(4, discovery.candidateItemCount)
        assertEquals(1, discovery.existingItemCount)
        assertEquals(listOf(1001, 3001, 4001), discovery.missingItemIds)
    }

    @Test
    fun `findExistingItemIds returns only ids already in canonical item table`() {
        itemJdbcRepository.syncItems(listOf(loadItem(171374), loadItem(171391)))

        val existingIds = itemJdbcRepository.findExistingItemIds(listOf(171374, 171391, 999999))

        assertEquals(setOf(171374, 171391), existingIds)
    }

    @Test
    fun `findExistingItemIds treats override only rows as existing`() {
        jdbcTemplate.update(
            """
            INSERT INTO `item` (id, is_override, override_note)
            VALUES (?, TRUE, ?)
            """.trimIndent(),
            999999,
            "placeholder override",
        )

        val existingIds = itemJdbcRepository.findExistingItemIds(listOf(999999, 1000000))

        assertEquals(setOf(999999), existingIds)
    }

    @Test
    fun `v_item returns sparse override values over base item and sync only updates base row`() {
        val item = loadItem(171374)
        itemJdbcRepository.syncItems(listOf(item))
        val overrideNameId =
            insertLocale(
                id = 900001,
                enGb = "Corrected Laestrite Ore",
                deDe = "Korrigiertes Laestriterz",
                sourceType = "item_override",
                sourceKey = "171374",
                sourceField = "name",
            )
        jdbcTemplate.update(
            """
            INSERT INTO `item` (id, is_override, name_id, media_url, override_note)
            VALUES (?, TRUE, ?, ?, ?)
            """.trimIndent(),
            171374,
            overrideNameId,
            "https://media.example/corrected.png",
            "manual correction",
        )

        val effective =
            jdbcTemplate.queryForMap(
                """
                SELECT
                    v.media_url,
                    v.quality_id,
                    l.en_gb AS item_name
                FROM v_item v
                    LEFT JOIN locale l ON l.id = v.name_id
                WHERE v.id = ?
                """.trimIndent(),
                171374,
            )
        val marketDetail =
            jdbcTemplate.queryForMap(
                """
                SELECT item_media_url, item_name_en_gb, quality_id
                FROM v_auction_market_item_details
                WHERE item_id = ?
                """.trimIndent(),
                171374,
            )

        assertEquals("Corrected Laestrite Ore", effective["item_name"])
        assertEquals("https://media.example/corrected.png", effective["media_url"])
        assertEquals(baseItemQualityId(171374), effective["quality_id"])
        assertEquals("Corrected Laestrite Ore", marketDetail["item_name_en_gb"])
        assertEquals("https://media.example/corrected.png", marketDetail["item_media_url"])
        assertEquals(baseItemQualityId(171374), marketDetail["quality_id"])

        itemJdbcRepository.syncItems(listOf(item.copy(mediaUrl = "https://media.example/base-updated.png")))

        assertEquals("https://media.example/base-updated.png", itemMediaUrl(171374, isOverride = false))
        assertEquals("https://media.example/corrected.png", itemMediaUrl(171374, isOverride = true))
    }

    @Test
    fun `findMissingItemIdsForEnabledExpansionRanges returns missing ids covered by ranges`() {
        itemJdbcRepository.syncItems(listOf(loadItem(171374)))
        ensureExpansionExists(
            id = 1,
            slug = "vanilla",
            majorVersion = 1,
            displayOrder = 10,
            enName = "Vanilla",
        )
        ensureExpansionExists(
            id = 2,
            slug = "the-burning-crusade",
            majorVersion = 2,
            displayOrder = 20,
            enName = "The Burning Crusade",
        )
        adminExpansionRepository.createRange(
            AdminExpansionItemRangeRequest(
                expansionId = 1,
                startItemId = 171374,
                endItemId = 171376,
                source = "manual",
                enabled = true,
            ),
        )
        adminExpansionRepository.createRange(
            AdminExpansionItemRangeRequest(
                expansionId = 2,
                startItemId = 999001,
                endItemId = 999002,
                source = "manual",
                enabled = false,
            ),
        )

        val discovery = itemJdbcRepository.findMissingItemIdsForEnabledExpansionRanges()

        assertEquals(3, discovery.candidateItemCount)
        assertEquals(1, discovery.existingItemCount)
        assertEquals(listOf(171375, 171376), discovery.missingItemIds)
    }

    private fun loadItem(itemId: Int) =
        mapper.readValue<ItemDTO>(loadFixture(this, "/blizzard/item/$itemId-response.json")).toDomain()

    private fun ensureExpansionExists(
        id: Int,
        slug: String,
        majorVersion: Int,
        displayOrder: Int,
        enName: String,
    ) {
        if (adminExpansionRepository.expansionExists(id)) {
            return
        }
        adminExpansionRepository.createExpansion(
            AdminExpansionRequest(
                id = id,
                slug = slug,
                majorVersion = majorVersion,
                displayOrder = displayOrder,
                nameLocales =
                    GameLocale(
                        enUS = enName,
                        enGB = enName,
                    ),
            ),
        )
    }

    private fun countRows(tableName: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName", Int::class.java)!!

    private fun countRowsWhere(
        tableName: String,
        condition: String,
    ): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName WHERE $condition", Int::class.java)!!

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

    private fun baseItemQualityId(itemId: Int): Long? =
        jdbcTemplate.queryForObject(
            "SELECT quality_id FROM `item` WHERE id = ? AND is_override = FALSE",
            Long::class.java,
            itemId,
        )

    private fun itemMediaUrl(
        itemId: Int,
        isOverride: Boolean,
    ): String? =
        jdbcTemplate.queryForObject(
            "SELECT media_url FROM `item` WHERE id = ? AND is_override = ?",
            String::class.java,
            itemId,
            isOverride,
        )
}
