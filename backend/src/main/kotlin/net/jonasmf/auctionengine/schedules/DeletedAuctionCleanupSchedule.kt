package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.service.DeletedAuctionCleanupService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DeletedAuctionCleanupSchedule(
    private val cleanupService: DeletedAuctionCleanupService,
) {
    private val logger: Logger = LoggerFactory.getLogger(DeletedAuctionCleanupSchedule::class.java)

    @Scheduled(
        cron = "\${app.scheduling.deleted-auction-cleanup-cron:0 0 4 * * *}",
        zone = "\${app.scheduling.deleted-auction-cleanup-zone:GMT+1}",
    )
    fun deleteOldHourlyHistory() {
        logger.info("Starting scheduled hourly auction statistics cleanup.")
        cleanupService.cleanupHourlyStats()
    }

    @Scheduled(
        cron = "\${app.scheduling.deleted-auction-cleanup-cron:0 0 4 * * *}",
        zone = "\${app.scheduling.deleted-auction-cleanup-zone:GMT+1}",
    )
    fun deleteOldDailyHistory() {
        logger.info("Starting scheduled daily auction statistics cleanup.")
        cleanupService.cleanupDailyStats()
    }

    @Scheduled(
        cron = "\${app.scheduling.deleted-auction-cleanup-cron:0 0 4 * * *}",
        zone = "\${app.scheduling.deleted-auction-cleanup-zone:GMT+1}",
    )
    fun deleteOldPriceHistory() {
        logger.info("Starting scheduled auction price history cleanup.")
        cleanupService.cleanupPriceHistory()
    }
}
