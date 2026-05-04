package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.Locale
import net.jonasmf.auctionengine.testsupport.MarketSearchTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate

class AuctionMarketContextServiceTest : IntegrationTestBase() {
    @Autowired
    lateinit var auctionMarketContextService: AuctionMarketContextService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `resolve uses JDBC realm row and matches auction house timestamps`() {
        MarketSearchTestFixtures.seedMarketSearchData(jdbcTemplate)

        val context = auctionMarketContextService.resolve("eu", "argent-dawn", null)

        assertEquals(Locale.EN_GB, context.locale)
        assertEquals("UTC", context.selectedRealmTimezone)
        assertEquals(1084, context.selectedSnapshot.connectedRealmId)
        assertEquals(LocalDate.parse("2026-05-01"), context.selectedSnapshot.date)
        assertEquals(11, context.selectedSnapshot.hour)
        assertEquals(-2, context.commoditySnapshot.connectedRealmId)
        assertEquals(10, context.commoditySnapshot.hour)
    }
}
