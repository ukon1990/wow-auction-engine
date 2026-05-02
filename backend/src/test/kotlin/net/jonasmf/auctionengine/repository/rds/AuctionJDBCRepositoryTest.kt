package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionId
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import net.jonasmf.auctionengine.utility.AuctionVariantKeyUtility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AuctionJDBCRepositoryTest : IntegrationTestBase() {
    @Autowired
    lateinit var auctionJdbcRepository: AuctionJDBCRepository

    @Autowired
    lateinit var auctionRepository: AuctionRepository

    @Autowired
    lateinit var auctionItemRepository: AuctionItemRepository

    @Autowired
    lateinit var auctionItemModifierRepository: AuctionItemModifierRepository

    @Autowired
    lateinit var connectedRealmRepository: ConnectedRealmRepository

    @Autowired
    lateinit var connectedRealmUpdateHistoryRepository: ConnectedRealmUpdateHistoryRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `bulk upsert reuses variant keys soft deletes missing auctions and undeletes reappearing rows`() {
        val connectedRealm = createConnectedRealm(1)
        val modifierRows =
            listOf(
                AuctionModifierUpsertRow(type = "ITEM_LEVEL", value = 489),
                AuctionModifierUpsertRow(type = "PLAYER_LEVEL", value = 70),
                AuctionModifierUpsertRow(type = "ITEM_LEVEL", value = 489),
            )

        auctionJdbcRepository.upsertModifiers(modifierRows)
        val modifierIds = auctionJdbcRepository.findModifierIds(modifierRows)
        assertEquals(2, modifierIds.size)
        assertEquals(2L, auctionItemModifierRepository.count())

        val variantHash =
            AuctionVariantKeyUtility.variantHash(
                itemId = 211297,
                bonusKey = "12251,12252,12499",
                modifierKey = "ITEM_LEVEL:489,PLAYER_LEVEL:70",
                context = 52,
                petBreedId = null,
                petLevel = null,
                petQualityId = null,
                petSpeciesId = null,
            )
        val itemRow =
            AuctionItemUpsertRow(
                variantHash = variantHash,
                itemId = 211297,
                bonusLists = "12251,12252,12499",
                context = 52,
                petBreedId = null,
                petLevel = null,
                petQualityId = null,
                petSpeciesId = null,
            )

        auctionJdbcRepository.upsertAuctionItems(listOf(itemRow, itemRow))
        val itemIds = auctionJdbcRepository.findAuctionItemIds(listOf(variantHash))
        val auctionItemId = itemIds.getValue(variantHash)
        assertEquals(1L, auctionItemRepository.count())

        auctionJdbcRepository.upsertAuctionItemModifierLinks(
            listOf(
                AuctionItemModifierLinkUpsertRow(
                    auctionItemId = auctionItemId,
                    sortOrder = 0,
                    modifierId = modifierIds.getValue(AuctionModifierUpsertRow("ITEM_LEVEL", 489)),
                ),
                AuctionItemModifierLinkUpsertRow(
                    auctionItemId = auctionItemId,
                    sortOrder = 1,
                    modifierId = modifierIds.getValue(AuctionModifierUpsertRow("PLAYER_LEVEL", 70)),
                ),
            ),
        )

        val firstSnapshot = OffsetDateTime.ofInstant(Instant.parse("2026-04-10T10:00:00Z"), ZoneOffset.UTC)
        val firstHistory = createUpdateHistory(connectedRealm, firstSnapshot, 2)
        auctionJdbcRepository.upsertAuctions(
            listOf(
                AuctionUpsertRow(
                    id = 101,
                    connectedRealmId = connectedRealm.id,
                    itemId = auctionItemId,
                    quantity = 3,
                    bid = null,
                    unitPrice = 1599900,
                    timeLeft = 3,
                    buyout = 1599900,
                    firstSeen = firstSnapshot,
                    lastSeen = firstSnapshot,
                    updateHistoryId = firstHistory.id,
                ),
                AuctionUpsertRow(
                    id = 102,
                    connectedRealmId = connectedRealm.id,
                    itemId = auctionItemId,
                    quantity = 1,
                    bid = 125000000,
                    unitPrice = null,
                    timeLeft = 2,
                    buyout = 250000000,
                    firstSeen = firstSnapshot,
                    lastSeen = firstSnapshot,
                    updateHistoryId = firstHistory.id,
                ),
            ),
        )

        val secondSnapshot = firstSnapshot.plusHours(1)
        val secondHistory = createUpdateHistory(connectedRealm, secondSnapshot, 1)
        auctionJdbcRepository.upsertAuctions(
            listOf(
                AuctionUpsertRow(
                    id = 101,
                    connectedRealmId = connectedRealm.id,
                    itemId = auctionItemId,
                    quantity = 5,
                    bid = null,
                    unitPrice = 1699900,
                    timeLeft = 1,
                    buyout = 1699900,
                    firstSeen = secondSnapshot,
                    lastSeen = secondSnapshot,
                    updateHistoryId = secondHistory.id,
                ),
            ),
        )
        auctionJdbcRepository.markMissingAuctionsDeleted(connectedRealm.id, secondHistory.id, secondSnapshot)

        assertEquals(firstSnapshot, auctionTimestamp(101, connectedRealm.id, "first_seen"))
        assertEquals(secondSnapshot, auctionTimestamp(101, connectedRealm.id, "last_seen"))
        assertNull(auctionTimestamp(101, connectedRealm.id, "deleted_at"))
        assertEquals(secondHistory.id, auctionLong(101, connectedRealm.id, "update_history_id"))
        assertNotNull(auctionTimestamp(102, connectedRealm.id, "deleted_at"))
        assertEquals(secondSnapshot, auctionTimestamp(102, connectedRealm.id, "deleted_at"))

        val thirdSnapshot = secondSnapshot.plusHours(1)
        val thirdHistory = createUpdateHistory(connectedRealm, thirdSnapshot, 1)
        auctionJdbcRepository.upsertAuctions(
            listOf(
                AuctionUpsertRow(
                    id = 102,
                    connectedRealmId = connectedRealm.id,
                    itemId = auctionItemId,
                    quantity = 2,
                    bid = 126000000,
                    unitPrice = null,
                    timeLeft = 0,
                    buyout = 251000000,
                    firstSeen = thirdSnapshot,
                    lastSeen = thirdSnapshot,
                    updateHistoryId = thirdHistory.id,
                ),
            ),
        )
        auctionJdbcRepository.markMissingAuctionsDeleted(connectedRealm.id, thirdHistory.id, thirdSnapshot)

        assertEquals(firstSnapshot, auctionTimestamp(102, connectedRealm.id, "first_seen"))
        assertEquals(thirdSnapshot, auctionTimestamp(102, connectedRealm.id, "last_seen"))
        assertNull(auctionTimestamp(102, connectedRealm.id, "deleted_at"))
        assertNotNull(auctionTimestamp(101, connectedRealm.id, "deleted_at"))
        assertEquals(thirdSnapshot, auctionTimestamp(101, connectedRealm.id, "deleted_at"))
    }

    @Test
    fun `purge deletes only aged soft deleted auctions and keeps reusable key tables`() {
        val connectedRealm = createConnectedRealm(2)
        val modifier = AuctionModifierUpsertRow(type = "ITEM_LEVEL", value = 525)
        auctionJdbcRepository.upsertModifiers(listOf(modifier))
        val modifierId = auctionJdbcRepository.findModifierIds(listOf(modifier)).getValue(modifier)
        val variantHash =
            AuctionVariantKeyUtility.variantHash(
                itemId = 19019,
                bonusKey = "",
                modifierKey = "ITEM_LEVEL:525",
                context = null,
                petBreedId = null,
                petLevel = null,
                petQualityId = null,
                petSpeciesId = null,
            )
        auctionJdbcRepository.upsertAuctionItems(
            listOf(
                AuctionItemUpsertRow(
                    variantHash = variantHash,
                    itemId = 19019,
                    bonusLists = "",
                    context = null,
                    petBreedId = null,
                    petLevel = null,
                    petQualityId = null,
                    petSpeciesId = null,
                ),
            ),
        )
        val itemId = auctionJdbcRepository.findAuctionItemIds(listOf(variantHash)).getValue(variantHash)
        auctionJdbcRepository.upsertAuctionItemModifierLinks(
            listOf(
                AuctionItemModifierLinkUpsertRow(auctionItemId = itemId, sortOrder = 0, modifierId = modifierId),
            ),
        )

        val historyTime = OffsetDateTime.ofInstant(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC)
        val history = createUpdateHistory(connectedRealm, historyTime, 1)
        auctionJdbcRepository.upsertAuctions(
            listOf(
                AuctionUpsertRow(
                    id = 201,
                    connectedRealmId = connectedRealm.id,
                    itemId = itemId,
                    quantity = 1,
                    bid = null,
                    unitPrice = 1,
                    timeLeft = 0,
                    buyout = 1,
                    firstSeen = historyTime,
                    lastSeen = historyTime,
                    updateHistoryId = history.id,
                ),
            ),
        )

        jdbcTemplate.update(
            "UPDATE auction SET deleted_at = ? WHERE id = ? AND connected_realm_id = ?",
            Timestamp.from(historyTime.minusDays(8).toInstant()),
            201L,
            connectedRealm.id,
        )

        val deletedRows = auctionJdbcRepository.deleteSoftDeletedAuctionsOlderThan(historyTime.minusDays(1))
        assertEquals(1, deletedRows)
        assertFalse(auctionRepository.findById(AuctionId(201, connectedRealm.id)).isPresent)
        assertEquals(1L, auctionItemRepository.count())
        assertEquals(1L, auctionItemModifierRepository.count())
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auction_item_modifier_link", Int::class.java))
    }

    private fun createConnectedRealm(id: Int): ConnectedRealm =
        connectedRealmRepository.save(
            ConnectedRealm(
                id = id,
                auctionHouse =
                    AuctionHouse(
                        connectedId = id,
                        region = Region.Europe,
                        lastModified = null,
                        lastRequested = null,
                        nextUpdate = Instant.EPOCH,
                        lowestDelay = 60,
                        avgDelay = 60,
                        highestDelay = 60,
                        tsmFile = null,
                        statsFile = null,
                        auctionFile = null,
                        updateAttempts = 0,
                    ),
                realms = mutableListOf(),
            ),
        )

    private fun createUpdateHistory(
        connectedRealm: ConnectedRealm,
        snapshotTime: OffsetDateTime,
        auctionCount: Int,
    ): ConnectedRealmUpdateHistory =
        connectedRealmUpdateHistoryRepository.save(
            ConnectedRealmUpdateHistory(
                auctionCount = auctionCount,
                lastModified = snapshotTime,
                updateTimestamp = snapshotTime,
                connectedRealm = connectedRealm,
            ),
        )

    private fun auctionTimestamp(
        auctionId: Long,
        connectedRealmId: Int,
        columnName: String,
    ): OffsetDateTime? =
        jdbcTemplate.queryForObject(
            "SELECT $columnName FROM auction WHERE id = ? AND connected_realm_id = ?",
            { rs, _ ->
                rs.getTimestamp(1)?.toInstant()?.atOffset(ZoneOffset.UTC)
            },
            auctionId,
            connectedRealmId,
        )

    private fun auctionLong(
        auctionId: Long,
        connectedRealmId: Int,
        columnName: String,
    ): Long =
        jdbcTemplate.queryForObject(
            "SELECT $columnName FROM auction WHERE id = ? AND connected_realm_id = ?",
            Long::class.java,
            auctionId,
            connectedRealmId,
        )!!
}
