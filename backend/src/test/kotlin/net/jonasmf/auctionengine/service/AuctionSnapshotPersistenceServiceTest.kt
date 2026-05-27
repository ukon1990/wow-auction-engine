package net.jonasmf.auctionengine.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.AuctionTimeLeft
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory
import net.jonasmf.auctionengine.dto.Link
import net.jonasmf.auctionengine.dto.Links
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.dto.auction.AuctionData
import net.jonasmf.auctionengine.dto.auction.AuctionItemDTO
import net.jonasmf.auctionengine.repository.rds.AuctionJDBCRepository
import net.jonasmf.auctionengine.testsupport.writeJsonToDisk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.dao.CannotAcquireLockException
import java.sql.SQLException
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.io.path.pathString
import kotlin.test.assertEquals

class AuctionSnapshotPersistenceServiceTest(
    val service: AuctionSnapshotPersistenceService,
) : IntegrationTestBase() {
    private val auctionJdbcRepository = mockk<AuctionJDBCRepository>()
    private val updateHistoryService = mockk<ConnectedRealmUpdateHistoryService>()

    @Test
    fun `saveAuction retries auction upsert when MariaDB reports deadlock`() {
        val service = AuctionSnapshotPersistenceService(auctionJdbcRepository, updateHistoryService)
        val connectedRealm = createRealm(3656)
        val lastModified = ZonedDateTime.now().minusMinutes(1)
        val updateHistory =
            ConnectedRealmUpdateHistory(
                id = 77,
                auctionCount = 1,
                lastModified = lastModified.toOffsetDateTime(),
                updateTimestamp = lastModified.toOffsetDateTime(),
                connectedRealm = connectedRealm,
            )
        val auction =
            AuctionDTO(
                id = 123,
                item = AuctionItemDTO(id = 82800),
                quantity = 2,
                bid = null,
                unit_price = 1500,
                buyout = 3000,
                time_left = AuctionTimeLeft.SHORT,
            )

        every { updateHistoryService.startUpdate(connectedRealm, 1, lastModified) } returns updateHistory
        every { updateHistoryService.setUpdateToCompleted(connectedRealm.id, lastModified) } returns true
        every { auctionJdbcRepository.upsertModifiers(any()) } returns 0
        every { auctionJdbcRepository.findModifierIds(any()) } returns emptyMap()
        every { auctionJdbcRepository.upsertAuctionItems(any()) } returns 1
        every { auctionJdbcRepository.findAuctionItemIds(any()) } answers {
            firstArg<Collection<String>>().associateWith { 42L }
        }
        every { auctionJdbcRepository.upsertAuctionItemModifierLinks(any()) } returns 0
        every { auctionJdbcRepository.upsertAuctions(any()) } throws
            CannotAcquireLockException(
                "deadlock",
                SQLException("Deadlock found when trying to get lock; try restarting transaction", "40001", 1213),
            ) andThen 1

        service.saveAuction(auction, connectedRealm, lastModified)

        verify(exactly = 2) { auctionJdbcRepository.upsertAuctions(any()) }
        verify(exactly = 1) { updateHistoryService.setUpdateToCompleted(connectedRealm.id, lastModified) }
    }

    @Nested
    inner class SaveSnapshot {
        @Test
        fun `Can save snapshot to the database`() {
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
            var result = AuctionSnapshotPersistenceSummary(0, 0, 0)
            if (auctionFile?.fileName != null) {
                result = service.saveSnapshot(auctionFile.fileName.toAbsolutePath(), realm, lastModified)
            }
            assertEquals(2, result.processedAuctions)
            assertEquals(1, result.batchCount)
        }
    }

    private fun createFileForAuctions(
        auctions: List<AuctionDTO>,
        fileName: String = "auctions",
    ) = writeJsonToDisk(
        "",
        AuctionData(
            _links = Links(Link(href = "")),
            auctions = auctions,
        ),
    )

    private fun createRealm(id: Int) =
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
        )
}
