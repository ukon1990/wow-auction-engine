package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.dbo.rds.auction.AuctionStatsDaily
import net.jonasmf.auctionengine.repository.rds.AuctionStatsDailyJDBCRepository
import net.jonasmf.auctionengine.utility.datesBetween
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

data class AuctionStatsDailyUpdateResult(
    val processedDates: List<LocalDate>,
    val updatedRows: Int,
) {
    val lastProcessedDate: LocalDate? = processedDates.lastOrNull()
}

@Service
class AuctionStatsDailyService(
    private val auctionStatsDailyJDBCRepository: AuctionStatsDailyJDBCRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * For a given realm, then we check which days that do not have daily stats,
     * and upsert those.
     * Remember to update the timestamp in auction_house, so we can keep track of this info
     */
    fun updateForDate(
        connectedRealmId: Int,
        lastUpdated: LocalDate,
        endDate: LocalDate = LocalDate.now().minusDays(1),
    ): AuctionStatsDailyUpdateResult {
        val startDate = lastUpdated.plusDays(1)
        if (endDate.isBefore(startDate)) {
            logger.info(
                "No daily price statistics to update for connected realm: $connectedRealmId, last updated: $lastUpdated, end date: $endDate",
            )
            return AuctionStatsDailyUpdateResult(emptyList(), 0)
        }

        val dates = datesBetween(startDate, endDate)
        val startTime = System.currentTimeMillis()
        var totalUpdatedRows = 0
        dates.forEach { date ->
            logger.info("Updating daily price statistics for connected realm: $connectedRealmId, date: $date")
            val updatedRows = auctionStatsDailyJDBCRepository.upsertDailyPriceStatistics(connectedRealmId, date)
            totalUpdatedRows += updatedRows
            val durationSeconds = (System.currentTimeMillis() - startTime) / 1000.0
            logger.info(
                "Updated daily price statistics for connected realm: $connectedRealmId, date: $date, rows affected: $updatedRows, duration: ${"%.2f".format(
                    durationSeconds,
                )} seconds",
            )
        }
        return AuctionStatsDailyUpdateResult(dates, totalUpdatedRows)
    }

    fun getStatsForConnectedRealm(
        connectedRealmId: Int,
        itemId: Int,
        petSpeciesId: Int? = null,
        modifierKey: String? = null,
        bonusKey: String? = null,
    ): List<AuctionStatsDaily> = emptyList()
}
