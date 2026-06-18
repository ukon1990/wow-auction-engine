package net.jonasmf.auctionengine.service

import jakarta.transaction.Transactional
import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.AuctionTimeLeft
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.auction.Auction
import net.jonasmf.auctionengine.dbo.rds.auction.AuctionPrice
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionUpdateHistory
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
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.assertEquals

class AuctionSnapshotPersistenceServiceTest : IntegrationTestBase() {
    @Autowired
    lateinit var service: AuctionSnapshotPersistenceService

    @Autowired
    lateinit var realmService: ConnectedRealmRepository

    @Autowired
    lateinit var auctionRepository: AuctionRepository

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
                        id = 6,
                        item = AuctionItemDTO(id = 200),
                        bid = 11,
                        unit_price = 4,
                        buyout = null,
                        time_left = AuctionTimeLeft.SHORT,
                        quantity = 5,
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
            val firstItem = result.groupedResult.first.first()
            assertEquals(6, auctions.size)
            assertEquals(1, firstItem.buyout)
            assertEquals(11, firstItem.bid)
            assertEquals(2, firstItem.p25)
            assertEquals(4, firstItem.p75)
            // assertEquals(5, result.processedAuctions)
            assertEquals(2, result.uniqueItems)
            assertEquals(2, auctionsFromDb.size)
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
