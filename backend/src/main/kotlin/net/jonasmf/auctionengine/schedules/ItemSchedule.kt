package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.service.ItemSyncService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class ItemSchedule(
    private val properties: BlizzardApiProperties,
    private val itemSyncService: ItemSyncService,
    private val backgroundWorkLauncher: BackgroundWorkLauncher,
    @Value("\${app.scheduling.static-data-sync-enabled:true}")
    private val staticDataSyncEnabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(ItemSchedule::class.java)
    private val syncRunning = AtomicBoolean(false)

    @Scheduled(
        cron = "\${app.scheduling.item-sync-cron:0 0 * * * *}",
        zone = "\${app.scheduling.item-sync-zone:GMT+1}",
    )
    fun syncItemsOnSchedule() {
        if (!shouldRunStaticDataSync("scheduled")) return
        runSync("scheduled")
    }

    @Scheduled(
        initialDelayString = "\${app.scheduling.item-sync-startup-delay:PT30S}",
        fixedDelayString = "\${app.scheduling.item-sync-startup-repeat-delay:P3650D}",
    )
    fun syncItemsAfterStartup() {
        if (!shouldRunStaticDataSync("startup")) return
        runSync("startup")
    }

    fun syncItems() {
        runSync("manual")
    }

    private fun shouldRunStaticDataSync(trigger: String): Boolean {
        if (!staticDataSyncEnabled) {
            log.info(
                "Skipping {} item sync because static data sync is disabled for this deployment.",
                trigger,
            )
            return false
        }
        return true
    }

    private fun runSync(trigger: String) {
        backgroundWorkLauncher.launchSingleFlight(syncRunning, "item-sync") {
            log.info(
                "Starting {} item sync for region {} (configured regions={})",
                trigger,
                properties.staticDataRegion,
                properties.configuredRegions,
            )
            itemSyncService.syncConfiguredStaticDataRegion()
            log.info(
                "Completed {} item sync for region {} (configured regions={})",
                trigger,
                properties.staticDataRegion,
                properties.configuredRegions,
            )
        }
    }
}
