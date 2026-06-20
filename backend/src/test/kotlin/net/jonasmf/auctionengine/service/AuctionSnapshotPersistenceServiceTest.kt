package net.jonasmf.auctionengine.service

import jakarta.transaction.Transactional
import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.AuctionTimeLeft
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.auction.Auction
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionPrice
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import net.jonasmf.auctionengine.dto.Link
import net.jonasmf.auctionengine.dto.Links
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.dto.auction.AuctionData
import net.jonasmf.auctionengine.dto.auction.AuctionItemDTO
import net.jonasmf.auctionengine.repository.rds.AuctionRepository
import net.jonasmf.auctionengine.repository.rds.ConnectedRealmRepository
import net.jonasmf.auctionengine.testsupport.writeJsonToDisk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuctionSnapshotPersistenceServiceTest : IntegrationTestBase() {
    @Autowired
    lateinit var service: AuctionSnapshotPersistenceService

    @Autowired
    lateinit var realmService: ConnectedRealmRepository

    @Autowired
    lateinit var auctionRepository: AuctionRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Nested
    open inner class SaveSnapshot {
        @Transactional
        @Test
        open fun `Can save snapshot to the database`() {
            val realm = createRealm(9_999)
            val lastModified = ZonedDateTime.of(2026, 5, 25, 5, 0, 0, 0, ZoneOffset.UTC)
            val auctions =
                listOf(
                    AuctionDTO(
                        id = 1,
                        item = AuctionItemDTO(id = 82800),
                        bid = null,
                        unit_price = 99,
                        buyout = null,
                        time_left = AuctionTimeLeft.SHORT,
                        quantity = 30,
                    ),
                    AuctionDTO(
                        id = 2,
                        item = AuctionItemDTO(id = 82800),
                        bid = 100,
                        unit_price = 1,
                        buyout = null,
                        time_left = AuctionTimeLeft.SHORT,
                        quantity = 6,
                    ),
                    AuctionDTO(
                        id = 3,
                        item = AuctionItemDTO(id = 82800),
                        bid = null,
                        unit_price = 2,
                        buyout = null,
                        time_left = AuctionTimeLeft.LONG,
                        quantity = 3,
                    ),
                    AuctionDTO(
                        id = 4,
                        item = AuctionItemDTO(id = 82800),
                        bid = null,
                        unit_price = 3,
                        buyout = null,
                        time_left = AuctionTimeLeft.SHORT,
                        quantity = 7,
                    ),
                    AuctionDTO(
                        id = 5,
                        item = AuctionItemDTO(id = 82800),
                        bid = 11,
                        unit_price = 4,
                        buyout = null,
                        time_left = AuctionTimeLeft.SHORT,
                        quantity = 5,
                    ),
                    AuctionDTO(
                        id = 7,
                        item = AuctionItemDTO(id = 82800),
                        bid = 5,
                        unit_price = null,
                        buyout = null,
                        time_left = AuctionTimeLeft.SHORT,
                        quantity = 1,
                    ),
                    AuctionDTO(
                        id = 8,
                        item = AuctionItemDTO(id = 200),
                        bid = 11,
                        unit_price = 4,
                        buyout = null,
                        time_left = AuctionTimeLeft.SHORT,
                        quantity = 5,
                    ),
                    AuctionDTO(
                        id = 9,
                        item = AuctionItemDTO(id = 201),
                        bid = 50,
                        unit_price = null,
                        buyout = null,
                        time_left = AuctionTimeLeft.SHORT,
                        quantity = 1,
                    ),
                )
            val auctionFile =
                createFileForAuctions(
                    auctions = auctions,
                )
            var result =
                AuctionSnapshotPersistenceSummary(
                    0,
                    0,
                    Pair<MutableList<Auction>, MutableList<AuctionPrice>>(
                        mutableListOf(),
                        mutableListOf(),
                    ),
                    ConnectedRealmUpdateHistory(
                        id = 0L,
                        auctionCount = 0,
                        lastModified = null,
                        updateTimestamp = null,
                        completedTimestamp = null,
                        connectedRealm = realm,
                    ),
                )
            if (auctionFile?.fileName != null) {
                result = service.saveSnapshot(auctionFile.toAbsolutePath(), realm, lastModified)
            }

            val auctionsFromDb = auctionRepository.findAll().toList()
            val firstItem = result.groupedResult.first.single { it.itemId == 82800 }
            val onlyBidItem = result.groupedResult.first.single { it.itemId == 201 }
            assertEquals(8, auctions.size)
            assertEquals(1, firstItem.buyout)
            assertEquals(5, firstItem.bid)
            assertEquals(2, firstItem.p25)
            assertEquals(4, firstItem.p75)
            assertNull(onlyBidItem.buyout)
            assertEquals(50, onlyBidItem.bid)
            assertNull(onlyBidItem.p25)
            assertNull(onlyBidItem.p75)
            assertEquals(8, result.processedAuctions)
            assertEquals(8, result.updateHistory.auctionCount)
            assertEquals(3, result.uniqueItems)
            assertEquals(3, auctionsFromDb.size)
            assertEquals(
                8,
                jdbcTemplate.queryForObject(
                    "SELECT auction_count FROM connected_realm_update_history WHERE id = ?",
                    Int::class.java,
                    result.updateHistory.id,
                ),
            )
            assertEquals(
                0,
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM auction WHERE buyout = 0 OR p25 = 0 OR p75 = 0",
                    Int::class.java,
                ),
            )
            assertEquals(
                0,
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM auction_price WHERE buyout = 0",
                    Int::class.java,
                ),
            )
            assertEquals(
                2,
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM auction_price WHERE id IN (7, 9) AND buyout IS NULL",
                    Int::class.java,
                ),
            )
        }
    }

    private fun createFileForAuctions(
        auctions: List<AuctionDTO>,
        fileName: String = "auctions",
    ) = writeJsonToDisk(
        fileName,
        AuctionData(
            _links = Links(Link(href = "")),
            auctions = auctions,
        ),
    )

    private fun createRealm(id: Int) =
        realmService.save(
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
                        updateAttempts = 0,
                    ),
                realms = mutableListOf(),
            ),
        )
}
