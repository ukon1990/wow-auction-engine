package net.jonasmf.auctionengine.controller.admin

import net.jonasmf.auctionengine.config.MVCIntegrationTest
import net.jonasmf.auctionengine.generated.model.AuctionHousePageSortBy
import net.jonasmf.auctionengine.mapper.realm.toDbo
import net.jonasmf.auctionengine.service.AuctionHouseService
import net.jonasmf.auctionengine.service.ConnectedRealmService
import net.jonasmf.auctionengine.testsupport.builder.buildConnectedRealm
import net.jonasmf.auctionengine.testsupport.builder.buildRealm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AdminAuctionHouseControllerTest : MVCIntegrationTest() {
    @Autowired
    lateinit var autionHouseService: AuctionHouseService

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
                    path = "$basePath?pageSize=$pageSize&page=$page",
                    roles = listOf("admin"),
                )

            mockMvc
                .perform(asyncDispatch(result))
                .andExpect(status().isOk)
                // It shoud be -2, due to the default sorting
                .andExpect(jsonPath("$.items[0].connectedRealmId").value(-2))
                .andExpect(jsonPath("$.page.page").value(page))
                .andExpect(jsonPath("$.page.pageSize").value(pageSize))
                .andExpect(jsonPath("$.page.totalItems").value(totalItems))
                .andExpect(jsonPath("$.page.totalPages").value(totalPages))
                .andExpect(jsonPath("$.sort.sortBy").value(sortBy))
                .andExpect(jsonPath("$.sort.sortDirection").value(sortDirection))
        }
    }

    @Nested
    inner class Path {
        val connectedRealmId = 509
        val result =
            mvcPatch(
                "$basePath/$connectedRealmId",
                roles = listOf("admin"),
            )
    }
}
