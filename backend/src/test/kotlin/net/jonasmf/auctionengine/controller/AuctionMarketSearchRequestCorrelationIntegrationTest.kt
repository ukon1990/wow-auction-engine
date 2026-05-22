package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.testsupport.MarketSearchTestFixtures
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class AuctionMarketSearchRequestCorrelationIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private fun performAsync(result: MvcResult) = mockMvc.perform(asyncDispatch(result))

    @Test
    fun `market search echoes x-request-id for log correlation`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)

        performAsync(
            mockMvc
                .perform(
                    get("/api/auctions/search")
                        .contextPath("/api")
                        .param("region", "eu")
                        .param("realmSlug", "argent-dawn")
                        .param("page", "0")
                        .param("pageSize", "10")
                        .header("X-Request-Id", "corr-integration-test-1"),
                ).andReturn(),
        ).andExpect(status().isOk)
            .andExpect(header().string("X-Request-Id", "corr-integration-test-1"))
    }

    @Test
    fun `market search filters echoes x-request-id for log correlation`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)

        performAsync(
            mockMvc
                .perform(
                    get("/api/auctions/search/filters")
                        .contextPath("/api")
                        .param("region", "eu")
                        .param("realmSlug", "argent-dawn")
                        .header("X-Request-Id", "corr-integration-test-2"),
                ).andReturn(),
        ).andExpect(status().isOk)
            .andExpect(header().string("X-Request-Id", "corr-integration-test-2"))
    }

    @Test
    fun `market search endpoint returns commodity only rows on first page`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        MarketSearchTestFixtures.seedCommodityOnlyItem(jdbcTemplate)

        performAsync(
            mockMvc
                .perform(
                    get("/api/auctions/search")
                        .contextPath("/api")
                        .param("region", "eu")
                        .param("realmSlug", "argent-dawn")
                        .param("page", "0")
                        .param("pageSize", "10"),
                ).andReturn(),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items[?(@.item.id == 19020)].preferredScope").value(contains("commodity")))
            .andExpect(jsonPath("$.items[?(@.item.id == 19020)].isCommodity").value(contains(true)))
            .andExpect(jsonPath("$.items[?(@.item.id == 19020)].listingPrice").value(contains(555)))
            .andExpect(jsonPath("$.items[?(@.item.id == 19020)].commodity.price").value(contains(555)))
    }
}
