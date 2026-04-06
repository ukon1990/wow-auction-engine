package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

class AuctionHousePriceRepositoryTest : IntegrationTestBase() {
    @Autowired
    lateinit var repository: AuctionHousePriceRepository

    @Autowired
    lateinit var hourlyPriceStatisticsRepository: HourlyPriceStatisticsRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var connectedRealmRepository: ConnectedRealmRepository

    @Autowired
    lateinit var regionRepository: RegionRepository

    @Test
    fun `should create v auction house prices view`() {
        val viewCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.views
                WHERE table_schema = DATABASE()
                  AND table_name = 'v_auction_house_prices'
                """.trimIndent(),
                Int::class.java,
            )

        assertEquals(1, viewCount)
    }

    @Test
    fun `should expose populated hours as individual rows through the view`() {
        seedConnectedRealm(1084)
        val date = LocalDate.of(2026, 4, 6)

        hourlyPriceStatisticsRepository.upsertHour(
            rows =
                listOf(
                    HourlyStatsUpsertRow(
                        connectedRealmId = 1084,
                        ahTypeId = 0,
                        itemId = 19019,
                        date = date,
                        petSpeciesId = null,
                        price = 123_456L,
                        quantity = 10L,
                    ),
                ),
            hour = 3,
        )
        hourlyPriceStatisticsRepository.upsertHour(
            rows =
                listOf(
                    HourlyStatsUpsertRow(
                        connectedRealmId = 1084,
                        ahTypeId = 0,
                        itemId = 19019,
                        date = date,
                        petSpeciesId = null,
                        price = 120_000L,
                        quantity = 15L,
                    ),
                ),
            hour = 7,
        )

        val prices = repository.findAllByConnectedRealmIdAndItemIdOrderByAuctionTimestampAsc(1084, 19019)

        assertEquals(2, prices.size)

        val first = prices[0]
        assertEquals(3, first.hourOfDay)
        assertEquals(LocalDateTime.of(2026, 4, 6, 3, 0), first.auctionTimestamp)
        assertEquals(123_456L, first.price)
        assertEquals(10L, first.quantity)
        assertEquals(-1, first.petSpeciesId)

        val second = prices[1]
        assertEquals(7, second.hourOfDay)
        assertEquals(LocalDateTime.of(2026, 4, 6, 7, 0), second.auctionTimestamp)
        assertEquals(120_000L, second.price)
        assertEquals(15L, second.quantity)
    }

    @Test
    fun `should omit hours with no stored price and quantity`() {
        seedConnectedRealm(2084)
        val date = LocalDate.of(2026, 4, 7)

        hourlyPriceStatisticsRepository.upsertHour(
            rows =
                listOf(
                    HourlyStatsUpsertRow(
                        connectedRealmId = 2084,
                        ahTypeId = 0,
                        itemId = 19020,
                        date = date,
                        petSpeciesId = 42,
                        price = 99L,
                        quantity = 2L,
                    ),
                ),
            hour = 11,
        )

        val prices = repository.findAllByConnectedRealmIdAndItemIdOrderByAuctionTimestampAsc(2084, 19020)

        assertEquals(1, prices.size)
        assertEquals(11, prices.single().hourOfDay)
        assertNotNull(prices.single().auctionTimestamp)
    }

    private fun seedConnectedRealm(id: Int) {
        val region =
            regionRepository.save(
                RegionDBO(
                    id = 1,
                    name = "US",
                    type = Region.NorthAmerica,
                ),
            )

        connectedRealmRepository.save(
            ConnectedRealm(
                id = id,
                auctionHouse =
                    AuctionHouse(
                        id = null,
                        lastModified = null,
                        lastRequested = null,
                        nextUpdate = ZonedDateTime.now(),
                        lowestDelay = 0L,
                        averageDelay = 60,
                        highestDelay = 0L,
                        tsmFile = null,
                        statsFile = null,
                        auctionFile = null,
                        failedAttempts = 0,
                        updateLog = mutableListOf(),
                    ),
                realms = mutableListOf(),
            ),
        )
    }
}
