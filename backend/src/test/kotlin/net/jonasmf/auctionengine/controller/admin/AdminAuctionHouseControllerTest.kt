package net.jonasmf.auctionengine.controller.admin

import net.jonasmf.auctionengine.config.MVCIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AdminAuctionHouseControllerTest : MVCIntegrationTest() {
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
