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
        cron = "\${app.scheduling.deleted-auction-hourly-cleanup-cron:0 0 * * * *}",
        zone = "\${app.scheduling.deleted-auction-cleanup-zone:GMT+1}",
    )
    fun deleteOldHourlyHistoryOnSchedule() {
        deleteOldHourlyHistory("scheduled")
    }

    @Scheduled(
        cron = "\${app.scheduling.deleted-auction-daily-cleanup-cron:0 0 * * * *}",
        zone = "\${app.scheduling.deleted-auction-cleanup-zone:GMT+1}",
    )
    fun deleteOldDailyHistoryOnSchedule() {
        deleteOldDailyHistory("scheduled")
    }

    @Scheduled(
        cron = "\${app.scheduling.deleted-auction-price-cleanup-cron:0 0 * * * *}",
        zone = "\${app.scheduling.deleted-auction-cleanup-zone:GMT+1}",
    )
    fun deleteOldPriceHistoryOnSchedule() {
        deleteOldPriceHistory("scheduled")
    }

    @Scheduled(
        initialDelayString = "\${app.scheduling.deleted-auction-cleanup-startup-delay:PT1M}",
        fixedDelayString = "\${app.scheduling.deleted-auction-cleanup-startup-repeat-delay:P3650D}",
    )
    fun cleanupAfterStartup() {
        deleteOldHourlyHistory("startup")
        deleteOldDailyHistory("startup")
        deleteOldPriceHistory("startup")
    }

    private fun deleteOldHourlyHistory(trigger: String) {
        logger.info("Starting {} hourly auction statistics cleanup.", trigger)
        cleanupService.cleanupHourlyStats()
    }

    private fun deleteOldDailyHistory(trigger: String) {
        logger.info("Starting {} daily auction statistics cleanup.", trigger)
        cleanupService.cleanupDailyStats()
    }

    private fun deleteOldPriceHistory(trigger: String) {
        logger.info("Starting {} auction price history cleanup.", trigger)
        cleanupService.cleanupPriceHistory()
    }
}
