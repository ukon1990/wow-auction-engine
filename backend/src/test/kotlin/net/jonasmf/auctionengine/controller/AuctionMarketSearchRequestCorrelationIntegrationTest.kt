package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.interceptor.CLIENT_SESSION_ID_HEADER
import net.jonasmf.auctionengine.interceptor.CORRELATION_ID_HEADER
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
    fun `market search echoes correlation and client session identifiers`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        val correlationId = "0198c728-8f4b-7ccb-9953-d23cc1976587"
        val clientSessionId = "0198c728-a27c-7286-a24e-230ea6be5045"

        performAsync(
            mockMvc
                .perform(
                    get("/api/auctions/search")
                        .contextPath("/api")
                        .param("region", "eu")
                        .param("realmSlug", "argent-dawn")
                        .param("page", "0")
                        .param("pageSize", "10")
                        .header(CORRELATION_ID_HEADER, correlationId)
                        .header(CLIENT_SESSION_ID_HEADER, clientSessionId),
                ).andReturn(),
        ).andExpect(status().isOk)
            .andExpect(header().string(CORRELATION_ID_HEADER, correlationId))
            .andExpect(header().string(CLIENT_SESSION_ID_HEADER, clientSessionId))
    }

    @Test
    fun `market search filters generates correlation id when none is supplied`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)

        performAsync(
            mockMvc
                .perform(
                    get("/api/auctions/search/filters")
                        .contextPath("/api")
                        .param("region", "eu")
                        .param("realmSlug", "argent-dawn")
                ).andReturn(),
        ).andExpect(status().isOk)
            .andExpect(header().exists(CORRELATION_ID_HEADER))
            .andExpect(header().doesNotExist(CLIENT_SESSION_ID_HEADER))
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
