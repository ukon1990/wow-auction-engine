package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.service.TsmRegionSyncService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class TsmRegionSyncSchedule(
    private val properties: BlizzardApiProperties,
    private val tsmRegionSyncService: TsmRegionSyncService,
    private val backgroundWorkLauncher: BackgroundWorkLauncher,
    @Value("\${app.scheduling.static-data-sync-enabled:true}")
    private val staticDataSyncEnabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(TsmRegionSyncSchedule::class.java)
    private val syncRunning = AtomicBoolean(false)

    @Scheduled(
        cron = "\${app.scheduling.tsm-region-sync-cron:0 0 6 * * *}",
        zone = "\${app.scheduling.tsm-region-sync-zone:GMT+1}",
    )
    fun syncTsmRegionOnSchedule() {
        if (!shouldRunStaticDataSync("scheduled")) return
        runSync("scheduled")
    }

    @Scheduled(
        initialDelayString = "\${app.scheduling.tsm-region-sync-startup-delay:PT30S}",
        fixedDelayString = "\${app.scheduling.tsm-region-sync-startup-repeat-delay:P3650D}",
    )
    fun syncTsmRegionAfterStartup() {
        if (!shouldRunStaticDataSync("startup")) return
        runSync("startup")
    }

    fun syncTsmRegion() {
        runSync("manual")
    }

    private fun shouldRunStaticDataSync(trigger: String): Boolean {
        if (!staticDataSyncEnabled) {
            log.info(
                "Skipping {} TSM region sync because static data sync is disabled for this deployment.",
                trigger,
            )
            return false
        }
        return true
    }

    private fun runSync(trigger: String) {
        backgroundWorkLauncher.launchSingleFlight(syncRunning, "tsm-region-sync") {
            log.info(
                "Starting {} TSM region sync for configured regions={}",
                trigger,
                properties.configuredRegions,
            )
            tsmRegionSyncService.syncConfiguredRegions()
            log.info(
                "Completed {} TSM region sync for configured regions={}",
                trigger,
                properties.configuredRegions,
            )
        }
    }
}
