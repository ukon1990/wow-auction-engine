package net.jonasmf.auctionengine.auction

import net.jonasmf.auctionengine.config.MVCIntegrationTest
import net.jonasmf.auctionengine.testsupport.AuctionSearchTestFixtures
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AuctionControllerTest : MVCIntegrationTest() {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val realm = "region=eu&realmSlug=argent-dawn"

    @BeforeEach
    fun seedAuctions() {
        AuctionSearchTestFixtures.seedAuctionData(jdbcTemplate)
        AuctionSearchTestFixtures.addCraftingData(jdbcTemplate)
    }

    @Test
    fun `searches the auction market and its filters anonymously`() {
        mvcGet("/api/auctions/search?$realm&page=0&pageSize=10", actor = null)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalItems").isNumber)
        mvcGet("/api/auctions/search/filters?$realm", actor = null).andExpect(status().isOk)
    }

    @Test
    fun `returns item detail with crafting profit`() {
        mvcGet("/api/auctions/items/detail?$realm&itemId=19019&preferredRecipeId=7001", actor = null)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.craftings[0].recipeId").value(7001))
            .andExpect(jsonPath("$.craftings[0].profit").value(900))
    }

    @Test
    fun `searches crafting recipes by profit`() {
        mvcGet("/api/crafting/search?$realm&page=0&pageSize=10&sortBy=itemName&sortDirection=asc", actor = null)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].recipeId").value(7001))
            .andExpect(jsonPath("$.items[0].profitCopper").value(900))
        mvcGet("/api/crafting/filters?$realm", actor = null).andExpect(status().isOk)
    }
}
