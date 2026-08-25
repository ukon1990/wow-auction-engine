package net.jonasmf.auctionengine.controller.admin

import net.jonasmf.auctionengine.config.MVCIntegrationTest
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

    @BeforeEach
    fun setupData() {
        connectedRealmService.updateRealms()
        /*
        var connectedRealmA =
            buildConnectedRealm(
                id = 1,
                realms =
                    mutableListOf(
                        buildRealm(
                            id = 1,
                            name = "a",
                        ),
                    ),
            )
        var connectedRealmB =
            buildConnectedRealm(
                id = 2,
                realms =
                    mutableListOf(
                        buildRealm(
                            id = 2,
                            name = "b",
                        ),
                    ),
            )
        autionHouseService.createIfMissing(connectedRealmA.toDbo())
        autionHouseService.createIfMissing(connectedRealmB.toDbo())*/
    }

    @Test
    fun getAuctionHouseById() {
        assertEquals(1, 2)
    }

    @Nested
    inner class SearchAuctionHouses {
        @Test
        fun `lists auction houses in a page for an administrator`() {
            val page = 1
            val pageSize = 10
            val totalItems = 41
            val totalPages = 5
            val sortDirection = "asc"
            val sortBy = "ah.id"

            val result =
                mockMvc
                    .perform(
                        get("/api/admin/auction-houses")
                            .contextPath("/api")
                            .with(
                                jwt()
                                    .jwt { token -> token.claim("cognito:groups", listOf("admin")) }
                                    .authorities(cognitoGroupsGrantedAuthoritiesConverter),
                            ),
                    ).andExpect(request().asyncStarted())
                    .andReturn()

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
}
