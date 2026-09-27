package net.jonasmf.auctionengine.controller.admin

import net.jonasmf.auctionengine.config.MVCIntegrationTest
import net.jonasmf.auctionengine.generated.model.AuctionHousePageSortBy
import net.jonasmf.auctionengine.generated.model.UpdateAuctionHouse
import net.jonasmf.auctionengine.service.AuctionHouseService
import net.jonasmf.auctionengine.service.ConnectedRealmService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity.noContent
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.OffsetDateTime

class AdminAuctionHouseControllerTest : MVCIntegrationTest() {
    @Autowired
    lateinit var auctionHouseService: AuctionHouseService

    @Autowired
    lateinit var connectedRealmService: ConnectedRealmService

    protected var basePath = "/api/admin/auction-houses"

    @BeforeEach
    fun setupData() {
        connectedRealmService.updateRealms()
    }

    @Nested
    inner class GetById {
        @Test
        fun `should return the auction house for a given id`() {
            val connectedRealmId = 509
            val result =
                mvcGet(
                    path = "$basePath/$connectedRealmId",
                    roles = listOf("admin"),
                )

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(connectedRealmId))
                .andExpect { jsonPath("$.realms.length()").value(3) }
        }

        @Test
        fun `should return 404 if there is no matching auction house for the given id`() {
            val connectedRealmId = -9999
            val result =
                mvcGet(
                    path = "$basePath/$connectedRealmId",
                    roles = listOf("admin"),
                )

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class SearchAuctionHouses {
        @Test
        fun `lists auction houses in a page for an administrator`() {
            val page = 0
            val pageSize = 10
            val totalItems = 41
            val totalPages = 5
            val sortDirection = "asc"
            val sortBy = AuctionHousePageSortBy.NAME.value

            val result =
                mvcGet(
                    path = "$basePath?pageSize=$pageSize&page=$page&sortBy=$sortBy&sortDirection=$sortDirection",
                    roles = listOf("admin"),
                )

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isOk)
                // It shoud be -2, due to the default sorting
                .andExpect(jsonPath("$.items[0].connectedRealmId").value(604))
                .andExpect(jsonPath("$.page.page").value(page))
                .andExpect(jsonPath("$.page.pageSize").value(pageSize))
                .andExpect(jsonPath("$.page.totalItems").value(totalItems))
                .andExpect(jsonPath("$.page.totalPages").value(totalPages))
                .andExpect(jsonPath("$.sort.sortBy").value(sortBy))
                .andExpect(jsonPath("$.sort.sortDirection").value(sortDirection))
        }

        @Test
        fun `Will return 403 forbidden if the user does not have the correct access`() {
            val page = 0
            val pageSize = 10

            val result =
                mvcGet(
                    path = "/api/admin/auction-houses?pageSize=$pageSize&page=$page",
                    roles = listOf("some_other_role"),
                )

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isForbidden)
                .andExpect { noContent() }
        }
    }

    @Nested
    inner class Patch {
        @Test
        fun `should be able to update an auction house, and get the updated version back with reset updateAttempts`() {
            val connectedRealmId = 509
            val nextUpdate = OffsetDateTime.now()

            auctionHouseService.updateTimes(
                id = connectedRealmId,
                newLastModified = null,
                isSuccess = false,
            )

            val result =
                mvcPatch(
                    path = "$basePath/$connectedRealmId",
                    body =
                        UpdateAuctionHouse(
                            nextUpdate = nextUpdate,
                        ),
                    roles = listOf("admin"),
                )

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(connectedRealmId))
                .andExpect(jsonPath("$.nextUpdate").value("${nextUpdate.toLocalDateTime()}Z"))
                .andExpect(jsonPath("$.updateAttempts").value(0))
        }
    }
}
