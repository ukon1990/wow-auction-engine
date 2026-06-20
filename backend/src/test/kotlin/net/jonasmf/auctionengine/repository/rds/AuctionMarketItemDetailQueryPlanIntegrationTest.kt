package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.testsupport.MarketSearchTestFixtures
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate

class AuctionMarketItemDetailQueryPlanIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var repository: AuctionMarketItemDetailRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `item detail sql explains successfully`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)
        val from = LocalDate.parse("2026-04-18")
        val to = LocalDate.parse("2026-05-01")

        val (daily, dailyArgs) =
            repository.buildDailySqlAndArgs(
                connectedRealmId = 1084,
                itemId = 19019,
                fromDate = from,
                toDate = to,
                variant = false,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
            )
        val (dailyVar, dailyVarArgs) =
            repository.buildDailySqlAndArgs(
                connectedRealmId = 1084,
                itemId = 19019,
                fromDate = from,
                toDate = to,
                variant = true,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
            )
        val (hourly, hourlyArgs) =
            repository.buildHourlySqlAndArgs(
                connectedRealmId = 1084,
                itemId = 19019,
                fromDate = from,
                toDate = to,
                variant = false,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
            )
        val (pie, pieArgs) =
            repository.buildPieSqlAndArgs(
                connectedRealmId = 1084,
                itemId = 19019,
                statDate = to,
                variant = false,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
            )
        val (currentListings, currentListingsArgs) =
            repository.buildCurrentListingsSqlAndArgs(
                connectedRealmId = 1084,
                itemId = 19019,
                variant = false,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
            )
        val (craft, craftArgs) =
            repository.buildCraftingSqlAndArgs(
                connectedRealmId = 1084,
                commodityConnectedRealmId = -2,
                itemId = 19019,
                statDate = to,
                commodityStatDate = to,
                hourOfDay = 11,
                commodityHourOfDay = 10,
                variant = false,
                bonusKey = "",
                modifierKey = "",
                petSpeciesId = 0,
                preferredRecipeId = null,
                localeColumnSuffix = "en_gb",
            )

        assertTrue(jdbcTemplate.queryForList("EXPLAIN $daily", *dailyArgs).isNotEmpty())
        assertTrue(jdbcTemplate.queryForList("EXPLAIN $dailyVar", *dailyVarArgs).isNotEmpty())
        assertTrue(jdbcTemplate.queryForList("EXPLAIN $hourly", *hourlyArgs).isNotEmpty())
        assertTrue(jdbcTemplate.queryForList("EXPLAIN $pie", *pieArgs).isNotEmpty())
        assertTrue(jdbcTemplate.queryForList("EXPLAIN $currentListings", *currentListingsArgs).isNotEmpty())
        assertTrue(jdbcTemplate.queryForList("EXPLAIN $craft", *craftArgs).isNotEmpty())
    }
}
