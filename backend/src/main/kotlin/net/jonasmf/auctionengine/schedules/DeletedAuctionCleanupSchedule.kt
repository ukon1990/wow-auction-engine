package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.service.DeletedAuctionCleanupService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock

@Component
class DeletedAuctionCleanupSchedule(
    private val cleanupService: DeletedAuctionCleanupService,
    private val backgroundWorkLauncher: BackgroundWorkLauncher,
) {
    private val logger: Logger = LoggerFactory.getLogger(DeletedAuctionCleanupSchedule::class.java)
    private val hourlyCleanupRunning = AtomicBoolean(false)
    private val dailyCleanupRunning = AtomicBoolean(false)
    private val priceCleanupRunning = AtomicBoolean(false)

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
        backgroundWorkLauncher.launchSingleFlight(hourlyCleanupRunning, "deleted-auction-hourly-cleanup") {
            val startTime = Clock.System.now().toEpochMilliseconds()
            logger.info("Starting {} hourly auction statistics cleanup.", trigger)
            cleanupService.cleanupHourlyStats()
            val endTime = Clock.System.now().toEpochMilliseconds()
            logger.info("Completed {} hourly history cleanup in {} ms", trigger, endTime - startTime)
        }
    }

    private fun deleteOldDailyHistory(trigger: String) {
        backgroundWorkLauncher.launchSingleFlight(dailyCleanupRunning, "deleted-auction-daily-cleanup") {
            val startTime = Clock.System.now().toEpochMilliseconds()
            logger.info("Starting {} daily auction statistics cleanup.", trigger)
            cleanupService.cleanupDailyStats()
            val endTime = Clock.System.now().toEpochMilliseconds()
            logger.info("Completed {} daily history cleanup in {} ms", trigger, endTime - startTime)
        }
    }

    private fun deleteOldPriceHistory(trigger: String) {
        backgroundWorkLauncher.launchSingleFlight(priceCleanupRunning, "deleted-auction-price-cleanup") {
            val startTime = Clock.System.now().toEpochMilliseconds()
            logger.info("Starting {} auction price history cleanup.", trigger)
            cleanupService.cleanupPriceHistory()
            val endTime = Clock.System.now().toEpochMilliseconds()
            logger.info("Completed {} auction price history cleanup in {} ms", trigger, endTime - startTime)
        }
    }
}
