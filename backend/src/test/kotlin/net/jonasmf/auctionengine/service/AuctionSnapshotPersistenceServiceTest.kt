package net.jonasmf.auctionengine.service

import jakarta.transaction.Transactional
import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.AuctionTimeLeft
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dto.Link
import net.jonasmf.auctionengine.dto.Links
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.dto.auction.AuctionData
import net.jonasmf.auctionengine.dto.auction.AuctionItemDTO
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

    @Nested
    open inner class SaveSnapshot {
        @Transactional
        @Test
        open fun `Can save snapshot to the database`() {
            val realm = createRealm(1)
            val lastModified = ZonedDateTime.of(2026, 5, 25, 5, 0, 0, 0, ZoneOffset.UTC)
            val auctionFile =
                createFileForAuctions(
                    auctions =
                        listOf(
                            AuctionDTO(
                                id = 123,
                                item = AuctionItemDTO(id = 82800),
                                bid = null,
                                unit_price = 1500,
                                buyout = null,
                                time_left = AuctionTimeLeft.SHORT,
                                quantity = 30,
                            ),
                            AuctionDTO(
                                id = 124,
                                item = AuctionItemDTO(id = 82800),
                                bid = null,
                                unit_price = 1501,
                                buyout = null,
                                time_left = AuctionTimeLeft.SHORT,
                                quantity = 10,
                            ),
                        ),
                )
            var result = AuctionSnapshotPersistenceSummary(0, 0)
            if (auctionFile?.fileName != null) {
                result = service.saveSnapshot(auctionFile.toAbsolutePath(), realm, lastModified)
            }
            assertEquals(2, result.processedAuctions)
            assertEquals(1, result.uniqueItems)
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
