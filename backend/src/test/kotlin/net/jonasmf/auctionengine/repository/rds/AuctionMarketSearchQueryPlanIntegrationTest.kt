package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.testsupport.MarketSearchTestFixtures
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate

class AuctionMarketSearchQueryPlanIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var repository: AuctionMarketSearchRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `market search paged sql explains successfully`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        val request = sampleRequest()
        val (sql, args) = repository.buildMarketSearchPagedSqlForExplain(request)
        val rows = jdbcTemplate.queryForList("EXPLAIN $sql", *args)
        assertTrue(rows.isNotEmpty(), "EXPLAIN should return at least one plan row")
    }

    @Test
    fun `price and quantity range sql explains successfully`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        val request = sampleRequest()
        val (sql, args) = repository.buildPriceAndQuantityRangeSqlForExplain(request)
        val rows = jdbcTemplate.queryForList("EXPLAIN $sql", *args)
        assertTrue(rows.isNotEmpty(), "EXPLAIN should return at least one plan row")
    }

    private fun sampleRequest(): AuctionMarketSearchRequest =
        AuctionMarketSearchRequest(
            selectedConnectedRealmId = 1084,
            selectedDate = LocalDate.parse("2026-05-01"),
            selectedHour = 11,
            communityConnectedRealmId = -2,
            communityDate = LocalDate.parse("2026-05-01"),
            communityHour = 10,
            localeColumnSuffix = "en_gb",
            page = 0,
            pageSize = 10,
            sortBy = "itemName",
            sortDirection = "asc",
            query = null,
            qualityIds = emptyList(),
            itemClassIds = emptyList(),
            itemSubclassIds = emptyList(),
            recipeOnly = null,
            minPrice = null,
            maxPrice = null,
            minQuantity = null,
            maxQuantity = null,
        )
}
