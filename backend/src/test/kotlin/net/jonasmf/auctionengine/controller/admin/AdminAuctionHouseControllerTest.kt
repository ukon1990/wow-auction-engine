package net.jonasmf.auctionengine.controller.admin

import net.jonasmf.auctionengine.config.MVCIntegrationTest
import net.jonasmf.auctionengine.generated.model.AuctionHousePageSortBy
import net.jonasmf.auctionengine.generated.model.UpdateAuctionHouse
import net.jonasmf.auctionengine.auction.snapshot.AuctionHouseService
import net.jonasmf.auctionengine.realm.ConnectedRealmService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.OffsetDateTime

class AdminAuctionHouseControllerTest : MVCIntegrationTest() {
    @Autowired
    lateinit var auctionHouseService: AuctionHouseService

    @Autowired
    lateinit var connectedRealmService: ConnectedRealmService

    private val basePath = "/api/admin/auction-houses"

    @BeforeEach
    fun setupData() {
        connectedRealmService.updateRealms()
    }

    @Nested
    inner class GetById {
        @Test
        fun `should return the auction house for a given id`() {
            mvcGet("$basePath/509", admin)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(509))
        }

        @Test
        fun `should return 404 if there is no matching auction house for the given id`() {
            mvcGet("$basePath/-9999", admin).andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class SearchAuctionHouses {
        @Test
        fun `lists auction houses in a page for an administrator`() {
            val sortBy = AuctionHousePageSortBy.NAME.value

            mvcGet("$basePath?pageSize=10&page=0&sortBy=$sortBy&sortDirection=asc", admin)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items[0].connectedRealmId").value(604))
                .andExpect(jsonPath("$.page.page").value(0))
                .andExpect(jsonPath("$.page.pageSize").value(10))
                .andExpect(jsonPath("$.page.totalItems").value(41))
                .andExpect(jsonPath("$.page.totalPages").value(5))
                .andExpect(jsonPath("$.sort.sortBy").value(sortBy))
                .andExpect(jsonPath("$.sort.sortDirection").value("asc"))
        }

        @Test
        fun `returns 403 for a caller without the admin group`() {
            mvcGet("$basePath?pageSize=10&page=0", user).andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 for an anonymous caller`() {
            mvcGet("$basePath?pageSize=10&page=0", actor = null).andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class Patch {
        @Test
        fun `should be able to update an auction house, and get the updated version back with reset updateAttempts`() {
            val nextUpdate = OffsetDateTime.parse("2026-01-15T12:34:56.123456Z")
            auctionHouseService.updateTimes(id = 509, newLastModified = null, isSuccess = false)

            mvcPatch("$basePath/509", UpdateAuctionHouse(nextUpdate = nextUpdate), admin)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(509))
                .andExpect(jsonPath("$.nextUpdate").value("${nextUpdate.toLocalDateTime()}Z"))
                .andExpect(jsonPath("$.updateAttempts").value(0))
        }
    }
}
