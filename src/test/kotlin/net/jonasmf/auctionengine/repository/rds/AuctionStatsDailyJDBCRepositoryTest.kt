package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.config.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class AuctionStatsDailyJDBCRepositoryTest : IntegrationTestBase() {
    @Autowired
    lateinit var auctionStatsHourlyJDBCRepository: AuctionStatsHourlyJDBCRepository

    @Test
    fun `upsertDailyPriceStatistics - should update daily price statistics`() {
        val date = LocalDate.of(1337, 1, 2)
        upsertHourly(
            hourOfDay = 1,
            date = date,
            price = 100,
            quantity = 10,
        )
        upsertHourly(
            hourOfDay = 2,
            date = date,
            price = 200,
            quantity = 10,
        )
        upsertHourly(
            hourOfDay = 3,
            date = date,
            price = 300,
            quantity = 10,
        )
        upsertHourly(
            hourOfDay = 4,
            date = date,
            price = 400,
            quantity = 10,
        )
        upsertHourly(
            hourOfDay = 5,
            date = date,
            price = 500,
            quantity = 10,
        )

        val result = jd
    }

    private fun upsertHourly(hourOfDay: Int, date: LocalDate, price: Long, quantity: Long) {
        auctionStatsHourlyJDBCRepository.upsertHour(
            listOf(
                HourlyStatsUpsertRow(
                    connectedRealmId = 1,
                    itemId = 1,
                    date = date,
                    price = price,
                    quantity = quantity,
                    petSpeciesId = TODO(),
                    modifierKey = TODO(),
                    bonusKey = TODO(),
                ),
            ),
            hourOfDay,
        )
    }
}
