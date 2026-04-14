package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.config.WaeS3Properties
import net.jonasmf.auctionengine.service.ProfessionRecipeSyncService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class ProfessionRecipeSchedule(
    private val properties: BlizzardApiProperties,
    private val s3Properties: WaeS3Properties,
    private val professionRecipeSyncService: ProfessionRecipeSyncService,
    @Value("\${spring.cloud.aws.region.static}")
    private val deploymentAwsRegion: String,
) {
    private val log = LoggerFactory.getLogger(ProfessionRecipeSchedule::class.java)
    private val syncRunning = AtomicBoolean(false)

    @Scheduled(
        cron = "\${app.scheduling.profession-recipe-sync-cron:0 0 8 ? * WED}",
        zone = "\${app.scheduling.profession-recipe-sync-zone:GMT+1}",
    )
    fun syncProfessionRecipesOnSchedule() {
        if (!shouldRunInCurrentDeploymentRegion("scheduled")) return
        runSync("scheduled")
    }

    /* TODO: Uncomment later, need to find a smart way of dealing with this.
    The import takes a while and I don't really want to run it every time
    but for fresh databases only basically.
    @Scheduled(
        initialDelayString = "\${app.scheduling.profession-recipe-sync-startup-delay:PT2M}",
        fixedDelayString = "\${app.scheduling.profession-recipe-sync-startup-repeat-delay:P3650D}",
    )
    fun syncProfessionRecipesAfterStartup() {
        if (!shouldRunInCurrentDeploymentRegion("startup")) return
        runSync("startup")
    }*/

    fun syncProfessionRecipes() {
        runSync("manual")
    }

    private fun shouldRunInCurrentDeploymentRegion(trigger: String): Boolean {
        val expectedAwsRegion = s3Properties.bucketFor(properties.staticDataRegion).bucketRegion
        if (deploymentAwsRegion != expectedAwsRegion) {
            log.info(
                "Skipping {} profession/recipe sync because deployment AWS region {} does not match static data region {} bucket region {}.",
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
            log.info("Skipping {} profession/recipe sync because sync already running.", trigger)
            return
        }

        try {
            log.info(
                "Starting {} profession/recipe sync for region {} (configured regions={})",
                trigger,
                properties.staticDataRegion,
                properties.configuredRegions,
            )
            professionRecipeSyncService.syncConfiguredStaticDataRegion()
            log.info(
                "Completed {} profession/recipe sync for region {} (configured regions={})",
                trigger,
                properties.staticDataRegion,
                properties.configuredRegions,
            )
        } finally {
            syncRunning.set(false)
        }
    }
}
