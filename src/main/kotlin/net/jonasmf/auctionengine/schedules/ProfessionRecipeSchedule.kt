package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class ProfessionRecipeSchedule(
    private val properties: BlizzardApiProperties,
    private val professionRecipeSyncService: ProfessionRecipeSyncService,
) {
    private val log = LoggerFactory.getLogger(ProfessionRecipeSchedule::class.java)
    private val syncRunning = AtomicBoolean(false)

    @Scheduled(
        cron = "\${app.scheduling.profession-recipe-sync-cron:0 0 8 ? * WED}",
        zone = "\${app.scheduling.profession-recipe-sync-zone:GMT+1}",
    )
    fun syncProfessionRecipesOnSchedule() {
        runSync("scheduled")
    }

    @Scheduled(
        initialDelayString = "\${app.scheduling.profession-recipe-sync-startup-delay:PT2M}",
        fixedDelayString = "\${app.scheduling.profession-recipe-sync-startup-repeat-delay:P3650D}",
    )
    fun syncProfessionRecipesAfterStartup() {
        runSync("startup")
    }

    fun syncProfessionRecipes() {
        runSync("manual")
    }

    private fun runSync(trigger: String) {
        if (!syncRunning.compareAndSet(false, true)) {
            log.info("Skipping {} profession/recipe sync because sync already running.", trigger)
            return
        }

        try {
            log.info("Starting {} profession/recipe sync for regions {}", trigger, properties.configuredRegions)
            professionRecipeSyncService.syncAllConfiguredRegions()
            log.info("Completed {} profession/recipe sync for regions {}", trigger, properties.configuredRegions)
        } finally {
            syncRunning.set(false)
        }
    }
}
