package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.config.MVCIntegrationTest
import net.jonasmf.auctionengine.service.ConnectedRealmService
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class RealmControllerTest : MVCIntegrationTest() {
    @Autowired
    private lateinit var connectedRealmService: ConnectedRealmService

    @BeforeEach
    fun loadRealms() {
        connectedRealmService.updateRealms()
    }

    @Test
    fun `lists the realm catalog for anonymous callers with server timing`() {
        mvcGet("/api/realms", actor = null)
            .andExpect(status().isOk)
            .andExpect(header().string("Server-Timing", containsString("total")))
            .andExpect(jsonPath("$[*].slug", hasItem("garona")))
    }

    @Test
    fun `returns realm detail with its auction house`() {
        mvcGet("/api/realms/eu/garona", actor = null)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.realm.slug").value("garona"))
            .andExpect(jsonPath("$.auctionHouse.connectedRealmId").value(509))
    }

    @Test
    fun `returns 404 for an unknown realm or region`() {
        mvcGet("/api/realms/eu/ghost", actor = null).andExpect(status().isNotFound)
        mvcGet("/api/realms/zz/garona", actor = null).andExpect(status().isNotFound)
    }
}
