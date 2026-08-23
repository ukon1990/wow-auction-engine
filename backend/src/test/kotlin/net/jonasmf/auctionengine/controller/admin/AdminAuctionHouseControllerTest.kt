package net.jonasmf.auctionengine.controller.admin

import net.jonasmf.auctionengine.config.MVCIntegrationTest
import net.jonasmf.auctionengine.mapper.realm.toDbo
import net.jonasmf.auctionengine.service.AuctionHouseService
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

    @BeforeEach
    fun setupData() {
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
        autionHouseService.createIfMissing(connectedRealmB.toDbo())
    }

    @Test
    fun getAuctionHouseById() {
        assertEquals(1, 2)
    }

    @Nested
    inner class SearchAuctionHouses {
        @Test
        fun `lists auction houses in a page for an administrator`() {
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
                .andExpect(jsonPath("$.items[0].connectedRealmId").value(1))
                .andExpect(jsonPath("$.items[1].connectedRealmId").value(2))
                .andExpect(jsonPath("$.page.page").value(0))
                .andExpect(jsonPath("$.page.pageSize").value(2))
                .andExpect(jsonPath("$.page.totalItems").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.sort.sortBy").value("connectedRealmId"))
                .andExpect(jsonPath("$.sort.sortDirection").value("asc"))
        }
    }
}
