package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.service.AuctionHouseService
import net.jonasmf.auctionengine.service.AuctionStatsDailyService
import net.jonasmf.auctionengine.service.AuctionStatsHourlyService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Component
class DeletedAuctionCleanupSchedule(
    private val auctionHouseService: AuctionHouseService,
    private val hourlyService: AuctionStatsHourlyService,
    private val dailyService: AuctionStatsDailyService,
) {
    private val logger: Logger = LoggerFactory.getLogger(DeletedAuctionCleanupSchedule::class.java)

    @Scheduled(
        cron = "\${app.scheduling.deleted-auction-cleanup-cron:0 0 4 * * *}",
        zone = "\${app.scheduling.deleted-auction-cleanup-zone:GMT+1}",
    ) // dbo.auction_house.last_history_delete_event og _daily
    fun deleteOldHourlyHistory() {
        val hourlyTTL = OffsetDateTime.now(ZoneOffset.UTC).minusDays(14)
        logger.info("Checking for old hourly old history rows to cleanup.")
        auctionHouseService.getReadyForHourlyStatsCleanup(hourlyTTL)
        // hourlyService
    }

    @Scheduled(
        cron = "\${app.scheduling.deleted-auction-cleanup-cron:0 0 4 * * *}",
        zone = "\${app.scheduling.deleted-auction-cleanup-zone:GMT+1}",
    ) // dbo.auction_house.last_history_delete_event og _daily
    fun deleteOldDailyHistory() {
        val dailyTTL = ZonedDateTime.now(ZoneOffset.UTC).minusMonths(4)
    }

    @Scheduled(
        cron = "\${app.scheduling.deleted-auction-cleanup-cron:0 0 4 * * *}",
        zone = "\${app.scheduling.deleted-auction-cleanup-zone:GMT+1}",
    ) // dbo.auction_house.last_history_delete_event og _daily
    fun deleteOldPriceHistory() {
        val priceTTL = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7)
    }
}
