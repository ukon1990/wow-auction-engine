package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncGuard
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class ProfessionRecipeSchedule(
    private val properties: BlizzardApiProperties,
    private val professionRecipeSyncService: ProfessionRecipeSyncService,
    private val professionRecipeSyncGuard: ProfessionRecipeSyncGuard,
    private val backgroundWorkLauncher: BackgroundWorkLauncher,
    @Value("\${app.scheduling.static-data-sync-enabled:true}")
    private val staticDataSyncEnabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(ProfessionRecipeSchedule::class.java)
    private val syncRunning = AtomicBoolean(false)

    @Scheduled(
        cron = "\${app.scheduling.profession-recipe-sync-cron:0 0 8 ? * WED}",
        zone = "\${app.scheduling.profession-recipe-sync-zone:GMT+1}",
    )
    fun syncProfessionRecipesOnSchedule() {
        if (!shouldRunStaticDataSync("scheduled")) return
        runSync("scheduled")
    }

    /**
     * This function is primarily intended to be run upon startup,
     * in cases where I have wiped the database for professions and recipes.
     * For the weekly maintenance we got the function above.
     */
    @Scheduled(
        initialDelayString = "\${app.scheduling.initial-delay}",
        // fixedDelayString = "\${app.scheduling.profession-recipe-sync-startup-repeat-delay:P3650D}",
    )
    fun syncProfessionRecipesAfterStartup() {
        if (!shouldRunStaticDataSync("startup")) return
        if (!professionRecipeSyncService.shouldInitiallySync()) return
        runSync("startup")
    }

    fun syncProfessionRecipes() {
        runSync("manual")
    }

    private fun shouldRunStaticDataSync(trigger: String): Boolean {
        if (!staticDataSyncEnabled) {
            log.info(
                "Skipping {} profession/recipe sync because static data sync is disabled for this deployment.",
                trigger,
            )
            return false
        }
        return true
    }

    private fun runSync(trigger: String) {
        backgroundWorkLauncher.launchSingleFlight(syncRunning, "profession-recipe-sync") {
            val syncLock = professionRecipeSyncGuard.tryAcquire()
            if (syncLock == null) {
                log.info("Skipping {} profession/recipe sync because sync already running.", trigger)
                return@launchSingleFlight
            }

            try {
                log.info(
                    "Starting {} profession/recipe sync for region {} (configured regions={})",
                    trigger,
                    properties.staticDataRegion,
                    properties.configuredRegions,
                )
                professionRecipeSyncService.syncConfiguredStaticDataRegion(syncLock::ensureActive)
                syncLock.ensureActive()
                log.info(
                    "Completed {} profession/recipe sync for region {} (configured regions={})",
                    trigger,
                    properties.staticDataRegion,
                    properties.configuredRegions,
                )
            } finally {
                professionRecipeSyncGuard.release(syncLock)
            }
        }
    }
}
