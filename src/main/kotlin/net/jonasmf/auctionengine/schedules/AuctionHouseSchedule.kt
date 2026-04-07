package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.service.AuctionHouseService
import net.jonasmf.auctionengine.service.BlizzardAuctionService
import net.jonasmf.auctionengine.service.RuntimeHealthTracker
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class AuctionHouseSchedule(
    private val properties: BlizzardApiProperties,
    private val blizzardAuctionService: BlizzardAuctionService,
    private val auctionHouseService: AuctionHouseService,
    private val runtimeHealthTracker: RuntimeHealthTracker,
) {
    val logger: Logger = LoggerFactory.getLogger(AuctionHouseSchedule::class.java)
    private val updateBatchRunning = AtomicBoolean(false)

    @Scheduled(fixedDelayString = "PT1M", initialDelay = 3_000)
    fun checkForUpdates() {
        if (!updateBatchRunning.compareAndSet(false, true)) {
            logger.info("Skipping scheduled auction house update check because an update batch is already running.")
            return
        }

        val batchStartTime = System.currentTimeMillis()
        logger.info("Starting scheduled auction house update check...")
        runtimeHealthTracker.markUpdateBatchStarted(properties.configuredRegions)
        try {
            var housesFound = false
            properties.configuredRegions.forEach { region ->
                runtimeHealthTracker.markUpdateBatchProgress("load-ready-auctions", region = region)
                val auctionHousesToUpdate = auctionHouseService.getReadyForUpdate(region)
                if (auctionHousesToUpdate.isEmpty()) {
                    logger.info("No auction houses found for update in region {}.", region)
                    return@forEach
                }
                housesFound = true
                logger.info(
                    "Found {} auction houses ready for update in region {}.",
                    auctionHousesToUpdate.size,
                    region,
                )
                runtimeHealthTracker.markUpdateBatchProgress("update-auctions", region = region)
                blizzardAuctionService.updateAuctionHouses(region, auctionHousesToUpdate)
            }
            if (!housesFound) {
                logger.info("No auction houses found for update.")
                runtimeHealthTracker.markUpdateBatchCompleted()
                return
            }
            logger.info(
                "Completed scheduled auction house update batch in ${System.currentTimeMillis() - batchStartTime}ms.",
            )
            runtimeHealthTracker.markUpdateBatchCompleted()
        } catch (exception: Exception) {
            runtimeHealthTracker.markUpdateBatchFailed("auction-house-schedule")
            throw exception
        } finally {
            updateBatchRunning.set(false)
        }
    }
}
