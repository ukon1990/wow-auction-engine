package net.jonasmf.auctionengine.dto.auction

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.jonasmf.auctionengine.mapper.toDBO
import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuctionDTODeserializationTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `should deserialize auction payloads with bonus lists`() {
        val payload = loadFixture(this, "/blizzard/auction/auction-data-response.json")
        val response: AuctionData = mapper.readValue(payload)

        assertEquals(125000000L, response.auctions[0].bid)
        assertEquals(listOf(12499, 12252, 12251), response.auctions[1].item.bonus_lists)
    }

    @Test
    fun `should canonicalize bonus lists when converting to dbo`() {
        val dbo =
            AuctionItemDTO(
                id = 19019,
                bonus_lists = listOf(12499, 12252, 12251),
            ).toDBO()

        assertEquals("12251,12252,12499", dbo.bonusLists)
    }

    @Test
    fun `should ignore unknown fields on auction and item dto`() {
        val auction: AuctionDTO =
            mapper.readValue(
                """
                {
                  "id": 42,
                  "item": {
                    "id": 19019,
                    "bonus_lists": [7, 5],
                    "future_item_field": "ignored"
                  },
                  "quantity": 2,
                  "bid": 1000,
                  "buyout": 2000,
                  "time_left": "LONG",
                  "future_auction_field": {"nested": true}
                }
                """.trimIndent(),
            )

        assertEquals(1000L, auction.bid)
        assertEquals(listOf(7, 5), auction.item.bonus_lists)
    }

    @Test
    fun `should map bid when converting auction dto to dbo`() {
        val auction =
            AuctionDTO(
                id = 99L,
                item = AuctionItemDTO(id = 19019),
                quantity = 1L,
                bid = 1500L,
                unit_price = null,
                buyout = 2500L,
                time_left = net.jonasmf.auctionengine.constant.AuctionTimeLeft.MEDIUM,
            )

        val connectedRealm =
            net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm(
                id = 1,
                auctionHouse =
                    net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse(
                        id = null,
                        connectedId = 1,
                        region = net.jonasmf.auctionengine.constant.Region.Europe,
                        lastModified = null,
                        lastRequested = null,
                        nextUpdate = java.time.Instant.EPOCH,
                        lowestDelay = 0L,
                        avgDelay = 60,
                        highestDelay = 0L,
                        tsmFile = null,
                        statsFile = null,
                        auctionFile = null,
                        updateAttempts = 0,
                        updateLog = mutableListOf(),
                    ),
                realms = mutableListOf(),
            )
        val updateHistory =
            net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealmUpdateHistory(
                id = 1L,
                auctionCount = 1,
                lastModified = java.time.OffsetDateTime.now(),
                updateTimestamp = java.time.OffsetDateTime.now(),
                completedTimestamp = null,
                connectedRealm = connectedRealm,
            )

        val dbo = auction.toDBO(connectedRealm, updateHistory)

        assertEquals(1500L, dbo.bid)
    }
}
