package net.jonasmf.auctionengine.dto.auction

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.jonasmf.auctionengine.testsupport.loadFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuctionDTODeserializationTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `should deserialize auction payloads with bonus lists`() {
        val payload = loadFixture(this, "/blizzard/auction/auction-data-response.json")
        val response: AuctionData = mapper.readValue(payload)

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
}
