package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.testsupport.MarketSearchTestFixtures
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class AuctionMarketSearchRequestCorrelationIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `market search echoes x-request-id for log correlation`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)

        mockMvc
            .perform(
                get("/api/auctions/market-search")
                    .contextPath("/api")
                    .param("region", "eu")
                    .param("realmSlug", "argent-dawn")
                    .param("page", "0")
                    .param("pageSize", "10")
                    .header("X-Request-Id", "corr-integration-test-1"),
            ).andExpect(status().isOk)
            .andExpect(header().string("X-Request-Id", "corr-integration-test-1"))
    }

    @Test
    fun `market search filters echoes x-request-id for log correlation`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)

        mockMvc
            .perform(
                get("/api/auctions/market-search/filters")
                    .contextPath("/api")
                    .param("region", "eu")
                    .param("realmSlug", "argent-dawn")
                    .header("X-Request-Id", "corr-integration-test-2"),
            ).andExpect(status().isOk)
            .andExpect(header().string("X-Request-Id", "corr-integration-test-2"))
    }
}
