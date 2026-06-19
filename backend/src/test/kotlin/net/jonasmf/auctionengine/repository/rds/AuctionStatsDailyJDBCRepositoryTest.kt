package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import java.time.LocalDateTime

class AuctionStatsDailyJDBCRepositoryTest : IntegrationTestBase() {
    private var updateHistorySequence = 0L

    @Autowired
    lateinit var auctionStatsHourlyJDBCRepository: AuctionStatsHourlyJDBCRepository

    @Autowired
    lateinit var auctionStatsDailyJDBCRepository: AuctionStatsDailyJDBCRepository

    @Autowired
    lateinit var dailyPriceRepository: AuctionHousePriceDailyRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `upsertDailyPriceStatistics - should persist daily statistics and expose daily price view`() {
        val date = LocalDate.of(2026, 1, 2)
        val connectedRealmId = 1
        val itemId = 19019
        val petSpeciesId = 42
        val modifierKey = "7,100"
        val bonusKey = "12251,12252"
        seedConnectedRealm(connectedRealmId)

        listOf(
            HourlyValue(hourOfDay = 1, price = 100, quantity = 10),
            HourlyValue(hourOfDay = 2, price = 200, quantity = 20),
            HourlyValue(hourOfDay = 3, price = 300, quantity = 30),
            HourlyValue(hourOfDay = 4, price = 400, quantity = 40),
            HourlyValue(hourOfDay = 5, price = 500, quantity = 50),
        ).forEach {
            updateHourlyStats(
                hourOfDay = it.hourOfDay,
                date = date,
                connectedRealmId = connectedRealmId,
                itemId = itemId,
                petSpeciesId = petSpeciesId,
                modifierKey = modifierKey,
                bonusKey = bonusKey,
                price = it.price,
                quantity = it.quantity,
            )
        }

        auctionStatsDailyJDBCRepository.upsertDailyPriceStatistics(connectedRealmId, date)

        assertRawDailyRow(
            connectedRealmId = connectedRealmId,
            itemId = itemId,
            date = date,
            petSpeciesId = petSpeciesId,
            modifierKey = modifierKey,
            bonusKey = bonusKey,
            expectedMinPrice = 100,
            expectedAvgPrice = 300,
            expectedP25Price = 200,
            expectedP75Price = 400,
            expectedMaxPrice = 500,
            expectedMinQuantity = 10,
            expectedAvgQuantity = 30,
            expectedMaxQuantity = 50,
        )
        assertDailyPriceView(
            connectedRealmId = connectedRealmId,
            itemId = itemId,
            date = date,
            petSpeciesId = petSpeciesId,
            modifierKey = modifierKey,
            bonusKey = bonusKey,
            expectedMinPrice = 100,
            expectedAvgPrice = 300.0,
            expectedP25Price = 200,
            expectedP75Price = 400,
            expectedMaxPrice = 500,
            expectedMinQuantity = 10,
            expectedAvgQuantity = 30.0,
            expectedMaxQuantity = 50,
        )

        updateHourlyStats(
            hourOfDay = 5,
            date = date,
            connectedRealmId = connectedRealmId,
            itemId = itemId,
            petSpeciesId = petSpeciesId,
            modifierKey = modifierKey,
            bonusKey = bonusKey,
            price = 600,
            quantity = 60,
        )
        auctionStatsDailyJDBCRepository.upsertDailyPriceStatistics(connectedRealmId, date)

        assertRawDailyRow(
            connectedRealmId = connectedRealmId,
            itemId = itemId,
            date = date,
            petSpeciesId = petSpeciesId,
            modifierKey = modifierKey,
            bonusKey = bonusKey,
            expectedMinPrice = 100,
            expectedAvgPrice = 320,
            expectedP25Price = 200,
            expectedP75Price = 400,
            expectedMaxPrice = 600,
            expectedMinQuantity = 10,
            expectedAvgQuantity = 32,
            expectedMaxQuantity = 60,
        )
        assertDailyPriceView(
            connectedRealmId = connectedRealmId,
            itemId = itemId,
            date = date,
            petSpeciesId = petSpeciesId,
            modifierKey = modifierKey,
            bonusKey = bonusKey,
            expectedMinPrice = 100,
            expectedAvgPrice = 320.0,
            expectedP25Price = 200,
            expectedP75Price = 400,
            expectedMaxPrice = 600,
            expectedMinQuantity = 10,
            expectedAvgQuantity = 32.0,
            expectedMaxQuantity = 60,
        )
    }

    @Test
    fun `upsertDailyPriceStatistics - should match percentile_cont for even price sets`() {
        val date = LocalDate.of(2026, 1, 4)
        val connectedRealmId = 2
        val itemId = 19020
        val petSpeciesId = 0
        val modifierKey = ""
        val bonusKey = ""
        seedConnectedRealm(connectedRealmId)

        listOf(
            HourlyValue(hourOfDay = 1, price = 100, quantity = 10),
            HourlyValue(hourOfDay = 2, price = 200, quantity = 20),
            HourlyValue(hourOfDay = 3, price = 300, quantity = 30),
            HourlyValue(hourOfDay = 4, price = 400, quantity = 40),
        ).forEach {
            updateHourlyStats(
                hourOfDay = it.hourOfDay,
                date = date,
                connectedRealmId = connectedRealmId,
                itemId = itemId,
                petSpeciesId = petSpeciesId,
                modifierKey = modifierKey,
                bonusKey = bonusKey,
                price = it.price,
                quantity = it.quantity,
            )
        }

        auctionStatsDailyJDBCRepository.upsertDailyPriceStatistics(connectedRealmId, date)

        assertRawDailyRow(
            connectedRealmId = connectedRealmId,
            itemId = itemId,
            date = date,
            petSpeciesId = petSpeciesId,
            modifierKey = modifierKey,
            bonusKey = bonusKey,
            expectedMinPrice = 100,
            expectedAvgPrice = 250,
            expectedP25Price = 175,
            expectedP75Price = 325,
            expectedMaxPrice = 400,
            expectedMinQuantity = 10,
            expectedAvgQuantity = 25,
            expectedMaxQuantity = 40,
        )
        assertDailyPriceView(
            connectedRealmId = connectedRealmId,
            itemId = itemId,
            date = date,
            petSpeciesId = petSpeciesId,
            modifierKey = modifierKey,
            bonusKey = bonusKey,
            expectedMinPrice = 100,
            expectedAvgPrice = 250.0,
            expectedP25Price = 175,
            expectedP75Price = 325,
            expectedMaxPrice = 400,
            expectedMinQuantity = 10,
            expectedAvgQuantity = 25.0,
            expectedMaxQuantity = 40,
        )
    }

    private fun assertRawDailyRow(
        connectedRealmId: Int,
        itemId: Int,
        date: LocalDate,
        petSpeciesId: Int,
        modifierKey: String,
        bonusKey: String,
        expectedMinPrice: Long,
        expectedAvgPrice: Long,
        expectedP25Price: Long,
        expectedP75Price: Long,
        expectedMaxPrice: Long,
        expectedMinQuantity: Long,
        expectedAvgQuantity: Long,
        expectedMaxQuantity: Long,
    ) {
        val daySuffix = "%02d".format(date.dayOfMonth)
        val row =
            jdbcTemplate.queryForMap(
                """
                SELECT
                    min$daySuffix,
                    avg$daySuffix,
                    price_percentile_25_$daySuffix,
                    price_percentile_75_$daySuffix,
                    max$daySuffix,
                    min_quantity$daySuffix,
                    avg_quantity$daySuffix,
                    max_quantity$daySuffix
                FROM auction_stats_daily
                WHERE connected_realm_id = ?
                  AND item_id = ?
                  AND date = ?
                  AND pet_species_id = ?
                  AND modifier_key = ?
                  AND bonus_key = ?
                """.trimIndent(),
                connectedRealmId,
                itemId,
                date,
                petSpeciesId,
                modifierKey,
                bonusKey,
            )

        assertEquals(expectedMinPrice, row.longValue("min$daySuffix"))
        assertEquals(expectedAvgPrice, row.longValue("avg$daySuffix"))
        assertEquals(expectedP25Price, row.longValue("price_percentile_25_$daySuffix"))
        assertEquals(expectedP75Price, row.longValue("price_percentile_75_$daySuffix"))
        assertEquals(expectedMaxPrice, row.longValue("max$daySuffix"))
        assertEquals(expectedMinQuantity, row.longValue("min_quantity$daySuffix"))
        assertEquals(expectedAvgQuantity, row.longValue("avg_quantity$daySuffix"))
        assertEquals(expectedMaxQuantity, row.longValue("max_quantity$daySuffix"))
    }

    private fun assertDailyPriceView(
        connectedRealmId: Int,
        itemId: Int,
        date: LocalDate,
        petSpeciesId: Int,
        modifierKey: String,
        bonusKey: String,
        expectedMinPrice: Long,
        expectedAvgPrice: Double,
        expectedP25Price: Long,
        expectedP75Price: Long,
        expectedMaxPrice: Long,
        expectedMinQuantity: Long,
        expectedAvgQuantity: Double,
        expectedMaxQuantity: Long,
    ) {
        val dailyPrices =
            dailyPriceRepository.findAllByConnectedRealmIdAndItemIdAndPetSpeciesIdAndModifierKeyAndBonusKey(
                connectedRealmId = connectedRealmId,
                itemId = itemId,
                petSpeciesId = petSpeciesId,
                modifierKey = modifierKey,
                bonusKey = bonusKey,
            )

        assertEquals(1, dailyPrices.size)
        val dailyPrice = dailyPrices.single()
        assertEquals(connectedRealmId, dailyPrice.connectedRealmId)
        assertEquals(itemId, dailyPrice.itemId)
        assertEquals(date, dailyPrice.date)
        assertEquals(petSpeciesId, dailyPrice.petSpeciesId)
        assertEquals(modifierKey, dailyPrice.modifierKey)
        assertEquals(bonusKey, dailyPrice.bonusKey)
        assertEquals(expectedMinPrice, dailyPrice.minPrice)
        assertEquals(expectedAvgPrice, dailyPrice.avgPrice)
        assertEquals(expectedP25Price, dailyPrice.medianPrice25)
        assertEquals(expectedP75Price, dailyPrice.medianPrice75)
        assertEquals(expectedMaxPrice, dailyPrice.maxPrice)
        assertEquals(expectedMinQuantity, dailyPrice.minQuantity)
        assertEquals(expectedAvgQuantity, dailyPrice.avgQuantity)
        assertEquals(expectedMaxQuantity, dailyPrice.maxQuantity)
    }

    private fun updateHourlyStats(
        hourOfDay: Int,
        date: LocalDate,
        connectedRealmId: Int,
        itemId: Int,
        petSpeciesId: Int,
        modifierKey: String,
        bonusKey: String,
        price: Long,
        quantity: Int,
    ) {
        val updateHistoryId = insertUpdateHistory(connectedRealmId, date.atTime(hourOfDay, 0))
        insertAuction(
            id = "auction-$updateHistoryId",
            connectedRealmId = connectedRealmId,
            updateHistoryId = updateHistoryId,
            itemId = itemId,
            date = date,
            hour = hourOfDay,
            petSpeciesId = petSpeciesId,
            modifierKey = modifierKey,
            bonusKey = bonusKey,
            price = price,
            quantity = quantity,
        )
        auctionStatsHourlyJDBCRepository.updateHourlyStats(hourOfDay, updateHistoryId)
    }

    private fun seedConnectedRealm(id: Int) {
        jdbcTemplate.update(
            """
            INSERT INTO auction_house (
                id,
                connected_id,
                auto_update,
                avg_delay,
                game_build,
                highest_delay,
                last_modified,
                last_requested,
                lowest_delay,
                next_update,
                region,
                update_attempts
            ) VALUES (?, ?, false, 60, 0, 60, NULL, NULL, 60, '1970-01-01 00:00:00', 'Europe', 0)
            ON DUPLICATE KEY UPDATE connected_id = VALUES(connected_id)
            """.trimIndent(),
            id,
            id,
        )
        jdbcTemplate.update(
            """
            INSERT INTO connected_realm (id, auction_house_id)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE auction_house_id = VALUES(auction_house_id)
            """.trimIndent(),
            id,
            id,
        )
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
            uniqueUpdateHistoryTimestamp(lastModified),
            lastModified,
            lastModified,
            connectedRealmId,
        )!!

    private fun uniqueUpdateHistoryTimestamp(lastModified: LocalDateTime): LocalDateTime =
        lastModified.plusNanos(++updateHistorySequence * 1_000)

    private fun insertAuction(
        id: String,
        connectedRealmId: Int,
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
            connectedRealmId,
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

    private fun Map<String, Any?>.longValue(column: String): Long = (getValue(column) as Number).toLong()

    private data class HourlyValue(
        val hourOfDay: Int,
        val price: Long,
        val quantity: Int,
    )
}
