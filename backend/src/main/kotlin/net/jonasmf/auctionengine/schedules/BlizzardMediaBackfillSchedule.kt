package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.service.BlizzardMediaBackfillService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class BlizzardMediaBackfillSchedule(
    private val properties: BlizzardApiProperties,
    private val blizzardMediaBackfillService: BlizzardMediaBackfillService,
    @Value("\${app.scheduling.static-data-sync-enabled:true}")
    private val staticDataSyncEnabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(BlizzardMediaBackfillSchedule::class.java)
    private val backfillRunning = AtomicBoolean(false)

    @Scheduled(
        cron = "\${app.scheduling.media-backfill-cron:0 30 * * * *}",
        zone = "\${app.scheduling.media-backfill-zone:GMT+1}",
    )
    fun backfillMediaOnSchedule() {
        if (!shouldRunStaticDataSync("scheduled")) return
        runBackfill("scheduled")
    }

    @Scheduled(
        initialDelayString = "\${app.scheduling.media-backfill-startup-delay:PT2M}",
        fixedDelayString = "\${app.scheduling.media-backfill-startup-repeat-delay:P3650D}",
    )
    fun backfillMediaAfterStartup() {
        if (!shouldRunStaticDataSync("startup")) return
        runBackfill("startup")
    }

    fun backfillMedia() {
        runBackfill("manual")
    }

    private fun shouldRunStaticDataSync(trigger: String): Boolean {
        if (!staticDataSyncEnabled) {
            log.info(
                "Skipping {} media backfill because static data sync is disabled for this deployment.",
                trigger,
            )
            return false
        }
        return true
    }

    private fun runBackfill(trigger: String) {
        if (!backfillRunning.compareAndSet(false, true)) {
            log.info("Skipping {} media backfill because backfill already running.", trigger)
            return
        }

        try {
            log.info(
                "Starting {} media backfill for region {} (configured regions={})",
                trigger,
                properties.staticDataRegion,
                properties.configuredRegions,
            )
            val result = blizzardMediaBackfillService.backfillConfiguredStaticDataRegion()
            log.info(
                "Completed {} media backfill for region {} itemUpdates={} itemAppearanceUpdates={} recipeUpdates={} professionUpdates={} totalUpdates={}",
                trigger,
                result.region,
                result.itemUpdates,
                result.itemAppearanceUpdates,
                result.recipeUpdates,
                result.professionUpdates,
                result.totalUpdates,
            )
        } finally {
            backfillRunning.set(false)
        }
    }
}
