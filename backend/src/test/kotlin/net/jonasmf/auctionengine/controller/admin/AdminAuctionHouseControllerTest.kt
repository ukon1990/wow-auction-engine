package net.jonasmf.auctionengine.controller.admin

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.config.MVCIntegrationTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AdminAuctionHouseControllerTest : MVCIntegrationTest() {
    @Test
    fun getAuctionHouseById() {
        assertEquals(1, 2)
    }

    @Nested
    inner class SearchAuctionHouses {
        @Test
        fun searchAuctionHouses() {
            assertEquals(1, 2)
        }
    }
}
