package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import net.jonasmf.auctionengine.constant.AuctionTimeLeft
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import net.jonasmf.auctionengine.dbo.rds.realm.ConnectedRealm
import net.jonasmf.auctionengine.dbo.rds.realm.RegionDBO
import net.jonasmf.auctionengine.dto.auction.AuctionDTO
import net.jonasmf.auctionengine.dto.auction.AuctionItemDTO
import net.jonasmf.auctionengine.dto.auction.ModifierDTO
import net.jonasmf.auctionengine.service.HourlyPriceStatisticsService
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
    lateinit var hourlyPriceStatisticsService: HourlyPriceStatisticsService

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

        val bonusKeyColumnCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'v_auction_house_prices'
                  AND column_name = 'bonus_key'
                """.trimIndent(),
                Int::class.java,
            )
        val ahTypeIdColumnCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'v_auction_house_prices'
                  AND column_name = 'ah_type_id'
                """.trimIndent(),
                Int::class.java,
            )

        assertEquals(1, bonusKeyColumnCount)
        assertEquals(0, ahTypeIdColumnCount)
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
                        itemId = 19019,
                        date = date,
                        petSpeciesId = null,
                        modifierKey = "",
                        bonusKey = "",
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
                        itemId = 19019,
                        date = date,
                        petSpeciesId = null,
                        modifierKey = "",
                        bonusKey = "",
                        price = 120_000L,
                        quantity = 15L,
                    ),
                ),
            hour = 7,
        )

        val prices = repository.findAllByConnectedRealmIdAndItemIdOrderByAuctionTimestampAsc(1084, 19019)

        assertEquals(2, prices.size)

        val first = prices[0]
        assertEquals(LocalDateTime.of(2026, 4, 6, 3, 0), first.auctionTimestamp)
        assertEquals(123_456L, first.price)
        assertEquals(10L, first.quantity)
        assertEquals(-1, first.petSpeciesId)
        assertEquals("", first.modifierKey)
        assertEquals("", first.bonusKey)

        val second = prices[1]
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
                        itemId = 19020,
                        date = date,
                        petSpeciesId = 42,
                        modifierKey = "",
                        bonusKey = "",
                        price = 99L,
                        quantity = 2L,
                    ),
                ),
            hour = 11,
        )

        val prices = repository.findAllByConnectedRealmIdAndItemIdOrderByAuctionTimestampAsc(2084, 19020)

        assertEquals(1, prices.size)
        assertEquals(LocalDateTime.of(2026, 4, 7, 11, 0), prices.single().auctionTimestamp)
        assertNotNull(prices.single().auctionTimestamp)
    }

    @Test
    fun `should keep different modifier sets as separate hourly variants`() {
        val connectedRealm = seedConnectedRealm(3084)
        val lastModified = ZonedDateTime.of(2026, 4, 8, 9, 0, 0, 0, ZonedDateTime.now().zone)

        hourlyPriceStatisticsService.processHourlyPriceStatistics(
            connectedRealm = connectedRealm,
            auctions =
                listOf(
                    auction(1, 19021, 500L, 2L, modifiers = listOf(ModifierDTO("9", 100), ModifierDTO("30", 7))),
                    auction(2, 19021, 450L, 3L, modifiers = listOf(ModifierDTO("9", 101), ModifierDTO("30", 7))),
                ),
            lastModified = lastModified,
        )

        val first =
            repository.findAllByConnectedRealmIdAndItemIdAndModifierKeyOrderByAuctionTimestampAsc(
                3084,
                19021,
                "7,100",
            )
        val second =
            repository.findAllByConnectedRealmIdAndItemIdAndModifierKeyOrderByAuctionTimestampAsc(
                3084,
                19021,
                "7,101",
            )

        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(500L, first.single().price)
        assertEquals(450L, second.single().price)
    }

    @Test
    fun `should keep different bonus lists as separate hourly variants`() {
        val connectedRealm = seedConnectedRealm(3584)
        val lastModified = ZonedDateTime.of(2026, 4, 8, 11, 0, 0, 0, ZonedDateTime.now().zone)

        hourlyPriceStatisticsService.processHourlyPriceStatistics(
            connectedRealm = connectedRealm,
            auctions =
                listOf(
                    auction(
                        11,
                        19031,
                        500L,
                        2L,
                        modifiers = listOf(ModifierDTO("9", 100)),
                        bonusLists = listOf(12251, 12252),
                    ),
                    auction(
                        12,
                        19031,
                        450L,
                        3L,
                        modifiers = listOf(ModifierDTO("9", 100)),
                        bonusLists = listOf(12251, 12253),
                    ),
                ),
            lastModified = lastModified,
        )

        val first =
            repository.findAllByConnectedRealmIdAndItemIdAndModifierKeyAndBonusKeyOrderByAuctionTimestampAsc(
                3584,
                19031,
                "100",
                "12251,12252",
            )
        val second =
            repository.findAllByConnectedRealmIdAndItemIdAndModifierKeyAndBonusKeyOrderByAuctionTimestampAsc(
                3584,
                19031,
                "100",
                "12251,12253",
            )

        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(500L, first.single().price)
        assertEquals(450L, second.single().price)
    }

    @Test
    fun `should canonicalize modifier order into one hourly variant`() {
        val connectedRealm = seedConnectedRealm(4084)
        val lastModified = ZonedDateTime.of(2026, 4, 9, 14, 0, 0, 0, ZonedDateTime.now().zone)

        hourlyPriceStatisticsService.processHourlyPriceStatistics(
            connectedRealm = connectedRealm,
            auctions =
                listOf(
                    auction(3, 19022, 700L, 4L, modifiers = listOf(ModifierDTO("30", 7), ModifierDTO("9", 100))),
                    auction(4, 19022, 650L, 5L, modifiers = listOf(ModifierDTO("9", 100), ModifierDTO("30", 7))),
                ),
            lastModified = lastModified,
        )

        val prices =
            repository.findAllByConnectedRealmIdAndItemIdAndModifierKeyOrderByAuctionTimestampAsc(
                4084,
                19022,
                "7,100",
            )

        assertEquals(1, prices.size)
        assertEquals(LocalDateTime.of(2026, 4, 9, 14, 0), prices.single().auctionTimestamp)
        assertEquals(650L, prices.single().price)
        assertEquals(9L, prices.single().quantity)
    }

    @Test
    fun `should canonicalize bonus list order into one hourly variant`() {
        val connectedRealm = seedConnectedRealm(4584)
        val lastModified = ZonedDateTime.of(2026, 4, 9, 16, 0, 0, 0, ZonedDateTime.now().zone)

        hourlyPriceStatisticsService.processHourlyPriceStatistics(
            connectedRealm = connectedRealm,
            auctions =
                listOf(
                    auction(13, 19032, 700L, 4L, bonusLists = listOf(12499, 12252, 12251)),
                    auction(14, 19032, 650L, 5L, bonusLists = listOf(12251, 12499, 12252)),
                ),
            lastModified = lastModified,
        )

        val prices =
            repository.findAllByConnectedRealmIdAndItemIdAndModifierKeyAndBonusKeyOrderByAuctionTimestampAsc(
                4584,
                19032,
                "",
                "12251,12252,12499",
            )

        assertEquals(1, prices.size)
        assertEquals(LocalDateTime.of(2026, 4, 9, 16, 0), prices.single().auctionTimestamp)
        assertEquals(650L, prices.single().price)
        assertEquals(9L, prices.single().quantity)
        assertEquals("12251,12252,12499", prices.single().bonusKey)
    }

    @Test
    fun `should aggregate commodity auctions with empty modifier key`() {
        val connectedRealm = seedConnectedRealm(5084)
        val lastModified = ZonedDateTime.of(2026, 4, 10, 6, 0, 0, 0, ZonedDateTime.now().zone)

        hourlyPriceStatisticsService.processHourlyPriceStatistics(
            connectedRealm = connectedRealm,
            auctions =
                listOf(
                    auction(5, 19023, 20L, 10L),
                    auction(6, 19023, 18L, 2L),
                ),
            lastModified = lastModified,
        )

        val prices =
            repository.findAllByConnectedRealmIdAndItemIdAndModifierKeyOrderByAuctionTimestampAsc(
                5084,
                19023,
                "",
            )

        assertEquals(1, prices.size)
        assertEquals(18L, prices.single().price)
        assertEquals(12L, prices.single().quantity)
    }

    private fun seedConnectedRealm(id: Int): ConnectedRealm {
        regionRepository.save(
            RegionDBO(
                id = 1,
                name = "US",
                type = Region.NorthAmerica,
            ),
        )

        return connectedRealmRepository.save(
            ConnectedRealm(
                id = id,
                auctionHouse =
                    AuctionHouse(
                        id = null,
                        connectedId = id,
                        region = Region.NorthAmerica,
                        lastModified = null,
                        lastRequested = null,
                        nextUpdate = java.time.Instant.EPOCH,
                        lowestDelay = 0L,
                        avgDelay = 60,
                        highestDelay = 0L,
                        tsmFile = null,
                        statsFile = null,
                        auctionFile = null,
                        updateAttempts = 0,
                        updateLog = mutableListOf(),
                    ),
                realms = mutableListOf(),
            ),
        )
    }

    private fun auction(
        id: Long,
        itemId: Int,
        price: Long,
        quantity: Long,
        modifiers: List<ModifierDTO>? = null,
        bonusLists: List<Int>? = null,
    ) = AuctionDTO(
        id = id,
        item =
            AuctionItemDTO(
                id = itemId,
                modifiers = modifiers,
                bonus_lists = bonusLists,
            ),
        quantity = quantity,
        unit_price = null,
        buyout = price,
        time_left = AuctionTimeLeft.LONG,
    )
}
