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

class AuctionHousePriceRepositoryTest : IntegrationTestBase() {
    @Autowired
    lateinit var repository: AuctionHousePriceRepository

    @Autowired
    lateinit var auctionStatsHourlyJDBCRepository: AuctionStatsHourlyJDBCRepository

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
        val connectedRealm = seedConnectedRealm(1084)
        val date = LocalDate.of(2026, 4, 6)

        updateHourlyStats(
            connectedRealm = connectedRealm,
            itemId = 19019,
            date = date,
            petSpeciesId = null,
            modifierKey = "",
            bonusKey = "",
            price = 123_456L,
            quantity = 10,
            hour = 3,
        )
        updateHourlyStats(
            connectedRealm = connectedRealm,
            itemId = 19019,
            date = date,
            petSpeciesId = null,
            modifierKey = "",
            bonusKey = "",
            price = 120_000L,
            quantity = 15,
            hour = 7,
        )

        val prices = repository.findAllByConnectedRealmIdAndItemIdOrderByTimestampAsc(1084, 19019)

        assertEquals(2, prices.size)

        val first = prices[0]
        assertEquals(LocalDateTime.of(2026, 4, 6, 3, 0), first.timestamp)
        assertEquals(123_456L, first.price)
        assertEquals(10L, first.quantity)
        assertEquals(-1, first.petSpeciesId)
        assertEquals("", first.modifierKey)
        assertEquals("", first.bonusKey)

        val second = prices[1]
        assertEquals(LocalDateTime.of(2026, 4, 6, 7, 0), second.timestamp)
        assertEquals(120_000L, second.price)
        assertEquals(15L, second.quantity)
    }

    @Test
    fun `should omit hours with no stored price and quantity`() {
        val connectedRealm = seedConnectedRealm(2084)
        val date = LocalDate.of(2026, 4, 7)

        updateHourlyStats(
            connectedRealm = connectedRealm,
            itemId = 19020,
            date = date,
            petSpeciesId = 42,
            modifierKey = "",
            bonusKey = "",
            price = 99L,
            quantity = 2,
            hour = 11,
        )

        val prices = repository.findAllByConnectedRealmIdAndItemIdOrderByTimestampAsc(2084, 19020)

        assertEquals(1, prices.size)
        assertEquals(LocalDateTime.of(2026, 4, 7, 11, 0), prices.single().timestamp)
        assertNotNull(prices.single().timestamp)
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
                        updateAttempts = 0,
                    ),
                realms = mutableListOf(),
            ),
        )
    }

    private fun updateHourlyStats(
        connectedRealm: ConnectedRealm,
        itemId: Int,
        date: LocalDate,
        petSpeciesId: Int?,
        modifierKey: String,
        bonusKey: String,
        price: Long,
        quantity: Int,
        hour: Int,
    ) {
        require(hour in 0..23)
        val updateHistoryId = insertUpdateHistory(connectedRealm.id, date.atTime(hour, 0))
        insertAuction(
            id = "auction-${connectedRealm.id}-$itemId-${date}-$hour",
            connectedRealm = connectedRealm,
            updateHistoryId = updateHistoryId,
            itemId = itemId,
            date = date,
            hour = hour,
            petSpeciesId = petSpeciesId,
            modifierKey = modifierKey,
            bonusKey = bonusKey,
            price = price,
            quantity = quantity,
        )
        auctionStatsHourlyJDBCRepository.updateHourlyStats(hour, updateHistoryId)
    }

    private fun insertUpdateHistory(
        connectedRealmId: Int,
        lastModified: LocalDateTime,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO connected_realm_update_history (
                auction_count,
                last_modified,
                update_timestamp,
                completed_timestamp,
                connected_realm_id
            ) VALUES (1, ?, ?, ?, ?)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            lastModified,
            lastModified,
            lastModified,
            connectedRealmId,
        )!!

    private fun insertAuction(
        id: String,
        connectedRealm: ConnectedRealm,
        updateHistoryId: Long,
        itemId: Int,
        date: LocalDate,
        hour: Int,
        petSpeciesId: Int?,
        modifierKey: String,
        bonusKey: String,
        price: Long,
        quantity: Int,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO auction (
                id,
                connected_realm_id,
                item_id,
                pet_breed_id,
                pet_species_id,
                pet_quality_id,
                pet_level,
                modifier_key,
                bonus_key,
                buyout,
                bid,
                p25,
                p75,
                quantity,
                first_seen,
                last_seen,
                update_history_id
            ) VALUES (?, ?, ?, NULL, ?, NULL, NULL, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            connectedRealm.id,
            itemId,
            petSpeciesId,
            modifierKey,
            bonusKey,
            price,
            price,
            price,
            quantity,
            date.atTime(hour, 0),
            date.atTime(hour, 0),
            updateHistoryId,
        )
    }
}
