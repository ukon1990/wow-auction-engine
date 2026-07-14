package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.Region
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
    fun `filter option sql explains successfully`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        val request = sampleRequest()
        val (qualitySql, qualityArgs) = repository.buildQualityOptionsSqlForExplain(request)
        val (itemClassSql, itemClassArgs) = repository.buildItemClassOptionsSqlForExplain(request)
        val (itemSubclassSql, itemSubclassArgs) = repository.buildItemSubclassOptionsSqlForExplain(request)

        val qualityRows = jdbcTemplate.queryForList("EXPLAIN $qualitySql", *qualityArgs)
        val itemClassRows = jdbcTemplate.queryForList("EXPLAIN $itemClassSql", *itemClassArgs)
        val itemSubclassRows = jdbcTemplate.queryForList("EXPLAIN $itemSubclassSql", *itemSubclassArgs)

        assertTrue(qualityRows.isNotEmpty(), "EXPLAIN should return a plan for quality options")
        assertTrue(itemClassRows.isNotEmpty(), "EXPLAIN should return a plan for item class options")
        assertTrue(itemSubclassRows.isNotEmpty(), "EXPLAIN should return a plan for item subclass options")
    }

    private fun sampleRequest(): AuctionMarketSearchRequest =
        AuctionMarketSearchRequest(
            region = Region.Europe,
            selectedConnectedRealmId = 1084,
            selectedDate = LocalDate.parse("2026-05-01"),
            selectedHour = 11,
            commodityConnectedRealmId = -2,
            commodityDate = LocalDate.parse("2026-05-01"),
            commodityHour = 10,
            localeColumnSuffix = "en_gb",
            page = 0,
            pageSize = 10,
            sortBy = "itemName",
            sortDirection = "asc",
            query = null,
            qualityIds = emptyList(),
            itemClassIds = emptyList(),
            itemSubclassIds = emptyList(),
            expansionIds = emptyList(),
            recipeOnly = null,
            minPrice = null,
            maxPrice = null,
            minQuantity = null,
            maxQuantity = null,
            minSaleRatePercent = null,
            maxSaleRatePercent = null,
            minSoldPerDay = null,
            maxSoldPerDay = null,
        )
}
