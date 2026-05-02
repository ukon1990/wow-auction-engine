package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.config.WaeS3Properties
import net.jonasmf.auctionengine.service.ItemSyncService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class ItemSchedule(
    private val properties: BlizzardApiProperties,
    private val s3Properties: WaeS3Properties,
    private val itemSyncService: ItemSyncService,
    @Value("\${spring.cloud.aws.region.static}")
    private val deploymentAwsRegion: String,
) {
    private val log = LoggerFactory.getLogger(ItemSchedule::class.java)
    private val syncRunning = AtomicBoolean(false)

    @Scheduled(
        cron = "\${app.scheduling.item-sync-cron:0 0 * * * *}",
        zone = "\${app.scheduling.item-sync-zone:GMT+1}",
    )
    fun syncItemsOnSchedule() {
        if (!shouldRunInCurrentDeploymentRegion("scheduled")) return
        runSync("scheduled")
    }

    @Scheduled(
        initialDelayString = "\${app.scheduling.item-sync-startup-delay:PT30S}",
        fixedDelayString = "\${app.scheduling.item-sync-startup-repeat-delay:P3650D}",
    )
    fun syncItemsAfterStartup() {
        if (!shouldRunInCurrentDeploymentRegion("startup")) return
        runSync("startup")
    }

    fun syncItems() {
        runSync("manual")
    }

    private fun shouldRunInCurrentDeploymentRegion(trigger: String): Boolean {
        val expectedAwsRegion = s3Properties.bucketFor(properties.staticDataRegion).bucketRegion
        if (deploymentAwsRegion != expectedAwsRegion) {
            log.info(
                "Skipping {} item sync because deployment AWS region {} does not match static data region {} bucket region {}.",
                trigger,
                deploymentAwsRegion,
                properties.staticDataRegion,
                expectedAwsRegion,
            )
            return false
        }
        return true
    }

    private fun runSync(trigger: String) {
        if (!syncRunning.compareAndSet(false, true)) {
            log.info("Skipping {} item sync because sync already running.", trigger)
            return
        }

        try {
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
        } finally {
            syncRunning.set(false)
        }
    }
}
